#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# D35 · Furar cabe en 3,5 GB (ADR-0015).
#   scripts/memoria.sh                          límites, uso do compose full e pico do build (≤ 3.584 MiB)
#   scripts/memoria.sh --iniciar FICH ID        crea o ficheiro de mostras desta execución (falla se xa existe)
#   scripts/memoria.sh --mostrar FICH FASE      engade unha mostra «FASE MiB» (FASE: antes | despois)
#   scripts/memoria.sh --vixiar FICH PID        engade «durante MiB» cada segundo mentres viva PID (o pai);
#                                               se o pai morre (mesmo con kill -9), o vixiante sae só
#   FURAR_MOSTRAS_BUILD=FICH FURAR_EXECUCION=ID ficheiro de mostras de D29 e execución á que pertence
#   FURAR_PICO_OBRIGATORIO=1                    sen mostras válidas, D35 falla (verificar.sh activao se correu D29)
#
# O ficheiro de mostras é propio de cada execución e só ten sentido en orde: cabeceira, unha mostra
# «antes», ≥ FURAR_MOSTRAS_MIN (10) «durante» e unha «despois» ao final. Unha mostra despois de
# «despois» delata un vixiante vivo doutra execución e fai fallar D35.
set -euo pipefail
source "$(dirname "$0")/lib/comun.sh"
LIMITE_MIB=${FURAR_LIMITE_MIB:-3584}
MOSTRAS_MIN=${FURAR_MOSTRAS_MIN:-10}

memoria_vm_mib() { # memoria usada (MemTotal - MemAvailable) da máquina onde corre Docker
  local info
  if command -v colima >/dev/null 2>&1 && colima status >/dev/null 2>&1; then
    info=$(colima ssh -- cat /proc/meminfo 2>/dev/null) || return 1
  elif [[ -r /proc/meminfo ]]; then
    info=$(cat /proc/meminfo)
  else
    return 1
  fi
  awk '/^MemTotal:/{t=$2} /^MemAvailable:/{a=$2} END{if (t > 0) printf "%d\n", (t-a)/1024; else exit 1}' <<<"$info"
}

mostra() { # fich fase: engade a mostra; o ficheiro ten que existir (creado por --iniciar)
  local m
  [[ -f $1 ]] || { aviso "non existe $1 (falta --iniciar)"; return 1; }
  m=$(memoria_vm_mib) || return 0 # a VM non respondeu: sen mostra (D35 contará as que faltan)
  printf '%s %s\n' "$2" "$m" >>"$1"
}

case "${1:-}" in
  --iniciar)
    fich=${2:?uso: memoria.sh --iniciar FICHEIRO ID}; id=${3:?uso: memoria.sh --iniciar FICHEIRO ID}
    ( set -o noclobber; printf 'execucion %s\n' "$id" >"$fich" ) 2>/dev/null || falla "$fich xa existe"
    exit 0 ;;
  --mostrar)
    fase=${3:?uso: memoria.sh --mostrar FICHEIRO antes|despois}
    [[ $fase == antes || $fase == despois ]] || falla "fase descoñecida: $fase"
    mostra "${2:?}" "$fase"; exit $? ;;
  --vixiar)
    fich=${2:?uso: memoria.sh --vixiar FICHEIRO PID_PAI}; pai=${3:?uso: memoria.sh --vixiar FICHEIRO PID_PAI}
    while kill -0 "$pai" 2>/dev/null && [[ -f $fich ]]; do
      mostra "$fich" durante || exit 1
      sleep 1
    done
    exit 0 ;;
  --*) falla "opción descoñecida: $1" ;;
esac

titulo "D35 · Memoria (límite $LIMITE_MIB MiB)"

# 1. Suma dos mem_limit do perfil full.
limites=$(dc config --format json | jq '[.services[] | (.mem_limit // empty | tonumber)] | add / 1048576 | floor')
sen_limite=$(dc config --format json | jq -r '[.services | to_entries[] | select(.value.mem_limit == null) | .key] | join(" ")')
[[ -z "$sen_limite" ]] || falla "servizos sen mem_limit: $sen_limite"
(( limites <= LIMITE_MIB )) || falla "suma de mem_limit = $limites MiB > $LIMITE_MIB"
ok "suma de mem_limit = $limites MiB"

# 2. Uso real dos contedores levantados (tras o e2e en verificar.sh).
ids=$(dc ps -q)
if [[ -n "$ids" ]]; then
  # shellcheck disable=SC2086
  uso=$(docker stats --no-stream --format '{{.Name}} {{.MemUsage}}' $ids | awk '
    function mib(v) { if (v ~ /GiB/) return v*1024; if (v ~ /MiB/) return v+0; if (v ~ /KiB/) return v/1024; return v/1048576 }
    { printf "    %-28s %7.0f MiB\n", $1, mib($2); total += mib($2) } END { printf "TOTAL %d\n", total }')
  grep -v '^TOTAL' <<<"$uso"
  total=$(awk '/^TOTAL/{print $2}' <<<"$uso")
  (( total <= LIMITE_MIB )) || falla "uso actual = $total MiB > $LIMITE_MIB"
  ok "uso actual dos contedores = $total MiB"
else
  aviso "compose full non está levantado: sen medida de uso"
fi

# 3. Pico da VM durante a construción das imaxes (D29).
mostras=${FURAR_MOSTRAS_BUILD:-}
if [[ -z $mostras || ! -f $mostras ]]; then
  [[ ${FURAR_PICO_OBRIGATORIO:-} == 1 ]] && falla "sen ficheiro de mostras do build (${mostras:-FURAR_MOSTRAS_BUILD baleiro})"
  aviso "sen medida do pico do build (mídese en scripts/verificar.sh cando corre D29)"
else
  # Valida orde e formato e calcula as cifras nunha soa pasada; calquera anomalía sae con motivo.
  res=$(awk -v id="${FURAR_EXECUCION:-}" '
    function erro(m) { print "ERRO " m; e = 1; exit }
    NR == 1 { if ($1 != "execucion") erro("sen cabeceira"); if (id != "" && $2 != id) erro("o ficheiro é da execución " $2 ", non da " id); next }
    $2 !~ /^[0-9]+$/ || NF != 2 { erro("liña " NR " mal formada: " $0) }
    fin { erro("mostra «" $1 "» despois da de «despois» (liña " NR "): hai outro vixiante escribindo neste ficheiro") }
    $1 == "antes"   { if (NR != 2 || na) erro("a mostra «antes» non é a primeira"); na++; antes = $2 }
    $1 == "durante" { if (!na) erro("mostra «durante» sen «antes»"); nd++ }
    $1 == "despois" { if (!na) erro("mostra «despois» sen «antes»"); fin = 1; despois = $2 }
    $1 != "antes" && $1 != "durante" && $1 != "despois" { erro("fase descoñecida: " $1) }
    { if ($2 + 0 > pico) pico = $2 + 0 }
    END { if (e) exit; if (!na) erro("falta a mostra «antes»"); if (!fin) erro("falta a mostra «despois»")
          print "OK", antes, pico, despois, nd }' "$mostras")
  [[ $res == OK\ * ]] || falla "mostras do build en $mostras: ${res#ERRO }"
  read -r _ antes pico despois n <<<"$res"
  (( n >= MOSTRAS_MIN )) || falla "só $n mostras durante o build (mínimo $MOSTRAS_MIN)"
  (( pico >= antes && pico >= despois )) || falla "pico $pico MiB menor ca antes ($antes) ou despois ($despois)"
  (( pico <= LIMITE_MIB )) || falla "pico de memoria da VM durante o build = $pico MiB > $LIMITE_MIB"
  ok "VM durante o build: antes $antes MiB · pico $pico MiB · despois $despois MiB ($n mostras durante)"
fi
if m=$(memoria_vm_mib); then ok "memoria usada agora pola VM de Docker = $m MiB (inclúe o compose full; non se compara co pico)"; fi
echo "D35 OK"
