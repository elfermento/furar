#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# D48 · O overlay de produción levántase de verdade, como na guía (docs/instalacion.md):
#   1. Publica as imaxes locais (furar-*:local, de D29) nun rexistro local (registry, fixado)
#      e anota os digests, como fai a CI en GHCR.
#   2. Copia os ficheiros versionables a un directorio limpo e escribe un .env de produción
#      falso pero válido: segredos aleatorios, FURAR_PUBLIC_URL=https://furar.internal:8443
#      (Caddy usa a súa CA interna para .internal; ProdProfileGuard rexeita localhost),
#      COMPOSE_FILE=compose.yaml:compose.prod.yaml e FURAR_ADMIN_IPS=127.0.0.1/32.
#   3. `docker compose pull` (desde o rexistro, por etiqueta e digest) e `up -d --wait`.
#   4. Comproba: /healthz por HTTPS; OIDC co iss público; administración pechada polo URL
#      público e aberta en 127.0.0.1:8082; realm sen usuarios, sen furar-e2e, con eventos, sen
#      segundo factor (fluxo browser de Keycloak, sen furar-2fa, sen acr.loa.map, sen accións de
#      OTP nin de códigos) e coa política de contrasinais con lista de comúns (un común da lista
#      rexéitase); API en prod (401 sen token), sen FURAR_PRIVACY_*; volume pgdata-prod; ninguén é root.
#      Acceso de verdade, con curl e un xarro de cookies por persoa, todo só con contrasinal:
#      veciñanza; plataforma (primeiro acceso coa conta de FURAR_ADMIN_EMAIL, sen grupos nin roles
#      en Keycloak; alta 201, exportación 403; outra conta co mesmo rol no token non dá nada,
#      ADR-0028); persoal invitado desde Furar (o correo
#      sae por SMTP a un Mailpit da proba) que acepta e traballa, e a desactivación vale na
#      seguinte petición. A web non obtén tokens offline.
#      Bloqueo (op.acc.6.8): 4 fallos seguidos non bloquean; o 5.º bloquea 15 minutos (estado pola
#      API de administración, sen esperar), o aviso xenérico sae en cada fallo e, bloqueada, o
#      contrasinal bo dá o mesmo erro ca un malo.
#   5. copia.sh e restaurar.sh con COMPOSE_FILE (sen FURAR_COMPOSE_ARGS): ida e volta.
# Precisa os portos 8080, 8443, 8082, 5000 e 18025 libres (baixa antes o perfil full).
set -euo pipefail
source "$(dirname "$0")/lib/comun.sh"

host=furar.internal
url=https://$host:8443
proxecto=furar-prod-proba
rexistro_porto=5000
rexistro=localhost:$rexistro_porto/furar
version=proba-$(date +%s)
registry=$(awk '/^registry:/{print $1"@"$2}' "$FURAR_RAIZ/scripts/imaxes-ferramentas.txt")
# SMTP da proba: o Mailpit fixado en compose.yaml, na rede do proxecto (como o SMTP do concello).
mailpit=$(awk '$1 == "image:" && $2 ~ /^axllent\/mailpit:/ {print $2; exit}' "$FURAR_RAIZ/compose.yaml")
correo_porto=18025
c() { curl -sk --resolve "$host:8443:127.0.0.1" "$@"; }
# Contorno limpo: na CI, FURAR_VERSION e FURAR_REVISION do traballo e2e gañarían ao .env
# (Compose prefire o contorno). Aquí só manda o .env, como nun servidor.
limpo() { env -i PATH="$PATH" HOME="$HOME" ${DOCKER_HOST:+DOCKER_HOST="$DOCKER_HOST"} "$@"; }
dcp() { limpo docker compose "$@"; }
digest_api="" digest_web="" digest_keycloak=""
codigo() { c -o /dev/null -w '%{http_code}' "$@"; }

titulo "D48 · Produción (compose.prod.yaml) en $url"
for i in furar-api:local furar-web:local furar-keycloak:local; do
  docker image inspect "$i" >/dev/null 2>&1 || falla "falta a imaxe $i (D29: constrúe en serie)"
done

tmp=$(mktemp -d "$FURAR_RAIZ/.verificar/prod.XXXXXX")
limpar() {
  (cd "$tmp/furar" 2>/dev/null && dcp down -v >/dev/null 2>&1) || true
  docker rm -f furar-rexistro-proba furar-correo-proba >/dev/null 2>&1 || true
  for s in api web keycloak; do docker rmi "$rexistro/furar-$s:$version" >/dev/null 2>&1 || true; done
  rm -rf "$tmp"
}
trap limpar EXIT

# 1. Rexistro local e publicación ------------------------------------------------------------
docker rm -f furar-rexistro-proba >/dev/null 2>&1 || true
docker run -d --rm --name furar-rexistro-proba -p "127.0.0.1:$rexistro_porto:5000" "$registry" >/dev/null
for _ in $(seq 30); do curl -sf "http://127.0.0.1:$rexistro_porto/v2/" >/dev/null 2>&1 && break; sleep 1; done
for s in api web keycloak; do
  docker tag "furar-$s:local" "$rexistro/furar-$s:$version"
  docker push -q "$rexistro/furar-$s:$version" >/dev/null
  d=$(docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$rexistro/furar-$s:$version" \
    | grep -F "$rexistro/furar-$s@" | head -1 | cut -d@ -f2)
  [[ $d == sha256:* ]] || falla "sen digest para furar-$s"
  printf -v "digest_$s" %s "${d#sha256:}"
  # Sen a etiqueta local, `pull` ten que descargala de verdade do rexistro.
  docker rmi -f "$rexistro/furar-$s:$version" >/dev/null
done
ok "imaxes publicadas en $rexistro ($version)"

# 2. Instalación limpa con .env de produción -------------------------------------------------
mkdir -p "$tmp/furar"
(cd "$FURAR_RAIZ" && git ls-files -z -co --exclude-standard | while IFS= read -r -d '' f; do [[ -e "$f" ]] && printf '%s\0' "$f"; done \
  | tar --null -T - -cf -) | tar -xf - -C "$tmp/furar"
cd "$tmp/furar"
segredo() { openssl rand -hex 24; }
cat >.env <<EOF
COMPOSE_PROJECT_NAME=$proxecto
COMPOSE_FILE=compose.yaml:compose.prod.yaml
FURAR_PUBLIC_URL=$url
FURAR_REGISTRY=$rexistro
FURAR_VERSION=$version
FURAR_API_DIGEST=$digest_api
FURAR_WEB_DIGEST=$digest_web
FURAR_KEYCLOAK_DIGEST=$digest_keycloak
POSTGRES_PASSWORD=$(segredo)
FURAR_DB_OWNER_PASSWORD=$(segredo)
FURAR_DB_PASSWORD=$(segredo)
KC_DB_PASSWORD=$(segredo)
FURAR_INVITATION_PROOF_SECRET=$(segredo)
KC_BOOTSTRAP_ADMIN_USERNAME=admin-proba
KC_BOOTSTRAP_ADMIN_PASSWORD=$(segredo)
FURAR_SMTP_HOST=correo-proba
FURAR_SMTP_PORT=1025
FURAR_SMTP_STARTTLS=false
FURAR_SMTP_AUTH=false
FURAR_SMTP_FROM=furar@$host
FURAR_ADMIN_IPS=127.0.0.1/32
FURAR_ADMIN_EMAIL=Plataforma-Proba@example.org
FURAR_HTTP_PORT=8080
FURAR_HTTPS_PORT=8443
EOF
chmod 600 .env
ok "instalación en $tmp/furar con .env de produción"

# 3. Pull e arranque -------------------------------------------------------------------------
limpo scripts/preflight.sh >/dev/null || { limpo scripts/preflight.sh; falla "preflight.sh"; }
ok "preflight.sh"
pull=$(dcp pull 2>&1) || { echo "$pull"; falla "docker compose pull"; }
[[ $(grep -c "Skipped Image is already present" <<<"$pull" | tr -d ' ') -le 1 ]] || { echo "$pull"; falla "pull non descargou as imaxes de Furar do rexistro"; }
ok "imaxes descargadas do rexistro por etiqueta e digest"
dcp up -d --wait --wait-timeout 420 >/dev/null 2>&1 || { dcp ps; dcp logs --tail 40; falla "produción non quedou san"; }
servizos=$(dcp ps --services | sort | tr '\n' ' ')
docker run -d --rm --name furar-correo-proba --network "${proxecto}_default" --network-alias correo-proba \
  --read-only --tmpfs /tmp --user 1000:1000 -p "127.0.0.1:$correo_porto:8025" "$mailpit" >/dev/null
for _ in $(seq 30); do curl -sf "http://127.0.0.1:$correo_porto/readyz" >/dev/null 2>&1 && break; sleep 1; done
esperar "servizos" "$servizos" "api keycloak postgres web "
# SEC2-03: migrate migrou e saíu; a API non ten o contrasinal do dono do esquema.
esperar "migrate rematou con 0" "$(docker inspect -f '{{.State.ExitCode}}' "$(dcp ps -a -q migrate)")" 0
esperar "a API sen FURAR_DB_OWNER_PASSWORD" "$(dcp exec -T api sh -c 'test -z "${FURAR_DB_OWNER_PASSWORD:-}" && echo ausente')" ausente

# 4. Comprobacións ---------------------------------------------------------------------------
esperar "GET $url/healthz (HTTPS, CA interna de Caddy)" "$(c "$url/healthz")" ok
esperar "HTTP redirixe a HTTPS" "$(curl -s -o /dev/null -w '%{http_code}' --resolve "$host:8080:127.0.0.1" "http://$host:8080/")" 308
esperar "iss do realm furar" "$(c "$url/auth/realms/furar/.well-known/openid-configuration" | jq -r .issuer)" "$url/auth/realms/furar"
esperar "/naron (web)" "$(codigo "$url/naron")" 200
esperar "POST $FURAR_API_CREAR_CONCELLO sen token" "$(codigo -X POST -H 'Content-Type: application/json' -d '{}' "$url$FURAR_API_CREAR_CONCELLO")" 401
# Sen FURAR_PRIVACY_* (revisión do 2026-11-03): a API arrinca e o despregue só achega os prazos do rexistro.
esperar "GET /api/v1/privacy-notice sen datos de privacidade do despregue" \
  "$(c "$url/api/v1/privacy-notice" | jq -r 'keys | join(" ")')" "activityRetention activityVisible"
esperar "/auth/admin/ polo URL público" "$(codigo "$url/auth/admin/")" 403
esperar "/auth/realms/master/ polo URL público" "$(codigo "$url/auth/realms/master/")" 403
esperar "perfil da API" "$(dcp exec -T api printenv SPRING_PROFILES_ACTIVE)" prod

admin_url=http://localhost:8082/auth
# Un token novo en cada chamada: o de master caduca aos 60 s e a proba dura varios minutos.
admin_token() {
  curl -sf -d grant_type=password -d client_id=admin-cli -d username=admin-proba \
    --data-urlencode "password=$(grep '^KC_BOOTSTRAP_ADMIN_PASSWORD=' .env | cut -d= -f2)" \
    "$admin_url/realms/master/protocol/openid-connect/token" | jq -r .access_token
}
a=$(admin_token)
[[ -n $a && $a != null ]] || falla "sen token de administración en $admin_url (túnel local)"
ok "administración de Keycloak en $admin_url (só 127.0.0.1)"
kc() { curl -sf -H "Authorization: Bearer $(admin_token)" "$admin_url/admin/realms/furar$1"; }
codigo_admin() { curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $(admin_token)" "$admin_url/admin/realms/furar$1"; }
esperar "usuarios no realm de produción" "$(kc /users/count)" 0
esperar "cliente furar-e2e" "$(kc '/clients?clientId=furar-e2e' | jq length)" 0
realm=$(kc "")
esperar "eventos de usuario" "$(jq -r .eventsEnabled <<<"$realm")" true
esperar "eventos de administración" "$(jq -r .adminEventsEnabled <<<"$realm")" true
esperar "fluxo de acceso (o browser de Keycloak)" "$(jq -r .browserFlow <<<"$realm")" browser
fluxos=$(kc /authentication/flows | jq -r '.[].alias')
for f in browser registration "reset credentials" "direct grant" clients "first broker login"; do
  grep -qx "$f" <<<"$fluxos" || falla "falta o fluxo integrado «$f» no realm importado"
done
grep -q '^furar-browser' <<<"$fluxos" && falla "seguen os fluxos furar-browser do segundo factor"
ok "fluxos integrados de Keycloak, sen furar-browser"
esperar "niveis de acceso (acr.loa.map)" "$(jq -r '.attributes["acr.loa.map"] // "ningún"' <<<"$realm")" ningún
esperar "rol furar-2fa" "$(codigo_admin "/roles/furar-2fa")" 404
esperar "accións de segundo factor activas (CONFIGURE_TOTP, CONFIGURE_RECOVERY_AUTHN_CODES)" "$(kc /authentication/required-actions \
  | jq -r '[.[] | select(.enabled and (.alias | IN("CONFIGURE_TOTP", "CONFIGURE_RECOVERY_AUTHN_CODES"))) | .alias] | length')" 0
esperar "política de contrasinais" "$(jq -r .passwordPolicy <<<"$realm")" \
  "length(12) and notUsername and notEmail and passwordBlacklist(furar-contrasinais-comuns.txt) and maxLength(128)"
esperar "caducidade da ligazón de verificación (s)" "$(jq -r '.attributes["actionTokenGeneratedByUserLifespan.verify-email"]' <<<"$realm")" 1800
esperar "grupos do realm (ningún, ADR-0028)" "$(kc /groups | jq -r 'length')" 0
api_cid=$(kc '/clients?clientId=furar-api' | jq -r '.[0].id')
esperar "roles de furar-api (ningún, ADR-0028)" "$(kc "/clients/$api_cid/roles" | jq -r 'length')" 0
kc_post() { curl -sS --fail-with-body -X "$1" -H "Authorization: Bearer $(admin_token)" -H 'Content-Type: application/json' "$admin_url/admin/realms/furar$2" ${3:+-d "$3"}; }
KC_ADMIN_URL=$admin_url KC_ADMIN_TOKEN=$(admin_token) scripts/comprobar-realm.sh --vivo >/dev/null \
  || { KC_ADMIN_URL=$admin_url KC_ADMIN_TOKEN=$(admin_token) scripts/comprobar-realm.sh --vivo || true; falla "comprobar-realm.sh --vivo"; }
ok "comprobar-realm.sh --vivo: sen grupos nin roles, sen segundo factor, o correo como usuario e a política de contrasinais"
# SEC2-01: un rol por concello en Keycloak (o xeito de antes) detéctase.
kc_post POST "/clients/$api_cid/roles" '{"name":"proba:MANAGER"}' >/dev/null
KC_ADMIN_URL=$admin_url KC_ADMIN_TOKEN=$(admin_token) scripts/comprobar-realm.sh --vivo >/dev/null \
  && falla "comprobar-realm.sh --vivo non detecta un rol por concello en Keycloak"
kc_post DELETE "/clients/$api_cid/roles/proba%3AMANAGER" >/dev/null
ok "comprobar-realm.sh --vivo detecta un rol por concello en Keycloak"

# Navegador sen JavaScript: un xarro de cookies por «navegador» ($nav) e os formularios de Keycloak.
# Todas as pantallas do acceso son formularios, así que chega con curl (como a proba D4-02).
nav=""
cn() { c -c "$tmp/xarro-$nav" -b "$tmp/xarro-$nav" -H 'Accept-Language: gl' "$@"; }
seguir() { # segue as redireccións dentro de Keycloak; para ao volver á web ($url/ fóra de /auth)
  local l
  while l=$(grep -i '^location:' "$tmp/r" | tr -d '\r' | sed -E 's/^[Ll]ocation: //'); [[ -n $l && ( $l != "$url/"* || $l == "$url/auth/"* ) ]]; do
    cn -D - "$l" >"$tmp/r"
  done
}
comezar() { # parámetros extra (&kc_action=…) → pantalla en $tmp/r; ámbitos en $ambito
  local v
  v=$(openssl rand -hex 32)
  printf '%s' "$v" >"$tmp/verificador-$nav"
  cn -D - "$url/auth/realms/furar/protocol/openid-connect/auth?client_id=furar-web&response_type=code&scope=${ambito:-openid}&redirect_uri=$url/&code_challenge=$(printf '%s' "$v" \
    | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')&code_challenge_method=S256&ui_locales=gl${1:-}" >"$tmp/r"
  seguir
}
enviar() { # campo=valor… ao primeiro formulario da pantalla
  local accion args=()
  accion=$(grep -oE 'action="[^"]+"' "$tmp/r" | head -1 | sed -E 's/^action="//; s/"$//; s/&amp;/\&/g')
  [[ -n $accion ]] || falla "sen formulario: $(pantalla)"
  for kv in "$@"; do args+=(--data-urlencode "$kv"); done
  cn -D - "${args[@]}" "$accion" >"$tmp/r"
  seguir
}
pantalla() {
  if grep -i '^location:' "$tmp/r" | grep -qF "$url/?"; then echo VOLTA
  elif grep -qE 'name="(totpSecret|otp|recoveryCodeInput)"|generatedRecoveryAuthnCodes' "$tmp/r"; then echo SEGUNDO_FACTOR
  elif grep -q 'name="password"' "$tmp/r"; then echo CONTRASINAL
  else echo "outra: $(grep -oE '<title>[^<]*' "$tmp/r" | head -1)"; fi
}
tokens() { # código de autorización da volta + PKCE → JSON de tokens
  local code
  code=$(grep -i '^location:' "$tmp/r" | grep -oE '[?&]code=[^&[:space:]]+' | cut -d= -f2)
  [[ -n $code ]] || falla "sen código de autorización ($(pantalla))"
  c -d grant_type=authorization_code -d client_id=furar-web --data-urlencode "code=$code" \
    --data-urlencode "redirect_uri=$url/" --data-urlencode "code_verifier=$(cat "$tmp/verificador-$nav")" \
    "$url/auth/realms/furar/protocol/openid-connect/token"
}
refresh() { c -d grant_type=refresh_token -d client_id=furar-web --data-urlencode "refresh_token=$1" \
  "$url/auth/realms/furar/protocol/openid-connect/token"; }
# Sen segundo factor: o token non leva amr (sen o mapeador) e o acr non é un nivel de Furar.
claims() { jwt_payload "$1" | jq -c '{amr, furar2fa: (.acr == "furar-2fa")}'; }
pedir() { # token método ruta [corpo] [cabeceira] → "estado código" (corpo en $tmp/corpo, cabeceiras en $tmp/cab)
  local estado
  estado=$(c -o "$tmp/corpo" -D "$tmp/cab" -w '%{http_code}' -X "$2" -H "Authorization: Bearer $1" -H 'Accept-Language: gl' \
    ${4:+-H 'Content-Type: application/json' -d "$4"} ${5:+-H "$5"} "$url$3")
  printf '%s %s' "$estado" "$(jq -r '.code // empty' "$tmp/corpo" 2>/dev/null)" | sed 's/ $//'
}
# O correo é o usuario (P13): Keycloak pon o correo como nome de usuario ao crear a conta, aínda que
# a API de administración mande outro, así que todas as persoas da proba entran co correo.
persoa() { # nome contrasinal: conta NOME@example.org xa rexistrada e co correo verificado (como tras o rexistro)
  kc_post POST /users "$(jq -n --arg u "$1" --arg p "$2" '{username:$u, enabled:true, email:($u+"@example.org"), emailVerified:true,
    firstName:"Proba", lastName:"Produción", attributes:{locale:["gl"]}, credentials:[{type:"password", value:$p, temporary:false}]}')" >/dev/null
}
# Cumpre a política do realm (12 ou máis, distinto do correo, fóra da lista de comúns).
contrasinal="Proba-1-$(segredo)"
# Un contrasinal común da lista de Furar (passwordBlacklist) non vale, aínda que teña 12 ou máis.
comun=$(grep -m1 -E '^contrasinal[0-9]{4}$' deploy/keycloak/password-blacklists/furar-contrasinais-comuns.txt)
erro_comun=$(curl -s -X POST -H "Authorization: Bearer $(admin_token)" -H 'Content-Type: application/json' "$admin_url/admin/realms/furar/users" \
  -d "$(jq -nc --arg p "$comun" '{username:"comun-proba", enabled:true, email:"comun-proba@example.org", emailVerified:true,
    credentials:[{type:"password", value:$p, temporary:false}]}')" | jq -r '.error // .errorMessage // empty')
esperar "conta cun contrasinal común da lista («$comun»)" "$erro_comun" invalidPasswordBlacklistedMessage

# Veciñanza: só contrasinal, nivel 1; rotación do refresh token (SEC2-04, RFC 9700 §4.14).
# Pedir offline_access desde furar-web, que non ten ese ámbito, volve á web con invalid_scope (R6).
persoa vecino-proba "$contrasinal"
nav=vecino; ambito="openid%20offline_access" comezar
esperar "furar-web non pode pedir offline_access" \
  "$( (grep -i '^location:' "$tmp/r" || true) | grep -oE 'error=[a-z_]+' | head -1 || true)" error=invalid_scope
esperar "o correo é o usuario (creada co usuario vecino-proba)" \
  "$(kc '/users?email=vecino-proba@example.org&exact=true' | jq -r '.[0].username')" vecino-proba@example.org
comezar
grep -q 'Correo electrónico' "$tmp/r" && ! grep -q 'Nome de usuario ou correo' "$tmp/r" \
  || falla "a pantalla de acceso non pide «Correo electrónico» (P13)"
enviar "username=vecino-proba@example.org" "password=$contrasinal"
esperar "veciño: entra só co contrasinal" "$(pantalla)" VOLTA
tokens=$(tokens)
esperar "veciño: token sen amr nin nivel de Furar" "$(claims "$(jq -r .access_token <<<"$tokens")")" '{"amr":null,"furar2fa":false}'
r1=$(jq -r .refresh_token <<<"$tokens")
esperar "refresh token da web (non offline)" "$(jwt_payload "$r1" | jq -r .typ)" Refresh
esperar "1.ª renovación co refresh token" "$(refresh "$r1" | jq -r 'has("access_token")')" true
esperar "2.ª renovación co mesmo refresh token" "$(refresh "$r1" | jq -r .error)" invalid_grant

# Bloqueo tras intentos fallidos (op.acc.6.8, docs/ens.md): 5 fallos → 15 minutos, temporal.
# Keycloak conta os fallos nun fío aparte: o estado (attack-detection) lese ata que chega.
persoa bloqueo-proba "$contrasinal"
bid=$(kc '/users?email=bloqueo-proba@example.org&exact=true' | jq -r '.[0].id')
forza_bruta() { kc "/attack-detection/brute-force/users/$bid"; }
agardar_fallos() { # número → estado cando numFailures chega a el (ou o último, aos 10 s)
  local e _
  for _ in $(seq 20); do
    e=$(forza_bruta); [[ $(jq -r .numFailures <<<"$e") == "$1" ]] && break; sleep 0.5
  done
  printf '%s' "$e"
}
aviso_bloqueo='Despois de 5 intentos fallidos, a conta bloquéase 15 minutos. Se xa está bloqueada, agarda ese tempo ou usa «Esqueciches o contrasinal?».'
erro_acceso='O correo electrónico ou o contrasinal non son correctos.'
nav=bloqueo; comezar
for i in 1 2 3 4; do
  enviar "username=bloqueo-proba@example.org" "password=mal-$i-Contrasinal"
  [[ $(pantalla) == CONTRASINAL ]] || falla "fallo $i: $(pantalla)"
  tr -s ' \n' ' ' <"$tmp/r" | grep -qF "$aviso_bloqueo" || falla "fallo $i sen o aviso «$aviso_bloqueo»"
done
estado=$(agardar_fallos 4)
esperar "4 fallos seguidos: sen bloqueo (nin o de dous fallos rápidos)" "$(jq -c '{disabled, numFailures}' <<<"$estado")" '{"disabled":false,"numFailures":4}'
enviar "username=bloqueo-proba@example.org" "password=mal-5-Contrasinal"
estado=$(agardar_fallos 5)
esperar "5.º fallo: conta bloqueada" "$(jq -r .disabled <<<"$estado")" true
esperar "espera do bloqueo (failedLoginNotBefore − lastFailure, s)" \
  "$(jq -r '.failedLoginNotBefore - ((.lastFailure / 1000) | floor)' <<<"$estado")" 900
enviar "username=bloqueo-proba@example.org" "password=$contrasinal"
esperar "bloqueada, co contrasinal bo: segue no acceso" "$(pantalla)" CONTRASINAL
tr -s ' \n' ' ' <"$tmp/r" | grep -qF "$erro_acceso" || falla "bloqueada: o erro non é o mesmo ca o dun contrasinal malo (enumeración)"
tr -s ' \n' ' ' <"$tmp/r" | grep -qF "$aviso_bloqueo" || falla "bloqueada: sen o aviso do bloqueo"
ok "bloqueada: mesmo erro ca un contrasinal malo e o aviso «agarda ou Esqueciches o contrasinal?»"
kc_post DELETE "/attack-detection/brute-force/users/$bid" >/dev/null
nav=bloqueo-fin; comezar; enviar "username=bloqueo-proba@example.org" "password=$contrasinal"
esperar "desbloqueada desde a administración: entra" "$(pantalla)" VOLTA

# Plataforma (ADR-0028): o rol vive na base de Furar. Outra conta, co rol no token como facía o
# grupo de antes, non dá nada; a conta co correo de FURAR_ADMIN_EMAIL (verificado, maiúsculas
# distintas) pasa a ser a administración na súa primeira petición, unha vez e no rexistro.
persoa outra-plataforma "$contrasinal"
nav=outra-plataforma; comezar; enviar "username=outra-plataforma@example.org" "password=$contrasinal"
esperar "outra conta: entra" "$(pantalla)" VOLTA
t_outra=$(tokens | jq -r .access_token)
esperar "outra conta: sen furar_roles no token" "$(jwt_payload "$t_outra" | jq -c '.furar_roles // "ningún"')" '"ningún"'
esperar "outra conta: non é a plataforma" "$(c -H "Authorization: Bearer $t_outra" "$url/api/v1/me/platform-access" | jq -c .)" '{"platformAdmin":false}'
esperar "outra conta: alta do concello" "$(pedir "$t_outra" POST "$FURAR_API_CREAR_CONCELLO" "$(furar_corpo_concello proba-outra)")" "403 auth.forbidden"
persoa plataforma-proba "$contrasinal"
nav=plataforma; comezar; enviar "username=plataforma-proba@example.org" "password=$contrasinal"
esperar "plataforma: entra só co contrasinal" "$(pantalla)" VOLTA
tokens=$(tokens); t_plat=$(jq -r .access_token <<<"$tokens")
acceso=$(c -H "Authorization: Bearer $t_plat" "$url/api/v1/me/platform-access")
esperar "plataforma: primeiro acceso (FURAR_ADMIN_EMAIL)" "$(jq -c . <<<"$acceso")" '{"platformAdmin":true}'
grep -qi 'plataforma-proba@' <<<"$acceso" && falla "a resposta leva o correo de FURAR_ADMIN_EMAIL"
esperar "plataforma: unha fila, do primeiro acceso" "$(dcp exec -T postgres psql -U postgres -d furar -tAc \
  "select count(*) || ' ' || min(granted_by) from platform_admin")" "1 furar:first-run"
esperar "plataforma: o evento platform.first-admin, sen o correo" "$(dcp exec -T postgres psql -U postgres -d furar -tAc \
  "select count(*) from audit_event where action = 'platform.first-admin' and details::text not ilike '%example.org%'")" 1
esperar "plataforma: alta do concello" "$(pedir "$t_plat" POST "$FURAR_API_CREAR_CONCELLO" "$(furar_corpo_concello proba)")" 201
t_plat=$(refresh "$(jq -r .refresh_token <<<"$tokens")" | jq -r .access_token)
esperar "plataforma: alta co token renovado" "$(pedir "$t_plat" POST "$FURAR_API_CREAR_CONCELLO" "$(furar_corpo_concello proba-renovado)")" 201
esperar "plataforma: exportación (non ve os datos do concello)" "$(pedir "$t_plat" GET "$FURAR_API_CREAR_CONCELLO/proba/export")" "403 auth.forbidden"

# Persoal (ADR-0027): Keycloak non sabe nada del. A plataforma invita á primeira persoa xestora
# desde Furar, o correo sae polo SMTP (Mailpit da proba) e a persoa acepta coa súa conta.
staff=$FURAR_API_CREAR_CONCELLO/proba/staff
convite() { # correo → token da ligazón do correo de invitación
  local id="" _
  for _ in $(seq 20); do
    id=$(curl -sf "http://127.0.0.1:$correo_porto/api/v1/search?query=to:$1" | jq -r '.messages[0].ID // empty')
    [[ -n $id ]] && break
    sleep 1
  done
  [[ -n $id ]] || falla "a invitación para $1 non chegou ao SMTP"
  curl -sf "http://127.0.0.1:$correo_porto/api/v1/message/$id" | jq -r '.Text + .HTML' | grep -oE "$url/proba/convite#[A-Za-z0-9_-]+" | head -1 | cut -d'#' -f2
}
for p in xestora-proba xestora2-proba; do
  persoa "$p" "$contrasinal"
  corpo=$(jq -nc --arg e "$p@example.org" '{email: $e, role: "MANAGER", language: "gl"}')
  esperar "plataforma invita a $p (MANAGER)" "$(pedir "$t_plat" POST "$staff" "$corpo")" 201
done
convite_x=$(convite xestora-proba@example.org); convite_x2=$(convite xestora2-proba@example.org)
[[ -n $convite_x && -n $convite_x2 ]] || falla "o correo de invitación non trae a ligazón $url/proba/convite#…"
ok "invitacións entregadas por SMTP coa ligazón $url/proba/convite#…"

# Xestora: entra co contrasinal, acepta a invitación e traballa (sen segundo factor).
aceptar() { jq -nc --arg t "$1" '{token: $t}'; } # token → corpo
nav=xestora; comezar; enviar "username=xestora-proba@example.org" "password=$contrasinal"
esperar "xestora: entra só co contrasinal" "$(pantalla)" VOLTA
tokens=$(tokens); t_xestora=$(jq -r .access_token <<<"$tokens")
estado=$(pedir "$t_xestora" POST "$FURAR_API_CREAR_CONCELLO/proba/staff-invitations/accept" "$(aceptar "$convite_x")")
esperar "xestora acepta co contrasinal" "$estado $(jq -r .role "$tmp/corpo")" "200 MANAGER"
esperar "xestora na xestión" "$(pedir "$t_xestora" GET "$staff")" 200
t_xestora=$(refresh "$(jq -r .refresh_token <<<"$tokens")" | jq -r .access_token)
esperar "xestora tras renovar" "$(pedir "$t_xestora" GET "$staff")" 200

# Baixa: a segunda xestora acepta e traballa; a primeira desactívaa e o mesmo token perde o
# acceso na seguinte petición.
nav=xestora2; comezar; enviar "username=xestora2-proba@example.org" "password=$contrasinal"
esperar "xestora2: entra só co contrasinal" "$(pantalla)" VOLTA
t2=$(tokens | jq -r .access_token)
esperar "xestora2 acepta a invitación" "$(pedir "$t2" POST "$FURAR_API_CREAR_CONCELLO/proba/staff-invitations/accept" "$(aceptar "$convite_x2")")" 200
esperar "xestora2 na xestión" "$(pedir "$t2" GET "$staff")" 200
id2=$(jq -r '.[] | select(.email == "xestora2-proba@example.org") | .id' "$tmp/corpo")
esperar "detalle de xestora2" "$(pedir "$t_xestora" GET "$staff/$id2")" 200
etag=$(grep -i '^etag:' "$tmp/cab" | tr -d '\r' | sed -E 's/^[Ee][Tt][Aa][Gg]: //')
esperar "xestora2 desactivada" "$(pedir "$t_xestora" POST "$staff/$id2/deactivate" "" "If-Match: $etag")" 200
esperar "xestora2, mesmo token, na seguinte petición" "$(pedir "$t2" GET "$staff")" "403 auth.forbidden"
esperar "volume da base" "$(docker volume ls -q --filter "name=${proxecto}_pgdata-prod" | head -1)" "${proxecto}_pgdata-prod"
root=$(for id in $(dcp ps -q); do docker top "$id" -o pid,uid,args | awk 'NR==2 && $2 == 0'; done)
esperar "procesos principais como root" "${root:-ningún}" ningún

# 5. Copia e restauración con COMPOSE_FILE ---------------------------------------------------
psql() { dcp exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d furar -Atqc "$1"; }
psql "CREATE TABLE public.proba_copia (marca text); INSERT INTO public.proba_copia VALUES ('$version')"
limpo scripts/copia.sh "$tmp/copia" >/dev/null
ok "copia.sh co .env de produción"
psql "DROP TABLE public.proba_copia"
limpo scripts/restaurar.sh "$tmp/copia" --si >/dev/null
esperar "marca restaurada" "$(psql 'SELECT marca FROM public.proba_copia')" "$version"
psql "DROP TABLE public.proba_copia"
esperar "healthz tras restaurar" "$(c "$url/healthz")" ok
echo "D48 OK"
