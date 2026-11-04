#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Acceso e roles do realm furar (SEC2-01, ADR-0007, ADR-0027, ADR-0028; docs/ens.md):
#   - Keycloak non dá roles: a administración da plataforma e o persoal de cada concello saen da
#     base de datos de Furar. Sen o grupo plataforma, sen roles no cliente furar-api (nin
#     *:PLATFORM_ADMIN nin <concello>:…) e sen o mapeador do claim furar_roles en ningún cliente
#     (docs/notas-realm/2026-11-03-administracion-na-base-e-nome.md).
#   - Sen segundo factor (decisión do mantedor, 2026-11-02): nin o rol de realm furar-2fa, nin
#     acr.loa.map, nin fluxos furar-browser; o acceso é o fluxo browser de Keycloak; as accións
#     CONFIGURE_TOTP e CONFIGURE_RECOVERY_AUTHN_CODES desactivadas, e sen o mapeador amr.
#   - Sen as accións WebAuthn (webauthn-register e webauthn-register-passwordless): Furar non as
#     usa.
#   - O cliente furar-web non pode pedir tokens offline: offline_access non está entre os seus
#     ámbitos (nin por defecto nin opcionais; sen lista explícita, colle os do realm, que o traen).
#   - O correo é o usuario (P13): registrationEmailAsUsername e loginWithEmailAllowed activos,
#     sen correos repetidos (duplicateEmailsAllowed) nin cambiar o usuario (editUsernameAllowed).
#   - Bloqueo tras intentos fallidos (op.acc.6.8): 5 fallos → 15 minutos, temporal
#     (permanentLockout false), sen o bloqueo de 60 s por dous fallos seguidos
#     (minimumQuickLoginWaitSeconds 0) e o contador a cero 15 minutos despois do último fallo
#     (maxDeltaTimeSeconds 900: tras o bloqueo volve haber 5 intentos).
#   - Política de contrasinais (op.acc.5, op.acc.6): 12 caracteres ou máis, distinto do usuario e
#     do correo e fóra da lista de contrasinais comúns de Furar
#     (passwordBlacklist(furar-contrasinais-comuns.txt), deploy/keycloak/password-blacklists).
#   - Rexistro (ADR-0027, revisión do 2026-11-02): o fluxo furar-registration, co contrasinal no
#     formulario e o paso furar-invitation-email (a invitación do persoal verifica o correo).
#
#   scripts/comprobar-realm.sh FICHEIRO.json…   realm exportado (X02: produción e demo)
#   scripts/comprobar-realm.sh --vivo           Keycloak en marcha, pola API de administración
#       KC_ADMIN_URL   (por defecto http://127.0.0.1:8082/auth, o túnel de docs/instalacion.md)
#       KC_ADMIN_TOKEN token de admin-cli do realm master; se falta, pide usuario e contrasinal
# Código 0 se todo cumpre; 1 e a lista do que non.
set -euo pipefail

bloqueo='{"bruteForceProtected":true,"permanentLockout":false,"failureFactor":5,"bruteForceStrategy":"MULTIPLE","waitIncrementSeconds":900,"maxFailureWaitSeconds":900,"minimumQuickLoginWaitSeconds":0,"quickLoginCheckMilliSeconds":1000,"maxDeltaTimeSeconds":900}'
politica_minima='["notUsername","notEmail","passwordBlacklist(furar-contrasinais-comuns.txt)"]'
segundo_factor='["CONFIGURE_TOTP","CONFIGURE_RECOVERY_AUTHN_CODES"]'
nota=docs/notas-realm/2026-11-03-administracion-na-base-e-nome.md

# stdin: {roles: [nomes dos roles de furar-api], grupos: [nomes dos grupos],
#         furarRoles: ["cliente/mapeador" dos mapeadores que emiten furar_roles], realmRoles: [roles de realm], loa, browserFlow,
#         flows: [alias], accions: [accións de segundo factor activas], amr: [mapeadores amr de furar-web],
#         webauthn: [accións WebAuthn activas], webScopes: [ámbitos de furar-web] ou null,
#         correo: {registrationEmailAsUsername, loginWithEmailAllowed, duplicateEmailsAllowed, editUsernameAllowed},
#         bloqueo: {os campos de $bloqueo}, politica: passwordPolicy,
#         rexistro: {fluxo: registrationFlow, pasos: [pasos activos do formulario]}}
# stdout: unha liña por fallo.
revisar() {
  jq -r --argjson bloqueo "$bloqueo" --argjson minima "$politica_minima" --arg nota "$nota" '
    (if (.roles | length) > 0 then "furar-api ten roles de cliente (os permisos saen da base de datos de Furar; bórraos, \($nota)): \(.roles | join(" "))" else empty end),
      (if (.grupos | index("plataforma")) != null then "segue o grupo plataforma (a administración da plataforma está na base de datos de Furar; bórrao, \($nota))" else empty end),
      (if (.furarRoles | length) > 0 then "segue o mapeador do claim furar_roles (a API xa non o le; bórrao, \($nota)): \(.furarRoles | join(" "))" else empty end),
      (if (.realmRoles | index("furar-2fa")) != null then "segue o rol furar-2fa (sen segundo factor: bórrao)" else empty end),
      (if (.loa // "") != "" then "segue acr.loa.map (\(.loa)): sen niveis de acceso, bórrao" else empty end),
      (if .browserFlow != "browser" then "o fluxo de acceso é «\(.browserFlow)», non o browser de Keycloak" else empty end),
      ((.flows | map(select(startswith("furar-browser")))) as $f
       | if ($f | length) > 0 then "seguen os fluxos do segundo factor: \($f | join(", "))" else empty end),
      (if (.accions | length) > 0 then "accións do segundo factor activas: \(.accions | join(" "))" else empty end),
      (if (.amr | length) > 0 then "furar-web segue co mapeador amr: \(.amr | join(" "))" else empty end),
      (if (.webauthn | length) > 0 then "accións WebAuthn activas (Furar non as usa): \(.webauthn | join(" "))" else empty end),
      (if .webScopes == null then "furar-web sen lista explícita de ámbitos (colle offline_access do realm)"
       elif (.webScopes | index("offline_access")) != null then "furar-web pode pedir offline_access" else empty end),
      (if .correo != {registrationEmailAsUsername: true, loginWithEmailAllowed: true, duplicateEmailsAllowed: false, editUsernameAllowed: false}
       then "o correo non é o usuario (P13: registrationEmailAsUsername e loginWithEmailAllowed true, duplicateEmailsAllowed e editUsernameAllowed false): \(.correo | tojson)" else empty end),
      (if .rexistro.fluxo != "furar-registration" or (.rexistro.pasos | index("furar-invitation-email")) == null
       then "o rexistro non é o fluxo furar-registration co paso furar-invitation-email (ADR-0027): \(.rexistro | tojson)" else empty end),
      (if .bloqueo != $bloqueo then "bloqueo tras intentos fallidos distinto de 5 fallos → 15 minutos (op.acc.6.8): \(.bloqueo | tojson), esperado \($bloqueo | tojson)" else empty end),
      ((.politica // "" | split(" and ") | map(gsub("^ +| +$"; "")) | map(select(. != ""))) as $p
       | (if ($minima - $p | length) > 0 then "a política de contrasinais non ten \($minima - $p | join(" ")) (\(.politica))" else empty end),
         (if ([$p[] | capture("^length\\((?<n>[0-9]+)\\)$").n | tonumber] | max // 0) < 12
          then "a política de contrasinais non pide 12 caracteres ou máis (\(.politica))" else empty end))
  '
}

informe() { # nome JSON
  local mal
  mal=$(revisar <<<"$2")
  if [[ -n $mal ]]; then
    while IFS= read -r l; do printf '✗ %s: %s\n' "$1" "$l"; done <<<"$mal"
    return 1
  fi
  printf '✓ %s: sen roles en Keycloak (nin grupo plataforma, nin roles de furar-api, nin furar_roles), sen segundo factor (nin furar-2fa, nin acr.loa.map, nin furar-browser, nin OTP nin códigos), sen WebAuthn nin offline_access na web, o correo como usuario, bloqueo 5 → 15 min, contrasinal de 12 con lista de comúns e o rexistro coa invitación\n' "$1"
}

if [[ ${1:-} != --vivo ]]; then
  (($#)) || { echo "uso: $0 FICHEIRO.json… | --vivo" >&2; exit 2; }
  fallo=0
  for f in "$@"; do
    datos=$(jq -c --argjson b "$bloqueo" --argjson sf "$segundo_factor" '{
      roles: [.roles.client["furar-api"][]?.name],
      grupos: [.groups[]?.name],
      furarRoles: [.clients[]? | .clientId as $c | .protocolMappers[]?
        | select(.config["claim.name"] == "furar_roles") | "\($c)/\(.name)"],
      realmRoles: [.roles.realm[]?.name],
      loa: (.attributes["acr.loa.map"] // ""),
      browserFlow: (.browserFlow // "browser"),
      flows: [.authenticationFlows[]?.alias],
      accions: [.requiredActions[]? | select(.enabled and (.alias as $a | $sf | index($a) != null)) | .alias],
      amr: [.clients[]? | select(.clientId == "furar-web") | .protocolMappers[]? | select(.protocolMapper == "oidc-amr-mapper") | .name],
      webauthn: [.requiredActions[]? | select(.enabled and (.alias | startswith("webauthn-register"))) | .alias],
      webScopes: ([.clients[]? | select(.clientId == "furar-web")][0]
        | if .defaultClientScopes == null or .optionalClientScopes == null then null
          else .defaultClientScopes + .optionalClientScopes end),
      correo: {registrationEmailAsUsername: (.registrationEmailAsUsername == true), loginWithEmailAllowed: (.loginWithEmailAllowed == true),
        duplicateEmailsAllowed: (.duplicateEmailsAllowed == true), editUsernameAllowed: (.editUsernameAllowed == true)},
      bloqueo: (. as $r | $b | keys_unsorted | map({(.): $r[.]}) | add), politica: .passwordPolicy,
      rexistro: {fluxo: (.registrationFlow // "registration"), pasos: [.authenticationFlows[]? | select(.alias == "furar-registration-form")
        | .authenticationExecutions[] | select(.requirement != "DISABLED") | .authenticator]}}' "$f")
    informe "$f" "$datos" || fallo=1
  done
  exit $fallo
fi

url=${KC_ADMIN_URL:-http://127.0.0.1:8082/auth}
token=${KC_ADMIN_TOKEN:-}
if [[ -z $token ]]; then
  read -r -p "Usuario administrador (realm master): " u
  read -r -s -p "Contrasinal: " p; echo
  token=$(curl -sf -d grant_type=password -d client_id=admin-cli --data-urlencode "username=$u" \
    --data-urlencode "password=$p" "$url/realms/master/protocol/openid-connect/token" | jq -r .access_token)
fi
[[ -n $token && $token != null ]] || { echo "sen token de administración en $url" >&2; exit 2; }
kc() { curl -sf -H "Authorization: Bearer $token" "$url/admin/realms/furar$1"; }
cid=$(kc '/clients?clientId=furar-api' | jq -r '.[0].id')
[[ -n $cid && $cid != null ]] || { echo "non existe o cliente furar-api" >&2; exit 2; }
roles=$(kc "/clients/$cid/roles?briefRepresentation=true&max=1000" | jq -c 'map(.name)')
grupos=$(kc '/groups?briefRepresentation=true&max=1000' | jq -c 'map(.name)')
furar_roles=$(kc '/clients?max=1000' | jq -c '[.[] | .clientId as $c | .protocolMappers[]?
  | select(.config["claim.name"] == "furar_roles") | "\($c)/\(.name)"]')
realm_roles=$(kc '/roles?briefRepresentation=true&max=1000' | jq -c 'map(.name)')
fluxos=$(kc '/authentication/flows' | jq -c 'map(.alias)')
accions=$(kc /authentication/required-actions)
wid=$(kc '/clients?clientId=furar-web' | jq -r '.[0].id')
[[ -n $wid && $wid != null ]] || { echo "non existe o cliente furar-web" >&2; exit 2; }
ambitos=$( { kc "/clients/$wid/default-client-scopes"; kc "/clients/$wid/optional-client-scopes"; } | jq -sc 'add | map(.name)')
amr=$(kc "/clients/$wid/protocol-mappers/models" | jq -c '[.[] | select(.protocolMapper == "oidc-amr-mapper") | .name]')
realm=$(kc '')
pasos=$(kc '/authentication/flows/furar-registration/executions' 2>/dev/null | jq -c '[.[] | select(.requirement != "DISABLED") | .providerId // empty]' || echo '[]')
datos=$(jq -nc --argjson roles "$roles" --argjson gr "$grupos" --argjson fr "$furar_roles" --argjson rr "$realm_roles" --argjson fl "$fluxos" --argjson ac "$accions" \
  --argjson ambitos "$ambitos" --argjson amr "$amr" --argjson r "$realm" --argjson b "$bloqueo" --argjson sf "$segundo_factor" \
  --argjson pasos "$pasos" \
  '{roles: $roles, grupos: $gr, furarRoles: $fr, realmRoles: $rr, loa: ($r.attributes["acr.loa.map"] // ""), browserFlow: $r.browserFlow, flows: $fl,
    accions: [$ac[] | select(.enabled and (.alias as $a | $sf | index($a) != null)) | .alias], amr: $amr,
    webauthn: [$ac[] | select(.enabled and (.alias | startswith("webauthn-register"))) | .alias], webScopes: $ambitos,
    correo: {registrationEmailAsUsername: ($r.registrationEmailAsUsername == true), loginWithEmailAllowed: ($r.loginWithEmailAllowed == true),
      duplicateEmailsAllowed: ($r.duplicateEmailsAllowed == true), editUsernameAllowed: ($r.editUsernameAllowed == true)},
    bloqueo: ($b | keys_unsorted | map({(.): $r[.]}) | add), politica: $r.passwordPolicy,
    rexistro: {fluxo: $r.registrationFlow, pasos: $pasos}}')
informe "Keycloak en $url" "$datos"
