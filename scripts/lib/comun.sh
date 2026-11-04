# shellcheck shell=bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Funcións comúns dos scripts de proba e operación (bash). Uso: source scripts/lib/comun.sh

FURAR_RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FURAR_URL="${FURAR_URL:-http://localhost:${FURAR_HTTP_PORT:-8080}}"
# Contrasinal (público, só demo) dos usuarios do realm de demo: a fonte é o overlay.
FURAR_DEMO_PASSWORD="${FURAR_DEMO_PASSWORD:-$(jq -r '[.users[] | select(.username == "xestor-naron") | .credentials[]? | select(.type == "password") | .value][0] // empty' \
  "$FURAR_RAIZ/deploy/keycloak/demo/overlay.json" 2>/dev/null || true)}"
# Argumentos de compose: local `--profile full`; produción `-f compose.yaml -f compose.prod.yaml`
# (ou nada, se .env define COMPOSE_FILE: docs/instalacion.md).
if [[ -z "${FURAR_COMPOSE_ARGS+x}" ]]; then
  if [[ -n "${COMPOSE_FILE:-}" ]] || grep -qE '^COMPOSE_FILE=' "$FURAR_RAIZ/.env" 2>/dev/null; then
    FURAR_COMPOSE_ARGS=""
  else
    FURAR_COMPOSE_ARGS="--profile full"
  fi
fi
read -r -a FURAR_COMPOSE <<<"$FURAR_COMPOSE_ARGS"

# Valor dunha variable do .env do servidor (cron non carga o .env). Sen .env ou sen a variable,
# baleiro e código 0.
desde_env() {
  [[ -f $FURAR_RAIZ/.env ]] || return 0
  sed -nE "s/^$1=(.*)$/\1/p" "$FURAR_RAIZ/.env" | tail -1
}
# Versión de Furar que despregaría agora compose (a mesma regra ca compose.yaml: baleiro → dev).
furar_version_actual() { local v=${FURAR_VERSION:-$(desde_env FURAR_VERSION)}; echo "${v:-dev}"; }

# shellcheck source=probas-api.sh
source "$FURAR_RAIZ/scripts/lib/probas-api.sh"

# Compatible con bash 3.2 (macOS): array baleiro con set -u.
dc() { (cd "$FURAR_RAIZ" && docker compose --progress quiet ${FURAR_COMPOSE[@]+"${FURAR_COMPOSE[@]}"} "$@"); }

titulo() { printf '\n\033[1m» %s\033[0m\n' "$*"; }
ok() { printf '  ✓ %s\n' "$*"; }
aviso() { printf '  ! %s\n' "$*" >&2; }
falla() { printf '  ✗ %s\n' "$*" >&2; exit 1; }

# Cerrollo exclusivo ata que remate o proceso (OPS-R3-6). Linux: flock no descritor 9 (o kernel
# sóltao aínda que o proceso morra). Sen flock (macOS): directorio co PID; se ese PID xa non
# existe, o cerrollo está abandonado e cóllese. Devolve 1 se outro proceso vivo o ten.
cerrollo() { # ficheiro
  if command -v flock >/dev/null; then
    exec 9>"$1"
    flock -n 9
    return
  fi
  local pid propio
  propio=$(exec sh -c 'echo "$PPID"') # PID deste (sub)shell; bash 3.2 non ten BASHPID
  if ! mkdir "$1.d" 2>/dev/null; then
    pid=$(cat "$1.d/pid" 2>/dev/null || true)
    if [[ -n $pid ]] && kill -0 "$pid" 2>/dev/null; then return 1; fi
    rm -rf "$1.d"
    mkdir "$1.d" 2>/dev/null || return 1
  fi
  echo "$propio" >"$1.d/pid"
  # shellcheck disable=SC2064 # o camiño fíxase agora
  trap "rm -rf '$1.d'" EXIT
}

# Compara un valor co esperado e para se non coincide.
esperar() { # descrición obtido esperado
  if [[ "$2" == "$3" ]]; then ok "$1 ($2)"; else falla "$1: obtido «$2», esperado «$3»"; fi
}

# Código HTTP dunha petición (resto de argumentos para curl).
http_code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

# Cabeceira (sen distinguir maiúsculas) dunha resposta; baleiro se non existe.
cabeceira() { # nome url [args curl]
  local nome=$1; shift
  curl -s -o /dev/null -D - "$@" | tr -d '\r' | awk -v n="$(tr '[:upper:]' '[:lower:]' <<<"$nome")" -F': ' 'tolower($1)==n {sub(/^[^:]*: /,""); print; exit}'
}

# Carga útil dun JWT (JSON).
jwt_payload() {
  local p
  p=$(cut -d. -f2 <<<"$1" | tr '_-' '/+')
  while (( ${#p} % 4 )); do p+='='; done
  base64 -d <<<"$p" 2>/dev/null || base64 -D <<<"$p"
}

# POST ao endpoint de token; imprime o access_token. Se falla, deixa a causa en stderr: código
# HTTP, erro de curl, corpo (sen o token) e as últimas liñas de keycloak e web (QA4: un fallo
# intermitente sen rexistro non se pode diagnosticar). Sen reintentos: un fallo é un fallo.
pedir_token() { # url [args curl]
  local url=$1 corpo saida rc=0; shift
  corpo=$(mktemp)
  saida=$(curl -sS -m 60 -o "$corpo" -w '%{http_code}' "$@" "$url" 2>&1) || rc=$?
  if [[ $rc == 0 && $saida == 200 ]] && jq -e '.access_token|strings' "$corpo" >/dev/null 2>&1; then
    jq -r .access_token "$corpo"; rm -f "$corpo"; return 0
  fi
  {
    printf '  ! token: POST %s → HTTP %s, curl %s\n' "$url" "${saida: -3}" "$rc"
    [[ $rc == 0 ]] || printf '    curl: %s\n' "$saida"
    printf '    corpo: %s\n' "$(head -c 400 "$corpo")"
    dc logs --no-color --timestamps --tail 15 keycloak web 2>&1 | sed 's/^/    /' || true
  } >&2
  rm -f "$corpo"
  return 1
}

# Token de acceso dun usuario de demo (cliente furar-e2e, só no realm de demo).
token() { # usuario [contrasinal]
  pedir_token "$FURAR_URL/auth/realms/furar/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=furar-e2e --data-urlencode "username=$1" \
    --data-urlencode "password=${2:-$FURAR_DEMO_PASSWORD}" -d scope=openid
}

# Administración de Keycloak: por Caddy en local; en produción só no porto local 8082
# (compose.prod.yaml, KC_HOSTNAME_ADMIN), p. ex. FURAR_KC_ADMIN_URL=http://localhost:8082/auth
FURAR_KC_ADMIN_URL="${FURAR_KC_ADMIN_URL:-$FURAR_URL/auth}"

# Token de administración de Keycloak (realm master).
token_admin_keycloak() {
  pedir_token "$FURAR_KC_ADMIN_URL/realms/master/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=admin-cli \
    --data-urlencode "username=${KC_BOOTSTRAP_ADMIN_USERNAME:-admin}" \
    --data-urlencode "password=${KC_BOOTSTRAP_ADMIN_PASSWORD:-admin-demo}"
}

# Agarda a que un URL responda o código esperado.
agardar_http() { # url codigo [segundos]
  local fin=$((SECONDS + ${3:-120}))
  until [[ "$(http_code "$1")" == "$2" ]]; do
    (( SECONDS < fin )) || falla "$1 non respondeu $2 en ${3:-120} s"
    sleep 3
  done
}

# Crea un concello pola API como admin-plataforma; imprime o slug.
crear_concello() { # slug
  local t corpo codigo
  t=$(token admin-plataforma) || falla "sen token de admin-plataforma"
  corpo=$(furar_corpo_concello "$1")
  codigo=$(http_code -X POST "$FURAR_URL$FURAR_API_CREAR_CONCELLO" -H "Authorization: Bearer $t" \
    -H 'Content-Type: application/json' -d "$corpo")
  [[ "$codigo" == 201 || "$codigo" == 200 ]] || falla "POST $FURAR_API_CREAR_CONCELLO → $codigo (corpo: $corpo)"
}

existe_concello() { # slug → 0 se GET dá 200
  [[ "$(http_code "$FURAR_URL$FURAR_API_CREAR_CONCELLO/$1")" == 200 ]]
}

# Crea un usuario en Keycloak (realm furar) con contrasinal fixo. O correo é o usuario (P13):
# Keycloak garda USUARIO@example.org como nome de usuario, e con iso se entra.
crear_usuario_keycloak() { # usuario contrasinal
  local a
  a=$(token_admin_keycloak) || falla "sen token de administración de Keycloak"
  curl -sf -X POST "$FURAR_KC_ADMIN_URL/admin/realms/furar/users" -H "Authorization: Bearer $a" \
    -H 'Content-Type: application/json' -d "$(jq -n --arg u "$1" --arg p "$2" '{username:$u, enabled:true,
      email:($u+"@example.org"), emailVerified:true, firstName:"Proba", lastName:"Copia",
      credentials:[{type:"password", value:$p, temporary:false}]}')" >/dev/null \
    || falla "non se puido crear o usuario $1 en Keycloak"
}
