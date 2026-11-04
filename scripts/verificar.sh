#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Executa EN LOCAL, en serie, a definición de feito (docs/definicion-de-feito.md) e remata en
# VERIFICADO (código 0) ou no primeiro fallo (código ≠ 0). ADR-0016.
#
#   scripts/verificar.sh                      todo: compliance → api → web → e2e
#   scripts/verificar.sh --parte api          só unha parte (compliance|api|web|e2e); a CI usa isto
#   scripts/verificar.sh --desde D29          empeza nesa comprobación (dentro da orde completa)
#   scripts/verificar.sh --estrito            D12–D20 execútanse un a un (por defecto lense dos informes de D11)
#   scripts/verificar.sh -v                   mostra a saída de cada comando (por defecto, en .verificar/logs/)
#   scripts/verificar.sh --contrato           só comproba que cada fila da DoD ten o seu paso aquí (sempre corre ao empezar)
#   scripts/verificar.sh --fila D14 'D28:w; NativeSqlRowLevelSecurityIT.policyThatOnlyChecksNotNullIsRejected'
#                                             rexistra a proba dunha fila nova ou cambiada (D53)
#
# Os comandos lense da táboa de docs/definicion-de-feito.md: o documento é o contrato, e o
# script non pode diverxer del. Pensado para 8 GB: nada en paralelo, compose propio
# (furar-verificar) que se baixa con -v ao saír, e limpeza dos contedores de Testcontainers.
set -Eeuo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ"
DOD=docs/definicion-de-feito.md
SAIDA=.verificar
LOGS=$SAIDA/logs
# shellcheck source=entorno.sh
source scripts/entorno.sh

# Compose propio: non toca os datos do perfil dev/full de quen desenvolve.
export COMPOSE_PROJECT_NAME=furar-verificar
# Enderezos sen rede, coma na CI: o e2e (D34) busca nos de Narón e Ferrol gardados (ADR-0023).
export FURAR_GEOCODER=${FURAR_GEOCODER:-fixed}

partes=(compliance api web e2e)
parte="" desde="" estrito=0 verbose=0 so_contrato=0 fila="" fila_proba=""
while (($#)); do
  case $1 in
    --parte) parte=${2:?falta a parte}; shift ;;
    --desde) desde=${2:?falta o ID}; shift ;;
    --estrito) estrito=1 ;;
    --contrato) so_contrato=1 ;;
    --fila) fila=${2:?falta o ID}; fila_proba=${3:?falta a proba}; shift 2 ;;
    -v | --verbose) verbose=1 ;;
    -h | --help) sed -n '2,17p' "$0"; exit 0 ;;
    *) echo "opción descoñecida: $1" >&2; exit 64 ;;
  esac
  shift
done
if [[ -n $parte ]]; then
  [[ " ${partes[*]} " == *" $parte "* ]] || { echo "parte descoñecida: $parte (${partes[*]})" >&2; exit 64; }
  partes=("$parte")
fi

# ---------------------------------------------------------------------------------------------
# Contrato: cada fila da definición de feito ten o seu paso neste script (QA: D50 quedara fóra)
# ---------------------------------------------------------------------------------------------
# D12–D20 e D50 lense dos informes de D11 (por_informes).
POR_INFORMES=(D12 D13 D14 D15 D16 D17 D18 D19 D20 D50)
# Só D42 non ten paso: é a execución enteira deste script.
SEN_PASO=(D42)
# Unha fila nova ou cambiada entra coa súa proba (QA 6.ª rolda: D14 e D16 cambiaran o resultado
# esperado e verificar.sh seguía en verde só polo código de saída). $PROBAS garda a pegada de cada
# fila D e a proba que demostra que a comprobación falla cando debe: casos de D28 («D28:w,x»),
# autoprobas («Clase.metodo») ou «base» (fila conxelada o 2026-10-23, sen cambios desde entón).
# Se a fila cambia, a pegada non coincide e hai que rexistrar a súa proba con --fila; «base» xa
# non vale para ela.
PROBAS=docs/verificacion/probas-das-filas.tsv
pegada() { # liña → 12 hex
  if command -v sha256sum >/dev/null; then sha256sum; else shasum -a 256; fi | cut -c1-12
}
pegada_fila() { # ID
  grep -E "^\| $1 " "$DOD" | head -1 | pegada
}
probas_das_filas() {
  local id gardada proba actual erro=0 caso clase metodo
  [[ -f $PROBAS ]] || { printf '\n━━ Contrato ━━\n  ✗ falta %s\n' "$PROBAS"; return 1; }
  while read -r id; do
    IFS=$'\t' read -r _ gardada proba < <(grep -E "^$id"$'\t' "$PROBAS" || true)
    actual=$(pegada_fila "$id")
    if [[ -z ${gardada:-} ]]; then
      printf '  ✗ %s: fila nova sen proba rexistrada (scripts/verificar.sh --fila %s \x27D28:<caso>|Clase.metodo\x27)\n' "$id" "$id"
      erro=1
    elif [[ $gardada != "$actual" ]]; then
      printf '  ✗ %s: a fila cambiou desde que se rexistrou a súa proba; engade o caso en D28 ou a autoproba e rexístraa con --fila %s\n' "$id" "$id"
      erro=1
    else
      for caso in $(grep -oE 'D28:[a-z,]+' <<<"$proba" | cut -d: -f2 | tr ',' ' '); do
        grep -qE "^caso_$caso\(\)" scripts/probar-violacion.sh \
          || { printf '  ✗ %s: o caso %s de D28 non existe en probar-violacion.sh\n' "$id" "$caso"; erro=1; }
      done
      for clase in $(grep -oE '[A-Za-z]+\.[A-Za-z]+' <<<"$proba" | grep -v '^D28'); do
        metodo=${clase#*.}
        grep -rqE "\b$metodo\b" api/src/test web/src 2>/dev/null \
          || { printf '  ✗ %s: a autoproba %s non existe\n' "$id" "$clase"; erro=1; }
      done
    fi
    gardada="" proba=""
  done < <(grep -oE '^\| D[0-9]+a?\b' "$DOD" | tr -d '| ')
  if ((erro)); then printf '  (%s)\n' "$PROBAS"; fi
  return "$erro"
}
cobertura_dod() {
  local dod script falta sobra
  dod=$(grep -oE '^\| D[0-9]+a?\b' "$DOD" | tr -d '| ' | sort -u)
  script=$( { grep -oE '\b(paso|omitir) D[0-9]+a?\b' "$0" | awk '{print $2}'
              printf '%s\n' "${POR_INFORMES[@]}" "${SEN_PASO[@]}"; } | sort -u)
  falta=$(comm -23 <(echo "$dod") <(echo "$script"))
  sobra=$(comm -13 <(echo "$dod") <(echo "$script"))
  if [[ -n $falta || -n $sobra ]]; then
    printf '\n━━ Contrato ━━\n'
    [[ -n $falta ]] && printf '  ✗ fila de %s sen paso en verificar.sh: %s\n' "$DOD" "$(tr '\n' ' ' <<<"$falta")"
    [[ -n $sobra ]] && printf '  ✗ paso sen fila en %s: %s\n' "$DOD" "$(tr '\n' ' ' <<<"$sobra")"
    return 1
  fi
  probas_das_filas || return 1
  # D28: as letras dos casos na fila da DoD son as do script (QA: a fila quedara en a–t con 21 casos).
  local na_dod no_script
  na_dod=$(grep -E '^\| D28 ' "$DOD" | grep -oE '\(([a-z])\) ' | tr -dc 'a-z\n' | sort -u | tr -d '\n')
  no_script=$(sed -nE 's/^\(\(\$\{#casos\[@\]\}\)\) \|\| casos=\(([a-z ]+)\)$/\1/p' scripts/probar-violacion.sh | tr -d ' ' | fold -w1 | sort -u | tr -d '\n')
  if [[ -z $no_script || $na_dod != "$no_script" ]]; then
    printf '\n━━ Contrato ━━\n  ✗ D28: casos na DoD «%s», en probar-violacion.sh «%s»\n' "$na_dod" "$no_script"
    return 1
  fi
}

# --fila: rexistra (ou substitúe) a proba dunha fila e sae.
if [[ -n $fila ]]; then
  grep -qE "^\| $fila " "$DOD" || { echo "non hai fila $fila en $DOD" >&2; exit 64; }
  [[ $fila_proba == base ]] && { echo "--fila: «base» só vale para as filas conxeladas; indica a proba" >&2; exit 64; }
  { grep -vE "^$fila"$'\t' "$PROBAS" 2>/dev/null || true; printf '%s\t%s\t%s\n' "$fila" "$(pegada_fila "$fila")" "$fila_proba"; } \
    | sort -V >"$PROBAS.novo" && mv "$PROBAS.novo" "$PROBAS"
  echo "$fila: proba rexistrada ($fila_proba)"
  exit 0
fi

# --contrato sae aquí: sen tocar .verificar nin Docker (D53 chámao desde dentro deste script).
if ((so_contrato)); then
  cobertura_dod || exit 1
  echo "Contrato: as $(grep -cE '^\| D[0-9]+a?\b' "$DOD") filas da DoD teñen paso"
  exit 0
fi

# ---------------------------------------------------------------------------------------------
# Saída
# ---------------------------------------------------------------------------------------------
mkdir -p "$LOGS"
INFORME=$SAIDA/informe.tsv
: >"$INFORME"
cor() { if [[ -t 1 ]]; then printf '\033[%sm%s\033[0m' "$1" "$2"; else printf '%s' "$2"; fi; }
cabeza() { printf '\n%s\n' "$(cor 1 "━━ $* ━━")"; }
T0=$SECONDS
omitidas=()

# ---------------------------------------------------------------------------------------------
# Limpeza ao saír (tamén con Ctrl-C ou fallo)
# ---------------------------------------------------------------------------------------------
VIXIA_PID=""
# Contedores de Testcontainers que xa había antes de empezar (non son desta execución).
TC_PREVIOS=$(docker ps -aq --no-trunc --filter label=org.testcontainers=true 2>/dev/null || true)
# Todos os contedores que xa había: a limpeza dos intermedios do build só toca os novos.
CONT_PREVIOS=$(docker ps -aq --no-trunc 2>/dev/null || true)
# Para o vixiante e agarda a que saia: unha mostra a medio escribir non pode caer despois da de «despois».
parar_vixiante() {
  [[ -n $VIXIA_PID ]] || return 0
  kill "$VIXIA_PID" 2>/dev/null || true
  wait "$VIXIA_PID" 2>/dev/null || true
  VIXIA_PID=""
}
# Vixiantes de memoria doutras execucións. Se o seu pai segue vivo, hai outro verificar.sh
# medindo a mesma VM e a medida non sería deste build: falla. Se non (órfos dunha execución
# morta, ou do formato vello sen PID), mátanse.
vixiantes_orfos() {
  local pid args pai
  while read -r pid args; do
    [[ -n $pid && $pid != "$$" ]] || continue
    pai=$(awk '{for (i = 1; i <= NF; i++) if ($i == "--vixiar") print $(i + 2)}' <<<"$args")
    if [[ $pai =~ ^[0-9]+$ ]] && kill -0 "$pai" 2>/dev/null; then
      printf '%s outra execución (PID %s) está a medir a memoria da VM: agarda a que remate\n' "$(cor 31 ✗)" "$pai" >&2
      exit 1
    fi
    kill "$pid" 2>/dev/null && printf '%s vixiante de memoria órfo (PID %s) parado\n' "$(cor 33 !)" "$pid" >&2
  done < <(ps -axo pid=,args= | awk '$0 ~ /memoria\.sh --vixiar/ && $0 !~ /awk/ {print}')
  return 0
}
limpar() {
  local rc=$?
  parar_vixiante
  if [[ " ${partes[*]} " == *" e2e "* ]]; then
    docker compose --profile full down -v --remove-orphans >/dev/null 2>&1 || true
    docker compose -p furar-limpo --profile full down -v >/dev/null 2>&1 || true
  fi
  # Só os contedores de Testcontainers creados por esta execución: os que xa existían ao
  # empezar (p. ex. un IT lanzado á man noutra terminal) non se tocan.
  local tc
  tc=$(comm -13 <(printf '%s\n' "$TC_PREVIOS" | sort) \
    <(docker ps -aq --no-trunc --filter label=org.testcontainers=true 2>/dev/null | sort) || true)
  [[ -n $tc ]] && docker rm -f $tc >/dev/null 2>&1 || true
  # Builder clásico (sen buildx): un RUN fallido de D29 deixa un contedor intermedio parado, sen
  # nome de imaxe (Config.Image = sha256:…) nin etiqueta de compose. Bórranse os desta execución.
  local c
  for c in $(comm -13 <(printf '%s\n' "$CONT_PREVIOS" | sort) \
    <(docker ps -aq --no-trunc --filter status=exited --filter status=created 2>/dev/null | sort) || true); do
    [[ $(docker inspect -f '{{.Config.Image}}|{{index .Config.Labels "com.docker.compose.project"}}' "$c" 2>/dev/null) == sha256:*'|' ]] \
      && docker rm "$c" >/dev/null 2>&1 || true
  done
  if ((rc != 0)); then
    printf '\n%s (%s). Rexistros en %s/\n' "$(cor 31 "NON VERIFICADO")" "$(duracion $((SECONDS - T0)))" "$LOGS"
  fi
  exit "$rc"
}
trap limpar EXIT
trap 'exit 130' INT TERM

duracion() { printf '%dm%02ds' $(($1 / 60)) $(($1 % 60)); }

# ---------------------------------------------------------------------------------------------
# Lectura da definición de feito
# ---------------------------------------------------------------------------------------------
# Campo da táboa (3 = comprobación, 4 = comando) para un ID; desfai o escape `\|` de Markdown.
campo() { # id n
  perl -CSD -ne '
    BEGIN { ($id, $n) = (shift, shift) }
    next unless /^\|\s*\Q$id\E\s*\|/;
    s/\\\|/\x{0}/g;
    my @f = split /\|/;
    my $c = $f[$n - 1];
    $c =~ s/^\s+|\s+$//g;
    # Comando: celda `comando` ou `comando` seguida dunha nota en prosa (p. ex. D11).
    # Título: só se quitan as comiñas invertidas (D30 «`full` arranca…»).
    if ($n == 4) { if ($c =~ /^`(.*)`$/ && $1 !~ /`/) { $c = $1 } elsif ($c =~ /^`([^`]*)`/) { $c = $1 } }
    else { $c =~ s/`//g }
    $c =~ s/\x{0}/|/g;
    print $c; $ok = 1; exit;
    END { exit($ok ? 0 : 1) }
  ' "$1" "$2" "$DOD"
}

# ---------------------------------------------------------------------------------------------
# Execución dunha comprobación
# ---------------------------------------------------------------------------------------------
activo=1
[[ -n $desde ]] && activo=0

rexistrar() { printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" >>"$INFORME"; }

# Executa un comando (texto) nun bash limpo co contorno de Furar; saída ao log.
correr() { # id comando
  local log=$LOGS/$1.log
  if ((verbose)); then
    bash -c "source scripts/entorno.sh; $2" 2>&1 | tee "$log"
    return "${PIPESTATUS[0]}"
  else
    bash -c "source scripts/entorno.sh; $2" >"$log" 2>&1
  fi
}

# Comprobación da táboa: lea o comando, execúteo e pare se falla.
paso() { # id [comando alternativo]
  local id=$1 cmd titulo t rc=0
  [[ $id == "$desde" ]] && activo=1
  ((activo)) || return 0
  titulo=$(campo "$id" 3) || { printf '%s %s non está en %s\n' "$(cor 31 ✗)" "$id" "$DOD"; exit 1; }
  cmd=${2:-$(campo "$id" 4)}
  printf '%s %-4s %s ' "$(cor 36 ▶)" "$id" "$titulo"
  t=$SECONDS
  correr "$id" "$cmd" || rc=$?
  # D44: o resultado esperado é «sen saída».
  if ((rc == 0)) && [[ $id == D44 && -s $LOGS/$id.log ]]; then rc=1; fi
  t=$((SECONDS - t))
  if ((rc == 0)); then
    printf '%s %s\n' "$(cor 32 ✓)" "$(duracion $t)"
    rexistrar "$id" ok "$t" "$titulo"
  else
    printf '%s %s (código %s)\n' "$(cor 31 ✗)" "$(duracion $t)" "$rc"
    rexistrar "$id" fallo "$t" "$titulo"
    printf '\n%s\n%s\n' "$(cor 1 "Comando:")" "$cmd"
    printf '%s\n' "$(cor 1 "Últimas liñas de $LOGS/$id.log:")"
    tail -n 60 "$LOGS/$id.log"
    exit 1
  fi
}

omitir() { # id motivo
  local id=$1
  [[ $id == "$desde" ]] && activo=1
  ((activo)) || return 0
  printf '%s %-4s %s %s\n' "$(cor 33 ○)" "$id" "$(campo "$id" 3)" "$(cor 33 "omitida: $2")"
  rexistrar "$id" omitida 0 "$2"
  omitidas+=("$id")
}

# Comprobación extra (fóra da táboa): mesma saída.
extra() { # id titulo comando
  local id=$1 rc=0 t=$SECONDS
  [[ $id == "$desde" ]] && activo=1
  ((activo)) || return 0
  printf '%s %-4s %s ' "$(cor 36 ▶)" "$id" "$2"
  correr "$id" "$3" || rc=$?
  t=$((SECONDS - t))
  if ((rc == 0)); then
    printf '%s %s\n' "$(cor 32 ✓)" "$(duracion $t)"; rexistrar "$id" ok "$t" "$2"
  else
    printf '%s (código %s)\n' "$(cor 31 ✗)" "$rc"; rexistrar "$id" fallo "$t" "$2"
    tail -n 60 "$LOGS/$id.log"; exit 1
  fi
}

# D12–D20 e D50 xa corren dentro de D11: compróbase nos informes que cada clase nomeada correu,
# con tests > 0 e sen fallos, erros nin omitidos. Con --estrito execútase o comando literal.
por_informes() { # id
  local id=$1 cmd clases c f fallos=""
  [[ $id == "$desde" ]] && activo=1
  ((activo)) || return 0
  if ((estrito)); then paso "$id"; return; fi
  cmd=$(campo "$id" 4)
  clases=$(grep -oE "api_(ut|it) '[^']+'" <<<"$cmd" | sed -E "s/^api_(ut|it) '//; s/'$//" | tr ',' '\n')
  for c in $clases; do
    f=$(ls api/target/surefire-reports/TEST-*."$c".xml api/target/failsafe-reports/TEST-*."$c".xml 2>/dev/null | head -1 || true)
    if [[ -z $f ]]; then
      fallos+=" $c(sen informe)"
    # (END corre tamén tras `exit`: o resultado vai nunha variable, non en dous exit.)
    elif ! perl -ne 'if (!$visto && /<testsuite\b/) { $visto = 1; ($t)=/\btests="(\d+)"/; ($f)=/\bfailures="(\d+)"/; ($e)=/\berrors="(\d+)"/; ($s)=/\bskipped="(\d+)"/;
          $ok = ($t>0 && $f==0 && $e==0 && $s==0) } END { $? = $ok ? 0 : 1 }' "$f"; then
      fallos+=" $c"
    fi
  done
  if [[ -z $fallos ]]; then
    paso "$id" "echo 'Informes de D11 correctos (sen fallos nin omitidos): $(tr '\n' ' ' <<<"$clases")'"
  else
    paso "$id" "echo 'Clases sen informe ou con fallos:$fallos'; exit 1"
  fi
}

# ---------------------------------------------------------------------------------------------
# Requisitos
# ---------------------------------------------------------------------------------------------
requisitos() {
  cabeza "Requisitos"
  local falta=() aviso=()
  for b in docker git jq curl perl; do command -v "$b" >/dev/null || falta+=("$b"); done
  docker info >/dev/null 2>&1 || falta+=("Docker en marcha")
  if docker compose version --short >/dev/null 2>&1; then
    local v; v=$(docker compose version --short | sed 's/^v//')
    [[ "$(printf '%s\n' 2.24.4 "$v" | sort -V | head -1)" == 2.24.4 ]] || falta+=("docker compose ≥ 2.24.4 (hai $v)")
  else
    falta+=("docker compose")
  fi
  local mem; mem=$(docker info --format '{{.MemTotal}}' 2>/dev/null || echo 0)
  ((mem >= 3800 * 1024 * 1024)) || falta+=("≥ 4 GiB para Docker (hai $((mem / 1048576)) MiB; colima start --memory 4)")
  if [[ " ${partes[*]} " =~ " "(compliance|api|web)" " ]]; then
    java -version 2>&1 | grep -qE 'version "25' || falta+=("Java 25")
  fi
  if [[ " ${partes[*]} " =~ " "(compliance|web|e2e)" " ]]; then
    node --version 2>/dev/null | grep -q '^v24\.' || falta+=("Node 24")
  fi
  docker buildx version >/dev/null 2>&1 || aviso+=("sen docker buildx: compose usa o builder clásico (sen --mount=type=cache)")
  if [[ " ${partes[*]} " == *" e2e "* ]]; then
    { command -v age && command -v age-keygen; } >/dev/null \
      || aviso+=("sen age no sistema: D41 usa o da imaxe local furar-age (scripts/lib/age.sh)")
    local p
    for p in 8080 8025 1025; do
      if (exec 3<>"/dev/tcp/127.0.0.1/$p") 2>/dev/null; then falta+=("porto $p ocupado (baixa o teu compose: docker compose --profile full down)"); fi
    done
  fi
  ((${#aviso[@]})) && printf '  ! %s\n' "${aviso[@]}"
  if ((${#falta[@]})); then
    printf '  ✗ %s\n' "${falta[@]}"
    exit 1
  fi
  printf '  ✓ docker %s, compose %s, %s, node %s, %s MiB para Docker\n' \
    "$(docker version --format '{{.Server.Version}}')" "$(docker compose version --short)" \
    "$(java -version 2>&1 | head -1 | sed -E 's/.*version "([^"]+)".*/java \1/')" \
    "$(node --version 2>/dev/null || echo -)" "$((mem / 1048576))"
}

# Ferramentas en contedor: fíxanse por digest antes de usalas pola etiqueta (ADR-0016).
fixar_ferramentas() {
  local imaxe digest
  while read -r imaxe digest; do
    [[ -z $imaxe || $imaxe == \#* ]] && continue
    if [[ "$(docker image inspect --format '{{join .RepoDigests " "}}' "$imaxe" 2>/dev/null)" != *"$digest"* ]]; then
      docker pull -q "${imaxe%:*}@$digest" >/dev/null
      docker tag "${imaxe%:*}@$digest" "$imaxe"
    fi
  done <scripts/imaxes-ferramentas.txt
}

npm_instalado=0
npm_ci() { # D21 só unha vez por execución
  if ((npm_instalado == 0)); then paso D21; npm_instalado=1; fi
}

commits() { git rev-parse --verify -q HEAD >/dev/null; }

# ---------------------------------------------------------------------------------------------
# Partes
# ---------------------------------------------------------------------------------------------
parte_compliance() {
  cabeza "Repositorio e licenzas (compliance)"
  extra X01 "Ferramentas fixadas por digest e coa mesma etiqueta en todos os comandos" "scripts/comprobar-etiquetas-ferramentas.sh && $(declare -f fixar_ferramentas); fixar_ferramentas"
  paso D01
  paso D02
  paso D44
  paso D07
  paso D06
  extra X02 "Realm de demo ao día, sen grupos nin roles (ADR-0028), sen segundo factor, o correo como usuario, contrasinal con lista de comúns e redireccións exactas" "scripts/realm-demo.sh --check && scripts/comprobar-realm.sh deploy/keycloak/import/furar-realm.json deploy/keycloak/import-demo/furar-realm.json && jq -e '([.groups[]?.name] | index(\"persoal\")) == null' deploy/keycloak/import/furar-realm.json deploy/keycloak/import-demo/furar-realm.json && test -s deploy/keycloak/password-blacklists/furar-contrasinais-comuns.txt && jq -e '[.clients[] | select(.clientId == \"furar-web\") | (.redirectUris[], (.attributes[\"post.logout.redirect.uris\"] | split(\"##\")[]))] | length > 0 and all(test(\"[*]\") | not)' deploy/keycloak/import/furar-realm.json deploy/keycloak/import-demo/furar-realm.json"
  extra X09 "Galego completo en Keycloak (login, correo, conta)" "scripts/proba-temas-keycloak.sh"
  # GOV-2: publiccode.yml válido. --no-network: no repositorio privado a url pública non responde;
  # GOV-3: ningún marcador «pendente>» nos ficheiros da raíz nunha etiqueta de versión.
  extra X11 "publiccode.yml válido e sen marcadores nunha release" \
    "docker run --rm -v \"\$PWD:/w\" -w /w italia/publiccode-parser-go:v5.4.3 -no-network -path /w publiccode.yml && scripts/sen-marcadores.sh"
  extra X03 "Caddyfile con formato e válido" \
    "docker run --rm -v \"\$PWD/deploy/caddy/Caddyfile:/etc/caddy/Caddyfile:ro\" \$(awk '/^caddy:/{print \$1}' scripts/imaxes-ferramentas.txt) sh -c 'caddy fmt /etc/caddy/Caddyfile | diff /etc/caddy/Caddyfile - && caddy validate --config /etc/caddy/Caddyfile'"
  extra X08 "Regras do Caddyfile: administración pechada, corpo máximo e teselas" "scripts/proba-caddy.sh"
  extra X04 "compose.yaml válido (dev e full)" "docker compose --profile dev config -q && docker compose --profile full config -q"
  paso D54
  paso D39
  paso D43
  paso D08
  npm_ci
  paso D03
  paso D04
  paso D05
  paso D49
  paso D51
  paso D52
  paso D47
  paso D53
  if commits; then paso D46; else
    # D46a: código 2 = falta unha decisión do mantedor (identidade neste repo, hook): omítese, non falla.
    local rc_primeiro=0
    scripts/probar-commits.sh --primeiro >"$LOGS/D46a.log" 2>&1 || rc_primeiro=$?
    if ((rc_primeiro == 2)); then
      omitir D46a "$(sed -n 's/^  - //p' "$LOGS/D46a.log" | paste -sd ';' -)"
    else
      paso D46a
    fi
    omitir D46 "aínda non hai commits: precisa o primeiro commit asinado do mantedor (ver D46a)"
  fi
}

parte_api() {
  cabeza "API"
  paso D10
  paso D11
  for id in "${POR_INFORMES[@]}"; do por_informes "$id"; done
}

parte_web() {
  cabeza "Web"
  npm_ci
  paso D22
  paso D23
  paso D24
  paso D25
  paso D26
  paso D27
  cabeza "Gardas e plantilla"
  paso D28
  paso D45
}

parte_e2e() {
  cabeza "Despregue (e2e)"
  # Pico de memoria da VM durante o build (D35), só se D29 corre nesta execución: ficheiro de
  # mostras propio (nome co ID da execución), mostra antes, vixiante cada 1 s atado a este PID
  # (sae só se esta execución morre) e mostra despois. D35 valida orde, número e límite.
  if ((activo)) || [[ $desde == D29 ]]; then
    vixiantes_orfos
    FURAR_EXECUCION="$$-$(date +%Y%m%dT%H%M%S)"
    export FURAR_EXECUCION FURAR_MOSTRAS_BUILD="$SAIDA/memoria-build-$FURAR_EXECUCION.txt"
    rm -f "$SAIDA"/memoria-build-*.txt "$SAIDA/pico-build-mib.txt"
    scripts/memoria.sh --iniciar "$FURAR_MOSTRAS_BUILD" "$FURAR_EXECUCION"
    scripts/memoria.sh --mostrar "$FURAR_MOSTRAS_BUILD" antes
    scripts/memoria.sh --vixiar "$FURAR_MOSTRAS_BUILD" "$$" >/dev/null 2>&1 &
    VIXIA_PID=$!
    paso D29
    parar_vixiante
    scripts/memoria.sh --mostrar "$FURAR_MOSTRAS_BUILD" despois
    export FURAR_PICO_OBRIGATORIO=1
  fi
  paso D30
  paso D31
  paso D32
  paso D33
  extra X10 "Correo de verificación en galego (Mailpit)" "scripts/proba-correo-gl.sh"
  [[ -d web/node_modules ]] || npm_ci
  if [[ -n ${CI:-} ]]; then
    extra X07 "Bibliotecas do sistema para Chromium (só CI)" "cd web && npx playwright install-deps chromium"
  fi
  paso D34
  paso D35
  paso D36
  extra X06 "Imaxe de Trivy fixada" "$(declare -f fixar_ferramentas); fixar_ferramentas"
  paso D37
  paso D38
  docker compose --profile full down -v >/dev/null 2>&1
  paso D40
  paso D41
  docker compose --profile full down -v >/dev/null 2>&1
  paso D48
}

# ---------------------------------------------------------------------------------------------
cobertura_dod || exit 1
requisitos
for p in "${partes[@]}"; do "parte_$p"; done

if ((activo == 0)); then echo "--desde $desde: ese ID non está nas partes ${partes[*]}" >&2; exit 64; fi
cabeza "Resumo"
awk -F'\t' '{ printf "  %-4s %-8s %5ss  %s\n", $1, $2, $3, $4 }' "$INFORME"
printf '  Tempo total: %s\n' "$(duracion $((SECONDS - T0)))"
if ((${#omitidas[@]})); then
  printf '\n%s: omitidas %s (só as desbloquea unha persoa: ver o motivo arriba)\n' "$(cor 33 "NON VERIFICADO")" "${omitidas[*]}"
  exit 3
fi
if [[ -n $desde || ${#partes[@]} -lt 4 ]]; then
  printf '\n%s\n' "$(cor 32 "VERIFICADO (${partes[*]}${desde:+ desde $desde})")"
else
  printf '\n%s\n' "$(cor 32 VERIFICADO)"
fi
