#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Instalación nun servidor do concello (docs/instalacion.md §2). Pregunta só o imprescindible
# (dominio, correo de quen administra e SMTP), xera todos os segredos,
# toma as imaxes do ficheiro furar-<versión>.env da release, escribe .env (chmod 600, modo
# produción), pasa scripts/preflight.sh, descarga as imaxes, envía un correo de proba polo SMTP
# (antes do primeiro arranque: Keycloak só le o SMTP nese momento) e arrinca con `up -d --wait`
# (se Keycloak segue arrincando cando Compose rende, agarda ata 15 minutos máis e volve).
# O resto do .env queda cos valores por defecto (docs/instalacion.md §4). Os datos de privacidade
# non se preguntan: son datos legais de cada concello, na web (ADR-0017, revisión do 2026-11-03).
# Ver --axuda.
set -euo pipefail
source "$(dirname "$0")/lib/comun.sh"

axuda() {
  cat <<'AXUDA'
Uso: scripts/instalar.sh [opcións]

Sen opcións, pregunta os datos un a un. Cada dato tamén pode vir dunha opción ou dunha
variable de contorno (entre parénteses) ou do .env que xa exista; en modo --si non pregunta.

  --dominio NOME            nome do servidor, p. ex. reservas.concello.gal (FURAR_PUBLIC_URL)
  --correo-admin CORREO     quen creará a conta de administración (FURAR_ADMIN_EMAIL)
  --smtp-servidor NOME      servidor de correo saínte (FURAR_SMTP_HOST)
  --smtp-cifrado C          starttls (porto 587, por defecto), ssl (SSL/TLS, porto 465) ou ningun
                            (só servidor interno, porto 25) (FURAR_SMTP_STARTTLS e FURAR_SMTP_SSL)
  --smtp-porto N            porto (FURAR_SMTP_PORT; por defecto, o do cifrado)
  --smtp-usuario U          usuario SMTP; baleiro se non pide (FURAR_SMTP_USER)
  --smtp-contrasinal C      contrasinal SMTP (FURAR_SMTP_PASSWORD; mellor como variable)
  --smtp-remitente CORREO   enderezo que envía os correos (FURAR_SMTP_FROM)
  --release FICHEIRO        furar-<versión>.env da release (por defecto, o que haxa no cartafol)
  --si                      non preguntar nada (automatización): falla se falta un dato
  --substituir              substitúe o .env que exista (garda unha copia e conserva os segredos)
  --so-configurar           escribe .env e para: sen comprobacións nin arranque
  --probar-correo           só envía o correo de proba co .env actual (a --correo-admin ou a
                            FURAR_ADMIN_EMAIL) e di se o servidor SMTP o aceptou
  --sen-correo-de-proba     instala sen o correo de proba (non recomendado)
  --autoproba               proba o script nun cartafol temporal, sen Docker
  --axuda                   esta axuda
AXUDA
}

# --- Opcións ----------------------------------------------------------------------------------
si=0 substituir=0 so_configurar=0 release="" so_probar_correo=0 sen_proba=0
op_dominio="" op_correo="" op_host="" op_porto="" op_usuario="" op_contrasinal="" op_remitente=""
op_cifrado=""
# Respostas: as enche preguntar() con printf -v.
correo_admin="" smtp_host="" smtp_cifrado="" smtp_porto="" smtp_usuario="" smtp_remitente=""
autoproba=0
while (($#)); do
  case $1 in
    --axuda | -h | --help) axuda; exit 0 ;;
    --si) si=1 ;;
    --substituir) substituir=1 ;;
    --so-configurar) so_configurar=1 ;;
    --autoproba) autoproba=1 ;;
    --probar-correo) so_probar_correo=1 ;;
    --sen-correo-de-proba) sen_proba=1 ;;
    --dominio | --correo-admin | --smtp-servidor | --smtp-cifrado | --smtp-porto | --smtp-usuario | \
      --smtp-contrasinal | --smtp-remitente | --release)
      (($# >= 2)) || falla "$1 precisa un valor (--axuda)"
      case $1 in
        --dominio) op_dominio=$2 ;;
        --correo-admin) op_correo=$2 ;;
        --smtp-servidor) op_host=$2 ;;
        --smtp-cifrado) op_cifrado=$2 ;;
        --smtp-porto) op_porto=$2 ;;
        --smtp-usuario) op_usuario=$2 ;;
        --smtp-contrasinal) op_contrasinal=$2 ;;
        --smtp-remitente) op_remitente=$2 ;;
        --release) release=$2 ;;
      esac
      shift ;;
    *) falla "opción descoñecida: $1 (--axuda)" ;;
  esac
  shift
done

# --- Autoproba: o script nunha copia mínima, sen Docker -----------------------------------------
if ((autoproba)); then
  titulo "Autoproba de instalar.sh (sen Docker)"
  t=$(mktemp -d)
  trap 'rm -rf "$t"' EXIT
  mkdir -p "$t/scripts/lib"
  cp "$FURAR_RAIZ/scripts/instalar.sh" "$t/scripts/"
  cp "$FURAR_RAIZ/scripts/lib/"*.sh "$t/scripts/lib/"
  cp "$FURAR_RAIZ/.env.example" "$t/"
  d64() { printf '%064d' "$1"; }
  printf 'FURAR_REGISTRY=rexistro.example/furar\nFURAR_VERSION=v9.9.9\nFURAR_API_DIGEST=%s\nFURAR_WEB_DIGEST=%s\nFURAR_KEYCLOAK_DIGEST=%s\n' \
    "$(d64 1)" "$(d64 2)" "$(d64 3)" >"$t/furar-v9.9.9.env"
  i="$t/scripts/instalar.sh"
  limpo() { env -i PATH="$PATH" HOME="$HOME" "$@"; }
  # 1. Respostas por teclado (unha por linea, na orde das preguntas: dominio, correo, servidor,
  # cifrado, porto, usuario, contrasinal e remitente).
  printf '%s\n' https://Reservas.Proba.gal/ admin@proba.gal smtp.proba.gal "" "" correo@proba.gal 'segredo $con # e espazos' "" \
    | limpo "$i" --so-configurar >/dev/null 2>&1 || falla "modo interactivo"
  e=$t/.env
  v() { sed -nE "s/^$1=(.*)$/\1/p" "$e"; }
  esperar "FURAR_PUBLIC_URL" "$(v FURAR_PUBLIC_URL)" https://reservas.proba.gal
  esperar "cifrado por defecto: STARTTLS" "$(v FURAR_SMTP_STARTTLS)/$(v FURAR_SMTP_SSL)" true/false
  esperar "porto SMTP por defecto" "$(v FURAR_SMTP_PORT)" 587
  esperar "remitente por defecto (o usuario)" "$(v FURAR_SMTP_FROM)" correo@proba.gal
  esperar "contrasinal SMTP literal" "$(v FURAR_SMTP_PASSWORD)" "'segredo \$con # e espazos'"
  esperar "COMPOSE_FILE de produción" "$(grep -c '^COMPOSE_FILE=compose.yaml:compose.prod.yaml$' "$e")" 1
  esperar "permisos" "$(stat -c %a "$e" 2>/dev/null || stat -f %Lp "$e")" 600
  esperar "digest da API" "$(v FURAR_API_DIGEST)" "$(d64 1)"
  for k in POSTGRES_PASSWORD FURAR_DB_OWNER_PASSWORD FURAR_DB_PASSWORD KC_DB_PASSWORD KC_BOOTSTRAP_ADMIN_PASSWORD FURAR_INVITATION_PROOF_SECRET; do
    [[ $(v "$k") =~ ^[0-9a-f]{48,}$ ]] || falla "$k sen segredo xerado"
  done
  ok "segredos xerados"
  [[ $(v FURAR_DB_PASSWORD) != "$(v KC_DB_PASSWORD)" ]] || falla "segredos repetidos"
  grep -q FURAR_PRIVACY_ "$e" && falla "o .env leva FURAR_PRIVACY_* (son datos de cada concello, na web)"
  ok "sen datos de privacidade no .env"
  # 2. Non substitúe sen permiso; con --substituir garda copia e conserva os segredos.
  vello=$(v FURAR_DB_PASSWORD)
  limpo "$i" --si --so-configurar >/dev/null 2>&1 || falla "--si cun .env xa feito"
  esperar "o .env existente non se toca" "$(v FURAR_DB_PASSWORD)" "$vello"
  # Un .env anterior ao 2026-11-03 traía FURAR_PRIVACY_*: xa non se usan e non se arrastran.
  printf 'FURAR_PRIVACY_CONTROLLER=Concello vello\nFURAR_PRIVACY_POLICY_URL=https://vello.gal/p\n' >>"$e"
  limpo FURAR_ADMIN_EMAIL=outra@proba.gal "$i" --si --substituir --so-configurar >/dev/null 2>&1 || falla "--substituir"
  esperar "--substituir quita os FURAR_PRIVACY_* vellos" "$(grep -c '^FURAR_PRIVACY_' "$e" || true)" 0
  esperar "--substituir conserva os segredos" "$(v FURAR_DB_PASSWORD)" "$vello"
  esperar "--substituir colle os datos do .env vello" "$(v FURAR_SMTP_HOST)" smtp.proba.gal
  esperar "--substituir aplica o novo correo" "$(v FURAR_ADMIN_EMAIL)" outra@proba.gal
  esperar "copia do .env vello" "$(find "$t" -maxdepth 1 -name '.env.copia-*' | wc -l | tr -d ' ')" 1
  # Cifrado: SSL/TLS co seu porto; un .env anterior sen FURAR_SMTP_SSL e con STARTTLS=false é
  # «sen cifrar» e conserva o seu porto; por teclado, a opción 3.
  limpo "$i" --si --substituir --so-configurar --smtp-cifrado ssl >/dev/null 2>&1 || falla "--smtp-cifrado ssl"
  esperar "SSL/TLS" "$(v FURAR_SMTP_STARTTLS)/$(v FURAR_SMTP_SSL)/$(v FURAR_SMTP_PORT)" false/true/465
  sed -i.bak -e '/^FURAR_SMTP_SSL=/d' -e 's/^FURAR_SMTP_STARTTLS=.*/FURAR_SMTP_STARTTLS=false/' \
    -e 's/^FURAR_SMTP_PORT=.*/FURAR_SMTP_PORT=2525/' "$e" && rm -f "$e.bak"
  limpo "$i" --si --substituir --so-configurar >/dev/null 2>&1 || falla ".env sen FURAR_SMTP_SSL"
  esperar ".env anterior sen cifrar" "$(v FURAR_SMTP_STARTTLS)/$(v FURAR_SMTP_SSL)/$(v FURAR_SMTP_PORT)" false/false/2525
  printf '%s\n' s "" "" "" 1 "" "" "" "" | limpo "$i" --so-configurar >/dev/null 2>&1 || falla "cifrado por teclado"
  esperar "volver a STARTTLS por teclado: porto 587" "$(v FURAR_SMTP_STARTTLS)/$(v FURAR_SMTP_SSL)/$(v FURAR_SMTP_PORT)" true/false/587
  limpo "$i" --si --substituir --so-configurar --smtp-cifrado outro >/dev/null 2>&1 && falla "acepta un cifrado incorrecto"
  ok "rexeita un cifrado incorrecto"
  # Correo de proba (--probar-correo) cun docker simulado: a imaxe da API co argumento test-mail;
  # só se mostra a liña do resultado, sen o contrasinal.
  mkdir -p "$t/bin"
  cat >"$t/bin/docker" <<'DOCKER'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"${FAKE_DOCKER_LOG:?}"
echo "Picked up JAVA_TOOL_OPTIONS: -Xss512k"
if [[ ${FAKE_SMTP:-} == ok ]]; then echo "test-mail: accepted"; exit 0; fi
echo "test-mail: MailAuthenticationException (AuthenticationFailedException: 535 5.7.8 Authentication failed)"
exit 1
DOCKER
  chmod +x "$t/bin/docker"
  log=$t/docker.log
  saida=$(limpo PATH="$t/bin:$PATH" FAKE_DOCKER_LOG="$log" FAKE_SMTP=ok "$i" --probar-correo 2>&1) || falla "correo aceptado: $saida"
  esperar "orde do correo de proba" "$(tail -1 "$log")" "compose run --rm --no-deps -T api test-mail outra@proba.gal"
  grep -q 'aceptou o correo' <<<"$saida" || falla "sen «aceptou» co correo aceptado: $saida"
  saida=$(limpo PATH="$t/bin:$PATH" FAKE_DOCKER_LOG="$log" "$i" --probar-correo --correo-admin x@proba.gal 2>&1) \
    && falla "un correo rexeitado non dá erro"
  grep -q '535 5.7.8' <<<"$saida" || falla "o rexeitamento non leva a resposta do servidor: $saida"
  grep -q 'segredo' <<<"$saida" && falla "a saída leva o contrasinal SMTP"
  esperar "destinatario de --correo-admin" "$(tail -1 "$log" | awk '{print $NF}')" x@proba.gal
  ok "correo de proba: aceptado, rexeitado co motivo e sen segredos"
  # 3. Entradas incorrectas e falta de release.
  rm -f "$e"
  limpo "$i" --si --so-configurar --dominio 'non vale' >/dev/null 2>&1 && falla "acepta un dominio incorrecto"
  limpo "$i" --si --so-configurar --dominio a.gal --correo-admin x >/dev/null 2>&1 && falla "acepta un correo incorrecto"
  limpo "$i" --si --so-configurar --dominio a.gal --correo-admin a@a.gal --smtp-servidor s.a.gal --smtp-porto 99999 \
    >/dev/null 2>&1 && falla "acepta un porto incorrecto"
  ok "rexeita dominio, correo e porto incorrectos"
  mv "$t/furar-v9.9.9.env" "$t/outro"
  saida=$(limpo "$i" --si --so-configurar 2>&1) && falla "arrinca sen o ficheiro da release"
  grep -q 'Releases' <<<"$saida" || falla "sen release, a mensaxe non di onde descargala: $saida"
  [[ ! -e $e ]] || falla "escribiu .env sen release"
  ok "sen furar-<versión>.env para e di onde descargalo"
  echo "autoproba OK"
  exit 0
fi

# --- Utilidades ---------------------------------------------------------------------------------
env_file=$FURAR_RAIZ/.env
minusculas() { tr '[:upper:]' '[:lower:]'; }
re_dominio='^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$'
re_correo='^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'
segredo() { openssl rand -hex "${1:-24}"; }

# Valor dunha clave no .env existente (sen comiñas).
do_env() { # clave
  [[ -f $env_file ]] || return 0
  sed -nE "s/^$1=(.*)$/\1/p" "$env_file" | tail -1 | sed -E "s/^'(.*)'$/\1/"
}

# Pregunta un dato. Por defecto: opción > variable de contorno > .env existente > proposto.
# Valida con `validar_<tipo>`; en modo --si non pregunta e para se falta ou non vale.
preguntar() { # variable texto tipo valor_por_defecto [opcional] [segredo]
  local var=$1 texto=$2 tipo=$3 def=$4 opcional=${5:-} oculto=${6:-} r erro fin=0
  while :; do
    if ((si)); then r=$def
    else
      if [[ -n $oculto ]]; then
        read -r -s -p "  $texto${def:+ [gardado]}: " r || fin=1; echo
      else
        read -r -p "  $texto${def:+ [$def]}: " r || fin=1
      fi
      [[ -n $r ]] || r=$def
    fi
    if [[ -z $r && -n $opcional ]]; then printf -v "$var" %s ""; return; fi
    if erro=$("validar_$tipo" "$r"); then printf -v "$var" %s "$erro"; return; fi
    ((si || fin)) && falla "$texto: $erro"
    aviso "$erro"
  done
}
# Cada validador imprime o valor normalizado (código 0) ou o motivo (código 1).
validar_texto() {
  [[ -n $1 ]] || { echo "non pode quedar baleiro"; return 1; }
  [[ $1 != *"'"* ]] || { echo "non se admite a comiña simple (')"; return 1; }
  printf '%s\n' "$1"
}
validar_dominio() {
  local d
  d=$(printf %s "$1" | minusculas | sed -E 's#^https?://##; s#/+$##')
  [[ $d =~ $re_dominio ]] || { echo "«$1» non é un nome de servidor (p. ex. reservas.concello.gal)"; return 1; }
  printf '%s\n' "$d"
}
validar_correo() {
  [[ $1 =~ $re_correo ]] || { echo "«$1» non é un correo (nome@concello.gal)"; return 1; }
  printf '%s\n' "$1"
}
validar_porto() {
  [[ $1 =~ ^[0-9]{1,5}$ ]] && ((10#$1 >= 1 && 10#$1 <= 65535)) || { echo "«$1» non é un porto (1-65535)"; return 1; }
  printf '%s\n' "$((10#$1))"
}
validar_servidor() {
  [[ $1 =~ ^[A-Za-z0-9.-]+$ ]] || { echo "«$1» non é un nome de servidor nin unha IP"; return 1; }
  printf '%s\n' "$1"
}
validar_cifrado() {
  case $(printf %s "$1" | minusculas) in
    1 | starttls) echo starttls ;;
    2 | ssl | ssl/tls | tls) echo ssl ;;
    3 | ningun | ningún | none) echo ningun ;;
    *) echo "«$1» non é unha opción: 1 (STARTTLS), 2 (SSL/TLS) ou 3 (sen cifrar)"; return 1 ;;
  esac
}

# Cifrado SMTP: starttls, ssl ou ningun, gardado como FURAR_SMTP_STARTTLS e FURAR_SMTP_SSL (o que
# len Keycloak e a API). Un .env anterior sen FURAR_SMTP_SSL é STARTTLS, ou ningun se o tiña a false.
cifrado_de() { # valor_starttls valor_ssl
  if [[ $2 == true ]]; then echo ssl
  elif [[ $1 == false ]]; then echo ningun
  else echo starttls; fi
}
numero_de_cifrado() { case $1 in ssl) echo 2 ;; ningun) echo 3 ;; *) echo 1 ;; esac; }
porto_de_cifrado() { case $1 in ssl) echo 465 ;; ningun) echo 25 ;; *) echo 587 ;; esac; }

# Correo de proba polo mesmo camiño ca os da API: a súa imaxe co argumento test-mail
# (FurarTestMail), co .env e a rede do compose, sen base nin outros servizos. Só se mostra a liña do
# resultado (sen segredos). Código 0 se o servidor SMTP o aceptou.
probar_correo() { # destinatario
  local para=$1 saida codigo=0 linea limite=()
  command -v timeout >/dev/null && limite=(timeout 300)
  titulo "Correo de proba a $para"
  saida=$(cd "$FURAR_RAIZ" && ${limite[@]+"${limite[@]}"} docker compose run --rm --no-deps -T api test-mail "$para" 2>&1) \
    || codigo=$?
  linea=$(grep -o 'test-mail: .*' <<<"$saida" | tail -1 | sed 's/^test-mail: //' || true)
  if ((codigo == 0)) && [[ $linea == accepted ]]; then
    ok "o servidor SMTP aceptou o correo: mira a caixa de $para (e o correo non desexado)"
    return 0
  fi
  aviso "o servidor SMTP non aceptou o correo de proba: ${linea:-sen resposta (proba a man: docker compose run --rm --no-deps api test-mail $para)}"
  cat >&2 <<'FIN'
    Que revisar (docs/instalacion.md, «Correo saínte»):
    - Servidor, porto e cifrado: 587 con STARTTLS, 465 con SSL/TLS. «Connection refused» ou
      «timed out»: este servidor non chega a ese porto (firewall ou saída a Internet).
    - Usuario e contrasinal: «535» son credenciais que o servidor non acepta.
    - Remitente: o servidor ten que deixar enviar como ese enderezo («550»/«553»: non o deixa).
    - Certificado («PKIX», «SSLHandshake»): o nome do servidor ten que ser o do certificado.
    Para corrixir, executa outra vez scripts/instalar.sh e substitúe o .env (consérvanse os
    contrasinais); para só probar outra vez: scripts/instalar.sh --probar-correo.
FIN
  return 1
}

# Valor para o .env: tal cal se é simple; se non, entre comiñas simples (Compose non interpola
# nada dentro delas: $ e # quedan literais).
para_env() {
  if [[ $1 =~ ^[A-Za-z0-9._:/@+=,-]*$ ]]; then printf %s "$1"; else printf "'%s'" "$1"; fi
}

# --- Só o correo de proba (--probar-correo) -------------------------------------------------------
if ((so_probar_correo)); then
  [[ -f $env_file ]] || falla "non hai .env en $FURAR_RAIZ: instala primeiro (scripts/instalar.sh)"
  para=${op_correo:-$(do_env FURAR_ADMIN_EMAIL)}
  [[ $para =~ $re_correo ]] || falla "falta o destinatario: --correo-admin CORREO"
  probar_correo "$para"
  exit
fi

# --- Ficheiro da release ------------------------------------------------------------------------
if [[ -z $release ]]; then
  release=${FURAR_RELEASE_ENV:-}
fi
if [[ -z $release ]]; then
  candidatos=()
  for f in "$FURAR_RAIZ"/furar-*.env; do [[ -f $f ]] && candidatos+=("$f"); done
  if ((${#candidatos[@]} > 1)); then
    falla "hai varios ficheiros furar-*.env en $FURAR_RAIZ: indica cal con --release FICHEIRO"
  fi
  release=${candidatos[0]:-}
fi
if [[ -z $release || ! -f $release ]]; then
  cat >&2 <<FIN
  ✗ Falta o ficheiro furar-<versión>.env da release${release:+ ($release)}.
    Descárgao da páxina «Releases» da forxa de Furar, na mesma versión ca o código, e
    déixao en $FURAR_RAIZ. Leva o rexistro, a versión e os digests das imaxes
    (docs/instalacion.md §2). Non se inventan: sen el non se pode instalar.
FIN
  exit 1
fi
rel() { sed -nE "s/^$1=(.*)$/\1/p" "$release" | tail -1; }
rel_registry=$(rel FURAR_REGISTRY) rel_version=$(rel FURAR_VERSION)
rel_api=$(rel FURAR_API_DIGEST) rel_web=$(rel FURAR_WEB_DIGEST) rel_keycloak=$(rel FURAR_KEYCLOAK_DIGEST)
[[ $rel_registry =~ ^[^[:space:]]+$ && $rel_version =~ ^[^[:space:]]+$ ]] \
  || falla "$release non ten FURAR_REGISTRY e FURAR_VERSION"
for d in "$rel_api" "$rel_web" "$rel_keycloak"; do
  [[ $d =~ ^[0-9a-f]{64}$ ]] || falla "$release ten un digest incorrecto ou falta algún (FURAR_*_DIGEST)"
done
if command -v git >/dev/null && etiqueta=$(git -C "$FURAR_RAIZ" describe --tags --exact-match 2>/dev/null); then
  [[ $etiqueta == "$rel_version" ]] || aviso "o código está na versión $etiqueta e a release é $rel_version: usa a mesma"
fi

# --- Requisitos mínimos antes de preguntar --------------------------------------------------------
command -v openssl >/dev/null || falla "falta openssl (apt install openssl)"
if ((!so_configurar)); then
  docker info >/dev/null 2>&1 || falla "Docker non está en marcha (ou non tes permiso: grupo docker). docs/instalacion.md §1"
fi

titulo "Instalar Furar $rel_version"

# --- .env existente -------------------------------------------------------------------------------
# Sen .env os contrasinais son novos: unha base que xa exista (outra instalación anterior) non os
# coñece e a API e Keycloak non conectarían (compróbase antes de arrincar).
env_previo=0
[[ -f $env_file ]] && env_previo=1
escribir=1
if [[ -f $env_file ]] && ((!substituir)); then
  if ((si)); then
    escribir=0
  else
    read -r -p "  Xa hai un .env. Substituílo? Gárdase unha copia e consérvanse os contrasinais [s/N]: " r || true
    [[ $(printf %s "${r:-n}" | minusculas) == s* ]] || escribir=0
  fi
  ((escribir)) || ok "mantense o .env actual"
fi

if ((escribir)); then
  if ((!si)); then
    echo "  Responde ás preguntas; Intro deixa o valor entre corchetes."
  fi
  url_vella=$(do_env FURAR_PUBLIC_URL)
  preguntar dominio "Nome do servidor (o enderezo da web, p. ex. reservas.concello.gal)" dominio \
    "${op_dominio:-${FURAR_PUBLIC_URL:-$url_vella}}"
  preguntar correo_admin "Correo de quen creará a conta de administración" correo \
    "${op_correo:-${FURAR_ADMIN_EMAIL:-$(do_env FURAR_ADMIN_EMAIL)}}"
  echo "  Correo saínte (SMTP) do concello, para os avisos e as contas:"
  preguntar smtp_host "Servidor SMTP" servidor "${op_host:-${FURAR_SMTP_HOST:-$(do_env FURAR_SMTP_HOST)}}"
  # Cifrado: opción > variables de contorno > .env existente > STARTTLS.
  cifrado_vello=""
  [[ -f $env_file ]] && cifrado_vello=$(cifrado_de "$(do_env FURAR_SMTP_STARTTLS)" "$(do_env FURAR_SMTP_SSL)")
  cifrado_env=""
  [[ -n ${FURAR_SMTP_STARTTLS:-}${FURAR_SMTP_SSL:-} ]] \
    && cifrado_env=$(cifrado_de "${FURAR_SMTP_STARTTLS:-}" "${FURAR_SMTP_SSL:-}")
  cifrado_def=${op_cifrado:-${cifrado_env:-${cifrado_vello:-starttls}}}
  cifrado_def=$(validar_cifrado "$cifrado_def") || falla "--smtp-cifrado: $cifrado_def"
  if ((!si)); then
    echo "  Cifrado da conexión co servidor SMTP:"
    echo "    1) STARTTLS (porto 587)"
    echo "    2) SSL/TLS (porto 465)"
    echo "    3) Sen cifrar (só servidor interno)"
  fi
  preguntar smtp_cifrado "Cifrado (1, 2 ou 3)" cifrado "$(numero_de_cifrado "$cifrado_def")"
  # O porto do .env vale se o cifrado non cambiou; se non, o habitual do cifrado escollido.
  porto_vello=$(do_env FURAR_SMTP_PORT)
  [[ $smtp_cifrado == "$cifrado_vello" ]] || porto_vello=""
  porto_def=${op_porto:-${FURAR_SMTP_PORT:-$porto_vello}}
  preguntar smtp_porto "Porto" porto "${porto_def:-$(porto_de_cifrado "$smtp_cifrado")}"
  preguntar smtp_usuario "Usuario (baleiro se o servidor non pide)" texto \
    "${op_usuario:-${FURAR_SMTP_USER:-$(do_env FURAR_SMTP_USER)}}" opcional
  if [[ -n $smtp_usuario ]]; then
    preguntar smtp_contrasinal "Contrasinal" texto \
      "${op_contrasinal:-${FURAR_SMTP_PASSWORD:-$(do_env FURAR_SMTP_PASSWORD)}}" "" oculto
  else
    smtp_contrasinal=""
  fi
  remitente_def=${op_remitente:-${FURAR_SMTP_FROM:-$(do_env FURAR_SMTP_FROM)}}
  if [[ -z $remitente_def && $smtp_usuario =~ $re_correo ]]; then remitente_def=$smtp_usuario; fi
  preguntar smtp_remitente "Enderezo que envía os correos" correo "$remitente_def"

  # Segredos: os do .env anterior, se os había (a base xa creada só coñece eses); se non, novos.
  conservar() { local v; v=$(do_env "$1"); if [[ -n $v ]]; then echo "$v"; else segredo "${2:-24}"; fi; }
  kc_usuario=$(do_env KC_BOOTSTRAP_ADMIN_USERNAME)
  valores=(
    FURAR_PUBLIC_URL "https://$dominio"
    FURAR_ADMIN_EMAIL "$correo_admin"
    FURAR_REGISTRY "$rel_registry"
    FURAR_VERSION "$rel_version"
    FURAR_API_DIGEST "$rel_api"
    FURAR_WEB_DIGEST "$rel_web"
    FURAR_KEYCLOAK_DIGEST "$rel_keycloak"
    POSTGRES_PASSWORD "$(conservar POSTGRES_PASSWORD)"
    FURAR_DB_OWNER_PASSWORD "$(conservar FURAR_DB_OWNER_PASSWORD)"
    FURAR_DB_PASSWORD "$(conservar FURAR_DB_PASSWORD)"
    KC_DB_PASSWORD "$(conservar KC_DB_PASSWORD)"
    KC_BOOTSTRAP_ADMIN_USERNAME "${kc_usuario:-admin-inicial}"
    KC_BOOTSTRAP_ADMIN_PASSWORD "$(conservar KC_BOOTSTRAP_ADMIN_PASSWORD)"
    FURAR_INVITATION_PROOF_SECRET "$(conservar FURAR_INVITATION_PROOF_SECRET 32)"
    FURAR_SMTP_HOST "$smtp_host"
    FURAR_SMTP_PORT "$smtp_porto"
    FURAR_SMTP_STARTTLS "$([[ $smtp_cifrado == starttls ]] && echo true || echo false)"
    FURAR_SMTP_SSL "$([[ $smtp_cifrado == ssl ]] && echo true || echo false)"
    FURAR_SMTP_FROM "$smtp_remitente"
    FURAR_SMTP_USER "$smtp_usuario"
    FURAR_SMTP_PASSWORD "$smtp_contrasinal"
    FURAR_ADMIN_IPS "$(do_env FURAR_ADMIN_IPS | grep . || echo 127.0.0.1/32)"
  )
  # Sen usuario SMTP non hai autenticación (se non, Keycloak e a API intentaríana baleira).
  [[ -n $smtp_usuario ]] || valores+=(FURAR_SMTP_AUTH false)

  # Mantén do .env anterior o que non se pregunta (mapa, copias, proxy...), salvo o que vai arriba.
  [[ -f $FURAR_RAIZ/.env.example ]] || falla "falta .env.example en $FURAR_RAIZ"
  novo=$(mktemp "$FURAR_RAIZ/.env.novo.XXXXXX")
  chmod 600 "$novo"
  trap 'rm -f "$novo"' EXIT
  valor_de() { # clave → valor (código 1 se non está na lista)
    local i
    for ((i = 0; i < ${#valores[@]}; i += 2)); do
      [[ ${valores[i]} == "$1" ]] && { printf %s "${valores[i + 1]}"; return 0; }
    done
    return 1
  }
  {
    printf '# Xerado por scripts/instalar.sh o %s. Valores avanzados: docs/instalacion.md §4.\n' "$(date '+%Y-%m-%d %H:%M')"
    while IFS= read -r linea || [[ -n $linea ]]; do
      if [[ $linea =~ ^#\ ?COMPOSE_FILE=compose\.yaml:compose\.prod\.yaml ]]; then
        echo "COMPOSE_FILE=compose.yaml:compose.prod.yaml"
      elif [[ $linea =~ ^([A-Z_][A-Z0-9_]*)= ]]; then
        k=${BASH_REMATCH[1]}
        if v=$(valor_de "$k"); then echo "$k=$(para_env "$v")"
        elif [[ -f $env_file ]] && grep -qE "^$k=" "$env_file"; then grep -E "^$k=" "$env_file" | tail -1
        else echo "$linea"; fi
      else
        echo "$linea"
      fi
    done <"$FURAR_RAIZ/.env.example"
    # Claves de arriba que .env.example non trae (p. ex. FURAR_SMTP_AUTH comentada).
    for ((i = 0; i < ${#valores[@]}; i += 2)); do
      grep -qE "^${valores[i]}=" "$FURAR_RAIZ/.env.example" || echo "${valores[i]}=$(para_env "${valores[i + 1]}")"
    done
    # Claves do .env anterior que non están en .env.example (p. ex. COMPOSE_PROJECT_NAME).
    if [[ -f $env_file ]]; then
      while IFS= read -r linea; do
        [[ $linea =~ ^([A-Z_][A-Z0-9_]*)= ]] || continue
        k=${BASH_REMATCH[1]}
        valor_de "$k" >/dev/null && continue
        # Datos de privacidade do despregue (ata o 2026-11-03): agora son de cada concello.
        [[ $k == FURAR_PRIVACY_* ]] && continue
        grep -qE "^#? ?$k=" "$FURAR_RAIZ/.env.example" || echo "$linea"
      done <"$env_file"
    fi
  } >"$novo"
  if [[ -f $env_file ]]; then
    copia="$env_file.copia-$(date +%Y%m%d-%H%M%S)"
    cp -p "$env_file" "$copia"
    chmod 600 "$copia"
    ok "copia do .env anterior en $copia"
  fi
  mv "$novo" "$env_file"
  trap - EXIT
  chmod 600 "$env_file"
  ok ".env escrito (permisos 600, modo produción, segredos xerados)"
fi

if ((so_configurar)); then
  echo "Configuración lista en $env_file (--so-configurar: sen arrancar)."
  exit 0
fi

# --- Comprobar e arrincar -------------------------------------------------------------------------
dominio=$(do_env FURAR_PUBLIC_URL | sed -E 's#^https?://##')
if command -v getent >/dev/null && ! getent hosts "$dominio" >/dev/null; then
  aviso "$dominio aínda non resolve no DNS: o certificado HTTPS non chegará ata que apunte a este servidor"
fi
cd "$FURAR_RAIZ"
if ((!env_previo)); then
  proxecto=$(docker compose config 2>/dev/null | sed -n 's/^name: //p' | head -1)
  vella=$(docker volume ls -q --filter "label=com.docker.compose.project=${proxecto:-furar}" \
    --filter label=com.docker.compose.volume=pgdata-prod | head -1)
  [[ -z $vella ]] || falla "xa hai unha base doutra instalación (volume $vella) cos contrasinais do seu .env, non cos novos: recupera ese .env ou, para empezar de cero, borra a base (docs/instalacion.md §8, «Empezar de cero») e volve executar scripts/instalar.sh"
fi
scripts/preflight.sh
titulo "Descargando as imaxes (uns minutos)"
docker compose pull
# Antes de arrincar: Keycloak copia o SMTP ao realm só no primeiro arranque (docs/instalacion.md §4),
# así que un SMTP mal escrito corríxese mellor agora, no .env.
correo_admin=${correo_admin:-$(do_env FURAR_ADMIN_EMAIL)}
if ((sen_proba)); then
  aviso "sen correo de proba (--sen-correo-de-proba)"
elif [[ -z $correo_admin ]]; then
  aviso "sen FURAR_ADMIN_EMAIL no .env: sen correo de proba"
elif ! probar_correo "$correo_admin"; then
  ((si)) && falla "corrixe o SMTP e volve executar o instalador (ou --sen-correo-de-proba)"
  read -r -p "  Arrincar igualmente? Keycloak gardará este SMTP no primeiro arranque [s/N]: " r || true
  [[ $(printf %s "${r:-n}" | minusculas) == s* ]] \
    || falla "sen arrincar: corrixe o SMTP (scripts/instalar.sh, substituíndo o .env) e volve executalo"
fi
# Servizos en marcha que aínda non están sans («starting» ou xa «unhealthy»): poden estar só
# lentos. Os que pararon ou reinician non saen aquí.
servizos_lentos() {
  local c
  for c in $(docker compose ps -q); do
    docker inspect -f '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}} {{index .Config.Labels "com.docker.compose.service"}}' "$c"
  done | awk '$1 == "running" && ($2 == "starting" || $2 == "unhealthy") {print $3}'
}
# Agarda ata que eses servizos estean sans; falla se pasa o tempo ou algún deixa de estar en marcha.
agardar_saude() { # segundos servizo...
  local fin_espera=$((SECONDS + $1)) s estado pendente
  shift
  while ((SECONDS < fin_espera)); do
    pendente=0
    for s in "$@"; do
      estado=$(docker inspect -f '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' \
        "$(docker compose ps -q "$s")" 2>/dev/null || echo parado)
      case $estado in
        "running healthy") ;;
        "running starting" | "running unhealthy") pendente=1 ;;
        *) return 1 ;;
      esac
    done
    ((pendente)) || return 0
    sleep 10
  done
  return 1
}
non_san() {
  docker compose ps
  falla "algún servizo non quedou san: docker compose logs --tail 50 (docs/instalacion.md §9)"
}
titulo "Arrincando (o primeiro arranque tarda uns 2 minutos)"
if ! docker compose up -d --wait --wait-timeout 600; then
  # Compose rende en canto unha dependencia queda «unhealthy», aínda que siga arrincando: Keycloak
  # importa o realm no primeiro arranque e, nun servidor lento, pasa do prazo do healthcheck.
  lentos=$(servizos_lentos | tr '\n' ' ')
  [[ -n ${lentos// /} ]] || non_san
  aviso "aínda arrincando: ${lentos% }. Keycloak tarda en arrincar a primeira vez (importa o realm); nun servidor lento pode pasar de 5 minutos. Agardo ata 15 minutos máis…"
  # shellcheck disable=SC2086 # lista de servizos, un por palabra
  agardar_saude 900 $lentos || non_san
  ok "xa están sans: ${lentos% }"
  docker compose up -d --wait --wait-timeout 600 || non_san
fi
ok "os servizos están en marcha"
if ! command -v curl >/dev/null; then
  aviso "sen curl: comproba ti https://$dominio/healthz"
elif [[ $(curl -fsS -m 15 "https://$dominio/healthz" 2>/dev/null) == ok ]]; then
  ok "https://$dominio/healthz responde"
elif [[ $(curl -fsSk -m 15 "https://$dominio/healthz" 2>/dev/null) == ok ]]; then
  # Responde, pero este equipo aínda non confía no certificado.
  aviso "https://$dominio responde, pero o certificado aínda non é de confianza: normal mentres Let's Encrypt o emite (uns minutos, co DNS e o porto 80 abertos) ou se o asina unha CA interna que este equipo non coñece"
else
  aviso "https://$dominio aínda non responde: revisa o DNS, os portos 80 e 443 e docker compose logs web"
fi
cat <<FIN

Furar está instalado. Seguintes pasos (docs/instalacion.md §3):
  1. Abre https://$dominio
  2. «Entrar» → «Crear a conta» con ${correo_admin:-o correo de FURAR_ADMIN_EMAIL no .env}
  3. Verifica o correo: volves na alta do primeiro concello.
  4. Enche os datos legais do concello (pestana «Legal»).
FIN
