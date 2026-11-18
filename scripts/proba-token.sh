#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# D33 · Un token real de Keycloak funciona de punta a punta por Caddy (ADR-0007).
# Usa o cliente furar-e2e (só no realm de demo). Rutas en scripts/lib/probas-api.sh.
set -euo pipefail
source "$(dirname "$0")/lib/comun.sh"

titulo "D33 · Token de punta a punta ($FURAR_URL)"

t=$(token xestor-naron) && [[ -n "$t" && "$t" != null ]] || falla "Keycloak non deu token a xestor-naron"
carga=$(jwt_payload "$t")
esperar "iss do token" "$(jq -r .iss <<<"$carga")" "${FURAR_PUBLIC_URL:-$FURAR_URL}/auth/realms/furar"
esperar "aud contén furar-api" "$(jq -r '[.aud]|flatten|index("furar-api")!=null' <<<"$carga")" true
# O rol de persoal sae da táboa de pertenzas (ADR-0027, db/demo/R__demo_staff.sql), non do claim.
esperar "sub fixo de xestor-naron" "$(jq -r .sub <<<"$carga")" 019a0b6e-0000-7000-8000-000000000902
esperar "rol de xestor-naron en naron (táboa)" \
  "$(curl -s -H "Authorization: Bearer $t" "$FURAR_URL/api/v1/municipalities/naron/me/staff" | jq -r .role)" MANAGER

# Escritura no propio concello: crea un campo (STAFF) e lelo require sesión.
codigo="proba_$(date +%s)"
resposta=$(curl -s -D - -o /dev/null -X POST -H "Authorization: Bearer $t" -H 'Content-Type: application/json' \
  -d "$(furar_corpo_campo "$codigo")" "$FURAR_URL$(furar_ruta_campos naron)" | tr -d '\r')
esperar "POST campo en naron co token de xestor-naron" "$(awk 'NR==1{print $2}' <<<"$resposta")" 201
ruta=$(awk -F': ' 'tolower($1)=="location"{print $2}' <<<"$resposta")
ruta=${ruta#"$FURAR_URL"}
[[ $ruta == /api/v1/* ]] || falla "sen Location na resposta do POST"
FURAR_API_GET_PROTEXIDO=$ruta
etag=$(awk -F': ' 'tolower($1)=="etag"{print $2}' <<<"$resposta")
# Ao saír arquívase o campo de proba: o límite de 50 definicións activas non se esgota
# aínda que se repita a proba contra o mesmo compose.
arquivar() {
  [[ -n $etag ]] && curl -s -o /dev/null -X POST -H "Authorization: Bearer $t" -H "If-Match: $etag" \
    "$FURAR_URL$ruta/archive" || true
}
trap arquivar EXIT

esperar "GET $ruta sen token" "$(http_code "$FURAR_URL$ruta")" 401
esperar "GET $ruta co token de xestor-naron" "$(http_code -H "Authorization: Bearer $t" "$FURAR_URL$ruta")" 200

esperar "POST campo en ferrol co token de xestor-naron" \
  "$(http_code -X POST -H "Authorization: Bearer $t" -H 'Content-Type: application/json' \
      -d "$(furar_corpo_campo "$codigo")" "$FURAR_URL$(furar_ruta_campos ferrol)")" 403

# Token doutro emisor: o realm master do mesmo Keycloak (outro iss e outras chaves).
alleo=$(token_admin_keycloak) || falla "sen token do realm master"
esperar "iss alleo" "$(jwt_payload "$alleo" | jq -r .iss)" "$FURAR_KC_ADMIN_URL/realms/master"
esperar "GET protexido con token doutro iss" \
  "$(http_code -H "Authorization: Bearer $alleo" "$FURAR_URL$FURAR_API_GET_PROTEXIDO")" 401

# Token manipulado (sinatura que non corresponde).
manipulado="${t%.*}.$(printf 'x%.0s' {1..43})"
esperar "GET protexido con token manipulado" \
  "$(http_code -H "Authorization: Bearer $manipulado" "$FURAR_URL$FURAR_API_GET_PROTEXIDO")" 401

echo "D33 OK"
