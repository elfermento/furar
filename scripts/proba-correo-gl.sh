#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# X10 · O correo de verificación chega en galego (AP-05), contra o compose full levantado:
# crea en Keycloak unha persoa co idioma gl, pide o correo de verificación e le en Mailpit
# o asunto, o corpo e a ligazón curta baixo o botón (tema de correo furar). Bórraa ao rematar.
set -euo pipefail
source "$(dirname "$0")/lib/comun.sh"
mailpit=${FURAR_MAILPIT_URL:-http://localhost:8025}

titulo "X10 · Correo de verificación en galego"
a=$(token_admin_keycloak) || falla "sen token de administración de Keycloak"
usuario="correo$(date +%s)"
enderezo="$usuario@example.org"
kc() { curl -sf -H "Authorization: Bearer $a" -H 'Content-Type: application/json' "$@"; }
kc -X POST "$FURAR_KC_ADMIN_URL/admin/realms/furar/users" -d "$(jq -n --arg u "$usuario" --arg e "$enderezo" \
  '{username:$u, email:$e, enabled:true, emailVerified:false, firstName:"Proba", lastName:"Correo", attributes:{locale:["gl"]}}')" >/dev/null \
  || falla "non se puido crear $usuario"
id=$(kc "$FURAR_KC_ADMIN_URL/admin/realms/furar/users?email=$enderezo&exact=true" | jq -r '.[0].id')
trap 'kc -X DELETE "$FURAR_KC_ADMIN_URL/admin/realms/furar/users/$id" >/dev/null 2>&1 || true' EXIT
kc -X PUT "$FURAR_KC_ADMIN_URL/admin/realms/furar/users/$id/send-verify-email" >/dev/null || falla "Keycloak non enviou o correo"

msx=""
for _ in $(seq 20); do
  msx=$(curl -sf "$mailpit/api/v1/search?query=to:$enderezo" | jq -r '.messages[0].ID // empty')
  [[ -n $msx ]] && break
  sleep 1
done
[[ -n $msx ]] || falla "o correo para $enderezo non chegou a Mailpit"
corpo=$(curl -sf "$mailpit/api/v1/message/$msx")
esperar "asunto" "$(jq -r .Subject <<<"$corpo")" "Verifica o teu correo"
grep -q 'Preme o botón para rematar de crear a túa conta' <<<"$(jq -r .Text <<<"$corpo")" || falla "o corpo non está en galego: $(jq -r .Text <<<"$corpo" | head -3)"
ok "corpo en galego"
# Baixo o botón, a ligazón curta (maqueta 55), no HTML e no texto.
curta=$(jq -r .Text <<<"$corpo" | grep -oE 'https?://[^[:space:]]+/auth/realms/furar/l/[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}' | head -1)
[[ -n $curta ]] || falla "o correo non leva a ligazón curta"
grep -qF "Non funciona o botón? Abre esta ligazón:<br><a href=\"$curta\"" <<<"$(jq -r .HTML <<<"$corpo")" || falla "o HTML non leva a ligazón curta baixo o botón"
ok "ligazón curta baixo o botón"
grep -q 'lang="gl"' <<<"$(jq -r .HTML <<<"$corpo")" || falla "o HTML non declara lang=\"gl\""
ok "HTML con lang=\"gl\""
curl -sf -X DELETE "$mailpit/api/v1/messages" -H 'Content-Type: application/json' -d "{\"IDs\":[\"$msx\"]}" >/dev/null || true
echo "X10 OK"
