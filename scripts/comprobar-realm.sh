#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Acceso e roles do realm furar (ADR-0007, ADR-0027, ADR-0028, ADR-0029; docs/ens.md):
#   - Keycloak non dá roles: a administración da plataforma e o persoal de cada concello saen da
#     base de datos de Furar. Sen o grupo plataforma, sen roles no cliente furar-api (nin
#     *:PLATFORM_ADMIN nin <concello>:…) e sen o mapeador do claim furar_roles en ningún cliente
#     (ADR-0028).
#   - Segundo factor só para o persoal e a plataforma (ADR-0029): o acceso é o fluxo furar-browser
#     (cookie | provedor | correo e contrasinal e, se a condición furar-second-factor di que si, o
#     segundo factor: a alta se aínda non ten ningún, furar-second-factor-setup, e despois o código
#     da aplicación, auth-otp-form, ou o do correo, furar-email-code, segundo o que teña);
#     «Esqueciches o contrasinal?» é furar-reset-credentials (o mesmo antes do contrasinal novo e
#     nunca o paso de Keycloak que borra ou cambia o código); cada provedor de identidade remata en
#     furar-post-broker (a condición non o repite se xa se deu neste acceso); o
#     rexistro leva furar-second-factor-registration antes de furar-invitation-email. Accións
#     furar-choose-second-factor (a escolla) e CONFIGURE_TOTP activas e
#     CONFIGURE_RECOVERY_AUTHN_CODES non; código TOTP de 6 cifras e 30 s, que non vale dúas veces.
#     Na demo (o realm co cliente furar-e2e), os mesmos fluxos co segundo factor desactivado
#     (DISABLED os subfluxos «… second factor» e o paso do rexistro; ADR-0029, punto 8): en
#     produción é obrigatorio e na demo non. Sen restos dos niveis de antes: nin o rol furar-2fa,
#     nin acr.loa.map, nin os subfluxos «nivel» ou «plataforma», nin o mapeador amr.
#   - Sen as accións WebAuthn (webauthn-register e webauthn-register-passwordless): Furar non as
#     usa.
#   - O cliente furar-web non pode pedir tokens offline: offline_access non está entre os seus
#     ámbitos (nin por defecto nin opcionais; sen lista explícita, colle os do realm, que o traen).
#   - O correo é o usuario: registrationEmailAsUsername e loginWithEmailAllowed activos,
#     sen correos repetidos (duplicateEmailsAllowed) nin cambiar o usuario (editUsernameAllowed).
#   - Bloqueo tras intentos fallidos (op.acc.6.8): 5 fallos → 15 minutos, temporal
#     (permanentLockout false), sen o bloqueo de 60 s por dous fallos seguidos
#     (minimumQuickLoginWaitSeconds 0) e o contador a cero 15 minutos despois do último fallo
#     (maxDeltaTimeSeconds 900: tras o bloqueo volve haber 5 intentos).
#   - Política de contrasinais (op.acc.5, op.acc.6): 10 caracteres ou máis e un número (digits(1)),
#     distinto do usuario e do correo e fóra da lista de contrasinais comúns de Furar
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
politica_minima='["digits(1)","notUsername","notEmail","passwordBlacklist(furar-contrasinais-comuns.txt)"]'
# Fluxos do segundo factor (ADR-0029) en produción: cada paso «autenticador ou subfluxo:requisito»,
# por prioridade. Na demo, os subfluxos «… second factor» van DISABLED (ADR-0029, punto 8).
fluxos_esperados='{
  "furar-browser": ["auth-cookie:ALTERNATIVE", "identity-provider-redirector:ALTERNATIVE", "furar-browser forms:ALTERNATIVE"],
  "furar-browser forms": ["auth-username-password-form:REQUIRED", "furar-browser second factor:CONDITIONAL"],
  "furar-browser second factor": ["furar-second-factor:REQUIRED", "furar-second-factor-setup:REQUIRED", "furar-browser app:CONDITIONAL", "furar-browser email:CONDITIONAL"],
  "furar-browser app": ["conditional-user-configured:REQUIRED", "auth-otp-form:REQUIRED"],
  "furar-browser email": ["conditional-user-configured:REQUIRED", "furar-email-code:REQUIRED"],
  "furar-reset-credentials": ["reset-credentials-choose-user:REQUIRED", "reset-credential-email:REQUIRED", "furar-reset-credentials second factor:CONDITIONAL", "reset-password:REQUIRED"],
  "furar-reset-credentials second factor": ["furar-second-factor:REQUIRED", "furar-second-factor-setup:REQUIRED", "furar-reset-credentials app:CONDITIONAL", "furar-reset-credentials email:CONDITIONAL"],
  "furar-reset-credentials app": ["conditional-user-configured:REQUIRED", "auth-otp-form:REQUIRED"],
  "furar-reset-credentials email": ["conditional-user-configured:REQUIRED", "furar-email-code:REQUIRED"],
  "furar-post-broker": ["furar-post-broker second factor:CONDITIONAL", "allow-access-authenticator:REQUIRED"],
  "furar-post-broker second factor": ["furar-second-factor:REQUIRED", "furar-second-factor-setup:REQUIRED", "furar-post-broker app:CONDITIONAL", "furar-post-broker email:CONDITIONAL"],
  "furar-post-broker app": ["conditional-user-configured:REQUIRED", "auth-otp-form:REQUIRED"],
  "furar-post-broker email": ["conditional-user-configured:REQUIRED", "furar-email-code:REQUIRED"]
}'
otp_esperado='{"type":"totp","algorithm":"HmacSHA1","digits":6,"period":30}'
nota=docs/adr/0028-administracion-da-plataforma-e-primeiro-acceso.md
nota_sf=docs/adr/0029-segundo-factor-do-persoal.md

# stdin: {roles: [nomes dos roles de furar-api], grupos: [nomes dos grupos],
#         furarRoles: ["cliente/mapeador" dos mapeadores que emiten furar_roles], realmRoles: [roles de realm], loa,
#         browserFlow, resetFlow, flows: {alias: ["paso:requisito"…]}, accions: {alias: activa},
#         amr: [mapeadores amr de furar-web], webauthn: [accións WebAuthn activas], webScopes: [ámbitos de furar-web] ou null,
#         idps: [{alias, post}], otp: {type, algorithm, digits, period, reusable}, demo: (cliente furar-e2e),
#         correo: {registrationEmailAsUsername, loginWithEmailAllowed, duplicateEmailsAllowed, editUsernameAllowed},
#         bloqueo: {os campos de $bloqueo}, politica: passwordPolicy,
#         rexistro: {fluxo: registrationFlow, pasos: [pasos activos do formulario, por orde]}}
# stdout: unha liña por fallo.
revisar() {
  jq -r --argjson bloqueo "$bloqueo" --argjson minima "$politica_minima" --argjson fe "$fluxos_esperados" \
    --argjson otp "$otp_esperado" --arg nota "$nota" --arg sf "$nota_sf" '
    (if (.roles | length) > 0 then "furar-api ten roles de cliente (os permisos saen da base de datos de Furar; bórraos, \($nota)): \(.roles | join(" "))" else empty end),
      (if (.grupos | index("plataforma")) != null then "segue o grupo plataforma (a administración da plataforma está na base de datos de Furar; bórrao, \($nota))" else empty end),
      (if (.furarRoles | length) > 0 then "segue o mapeador do claim furar_roles (a API xa non o le; bórrao, \($nota)): \(.furarRoles | join(" "))" else empty end),
      (if (.realmRoles | index("furar-2fa")) != null then "segue o rol furar-2fa dos niveis de antes (bórrao; o segundo factor decídeo Furar, \($sf))" else empty end),
      (if (.loa // "") != "" then "segue acr.loa.map (\(.loa)): sen niveis de acceso, bórrao" else empty end),
      ((.flows | keys | map(select(test("^furar-browser (formulario|nivel|plataforma)")))) as $f
       | if ($f | length) > 0 then "seguen os subfluxos dos niveis de antes: \($f | join(", "))" else empty end),
      (if .browserFlow != "furar-browser" then "o fluxo de acceso é «\(.browserFlow)», non furar-browser (\($sf))" else empty end),
      (if .resetFlow != "furar-reset-credentials" then "«Esqueciches o contrasinal?» é «\(.resetFlow)», non furar-reset-credentials: o de Keycloak cambia o código só co correo (\($sf))" else empty end),
      (. as $r | ($fe | if $r.demo then map_values(map(sub(" second factor:CONDITIONAL$"; " second factor:DISABLED"))) else . end)
       | to_entries[] | . as $e
       | ($r.flows[$e.key] // null) as $got
       | if $got == null then "falta o fluxo «\($e.key)» (\($sf))"
         elif $got != $e.value then "o fluxo «\($e.key)» é \($got | tojson), esperado \($e.value | tojson)\(if $r.demo then " (demo: segundo factor desactivado)" else "" end) (\($sf))"
         else empty end),
      (if .accions["furar-choose-second-factor"] != true then "a acción furar-choose-second-factor non está activa: o persoal non pode escoller nin dar de alta o segundo factor (\($sf))" else empty end),
      (if .accions.CONFIGURE_TOTP != true then "a acción CONFIGURE_TOTP non está activa: o persoal non pode dar de alta a aplicación (\($sf))" else empty end),
      (if .accions.CONFIGURE_RECOVERY_AUTHN_CODES == true then "a acción CONFIGURE_RECOVERY_AUTHN_CODES está activa (Furar non usa códigos de recuperación, ADR-0029)" else empty end),
      (if (.otp | {type, algorithm, digits, period}) != $otp then "código do segundo factor distinto de TOTP, 6 cifras, 30 s (\(.otp | tojson))" else empty end),
      (if .otp.reusable then "un código do segundo factor vale dúas veces (otpPolicyCodeReusable)" else empty end),
      ([.idps[] | select(.post != "furar-post-broker") | .alias] as $i
       | if ($i | length) > 0 then "provedores de identidade sen o fluxo furar-post-broker despois de entrar (o persoal entraría sen o código): \($i | join(" "))" else empty end),
      (if (.amr | length) > 0 then "furar-web segue co mapeador amr: \(.amr | join(" "))" else empty end),
      (if (.webauthn | length) > 0 then "accións WebAuthn activas (Furar non as usa): \(.webauthn | join(" "))" else empty end),
      (if .webScopes == null then "furar-web sen lista explícita de ámbitos (colle offline_access do realm)"
       elif (.webScopes | index("offline_access")) != null then "furar-web pode pedir offline_access" else empty end),
      (if .correo != {registrationEmailAsUsername: true, loginWithEmailAllowed: true, duplicateEmailsAllowed: false, editUsernameAllowed: false}
       then "o correo non é o usuario (registrationEmailAsUsername e loginWithEmailAllowed true, duplicateEmailsAllowed e editUsernameAllowed false): \(.correo | tojson)" else empty end),
      (if .rexistro.fluxo != "furar-registration" or (.rexistro.pasos | index("furar-invitation-email")) == null
       then "o rexistro non é o fluxo furar-registration co paso furar-invitation-email (ADR-0027): \(.rexistro | tojson)" else empty end),
      ((.rexistro.pasos | index("furar-second-factor-registration")) as $s | (.rexistro.pasos | index("furar-invitation-email")) as $i
       | if .demo then
           (if $s != null then "demo: o rexistro leva activo furar-second-factor-registration (na demo o segundo factor vai desactivado, ADR-0029)" else empty end)
         elif $s == null or ($i != null and $s > $i)
         then "o rexistro non leva furar-second-factor-registration antes de furar-invitation-email (ADR-0029): \(.rexistro.pasos | tojson)" else empty end),
      (if .bloqueo != $bloqueo then "bloqueo tras intentos fallidos distinto de 5 fallos → 15 minutos (op.acc.6.8): \(.bloqueo | tojson), esperado \($bloqueo | tojson)" else empty end),
      ((.politica // "" | split(" and ") | map(gsub("^ +| +$"; "")) | map(select(. != ""))) as $p
       | (if ($minima - $p | length) > 0 then "a política de contrasinais non ten \($minima - $p | join(" ")) (\(.politica))" else empty end),
         (if ([$p[] | capture("^length\\((?<n>[0-9]+)\\)$").n | tonumber] | max // 0) < 10
          then "a política de contrasinais non pide 10 caracteres ou máis (\(.politica))" else empty end))
  '
}

informe() { # nome JSON
  local mal
  mal=$(revisar <<<"$2") || { printf '✗ %s: non se puido revisar (jq)\n' "$1"; return 1; }
  if [[ -n $mal ]]; then
    while IFS= read -r l; do printf '✗ %s: %s\n' "$1" "$l"; done <<<"$mal"
    return 1
  fi
  local sf=obrigatorio
  [[ $(jq -r .demo <<<"$2") == true ]] && sf="desactivado: demo"
  printf '✓ %s: sen roles en Keycloak (nin grupo plataforma, nin roles de furar-api, nin furar_roles), segundo factor do persoal (%s; furar-browser, furar-reset-credentials, furar-post-broker e a alta no rexistro; aplicación ou código por correo; TOTP 6/30 s; sen niveis nin códigos de recuperación), sen WebAuthn nin offline_access na web, o correo como usuario, bloqueo 5 → 15 min, contrasinal de 10 cun número e lista de comúns e o rexistro coa invitación\n' "$1" "$sf"
}

# Pasos dun fluxo exportado ou da API de administración: «autenticador ou subfluxo:requisito».
pasos_jq='def pasos: [.authenticationExecutions // [] | sort_by(.priority)[]
  | "\(if .authenticatorFlow then .flowAlias else .authenticator end):\(.requirement)"];'

if [[ ${1:-} != --vivo ]]; then
  (($#)) || { echo "uso: $0 FICHEIRO.json… | --vivo" >&2; exit 2; }
  fallo=0
  for f in "$@"; do
    datos=$(jq -c --argjson b "$bloqueo" "$pasos_jq"'{
      roles: [.roles.client["furar-api"][]?.name],
      grupos: [.groups[]?.name],
      furarRoles: [.clients[]? | .clientId as $c | .protocolMappers[]?
        | select(.config["claim.name"] == "furar_roles") | "\($c)/\(.name)"],
      realmRoles: [.roles.realm[]?.name],
      loa: (.attributes["acr.loa.map"] // ""),
      browserFlow: (.browserFlow // "browser"),
      resetFlow: (.resetCredentialsFlow // "reset credentials"),
      flows: ([.authenticationFlows[]? | {(.alias): pasos}] | add // {}),
      accions: ([.requiredActions[]? | {(.alias): (.enabled == true)}] | add // {}),
      otp: {type: (.otpPolicyType // "totp"), algorithm: (.otpPolicyAlgorithm // "HmacSHA1"), digits: (.otpPolicyDigits // 6),
        period: (.otpPolicyPeriod // 30), reusable: (.otpPolicyCodeReusable == true)},
      demo: ([.clients[]? | select(.clientId == "furar-e2e")] | length > 0),
      idps: [.identityProviders[]? | {alias, post: (.postBrokerLoginFlowAlias // "")}],
      amr: [.clients[]? | select(.clientId == "furar-web") | .protocolMappers[]? | select(.protocolMapper == "oidc-amr-mapper") | .name],
      webauthn: [.requiredActions[]? | select(.enabled and (.alias | startswith("webauthn-register"))) | .alias],
      webScopes: ([.clients[]? | select(.clientId == "furar-web")][0]
        | if .defaultClientScopes == null or .optionalClientScopes == null then null
          else .defaultClientScopes + .optionalClientScopes end),
      correo: {registrationEmailAsUsername: (.registrationEmailAsUsername == true), loginWithEmailAllowed: (.loginWithEmailAllowed == true),
        duplicateEmailsAllowed: (.duplicateEmailsAllowed == true), editUsernameAllowed: (.editUsernameAllowed == true)},
      bloqueo: (. as $r | $b | keys_unsorted | map({(.): $r[.]}) | add), politica: .passwordPolicy,
      rexistro: {fluxo: (.registrationFlow // "registration"), pasos: [.authenticationFlows[]? | select(.alias == "furar-registration-form")
        | .authenticationExecutions | sort_by(.priority)[] | select(.requirement != "DISABLED") | .authenticator]}}' "$f")
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
clientes=$(kc '/clients?max=1000')
furar_roles=$(jq -c '[.[] | .clientId as $c | .protocolMappers[]?
  | select(.config["claim.name"] == "furar_roles") | "\($c)/\(.name)"]' <<<"$clientes")
demo=$(jq -c '[.[] | select(.clientId == "furar-e2e")] | length > 0' <<<"$clientes")
realm_roles=$(kc '/roles?briefRepresentation=true&max=1000' | jq -c 'map(.name)')
# A lista de fluxos só trae os de primeiro nivel: os subfluxos lense polo seu alias.
fluxos=$(kc '/authentication/flows' | jq -c "$pasos_jq"'[.[] | {(.alias): pasos}] | add // {}')
# Subfluxos: GET /authentication/flows/{alias}/executions dá a árbore con level e index.
sub=$(for top in furar-browser furar-reset-credentials furar-post-broker; do
  kc "/authentication/flows/$top/executions" 2>/dev/null || echo '[]'
done | jq -sc 'add')
fluxos=$(jq -c --argjson sub "$sub" '
  . as $top
  # Para cada subfluxo (authenticationFlow true), os seus fillos directos (level + 1, ata o seguinte do mesmo nivel).
  | reduce range(0; $sub | length) as $i ($top;
      ($sub[$i]) as $e
      | if ($e.authenticationFlow // false) then
          . + {($e.displayName): [
            ($sub[$i + 1:] | (map(.level <= $e.level) | index(true)) as $fin
             | (if $fin == null then $sub[$i + 1:] else $sub[$i + 1:$i + 1 + $fin] end))[]
            | select(.level == $e.level + 1)
            | "\(if (.authenticationFlow // false) then .displayName else .providerId end):\(.requirement)"]}
        else . end)' <<<"$fluxos")
accions=$(kc /authentication/required-actions)
wid=$(jq -r '[.[] | select(.clientId == "furar-web")][0].id' <<<"$clientes")
[[ -n $wid && $wid != null ]] || { echo "non existe o cliente furar-web" >&2; exit 2; }
ambitos=$( { kc "/clients/$wid/default-client-scopes"; kc "/clients/$wid/optional-client-scopes"; } | jq -sc 'add | map(.name)')
amr=$(kc "/clients/$wid/protocol-mappers/models" | jq -c '[.[] | select(.protocolMapper == "oidc-amr-mapper") | .name]')
idps=$(kc '/identity-provider/instances' | jq -c '[.[] | {alias, post: (.postBrokerLoginFlowAlias // "")}]')
realm=$(kc '')
pasos=$(kc '/authentication/flows/furar-registration/executions' 2>/dev/null | jq -c '[.[] | select(.requirement != "DISABLED") | .providerId // empty]' || echo '[]')
datos=$(jq -nc --argjson roles "$roles" --argjson gr "$grupos" --argjson fr "$furar_roles" --argjson rr "$realm_roles" --argjson fl "$fluxos" --argjson ac "$accions" \
  --argjson ambitos "$ambitos" --argjson amr "$amr" --argjson r "$realm" --argjson b "$bloqueo" --argjson idps "$idps" --argjson demo "$demo" \
  --argjson pasos "$pasos" \
  '{roles: $roles, grupos: $gr, furarRoles: $fr, realmRoles: $rr, loa: ($r.attributes["acr.loa.map"] // ""), browserFlow: $r.browserFlow,
    resetFlow: $r.resetCredentialsFlow, flows: $fl,
    accions: ([$ac[] | {(.alias): (.enabled == true)}] | add // {}),
    otp: {type: $r.otpPolicyType, algorithm: $r.otpPolicyAlgorithm, digits: $r.otpPolicyDigits, period: $r.otpPolicyPeriod,
      reusable: ($r.otpPolicyCodeReusable == true)},
    demo: $demo, idps: $idps, amr: $amr,
    webauthn: [$ac[] | select(.enabled and (.alias | startswith("webauthn-register"))) | .alias], webScopes: $ambitos,
    correo: {registrationEmailAsUsername: ($r.registrationEmailAsUsername == true), loginWithEmailAllowed: ($r.loginWithEmailAllowed == true),
      duplicateEmailsAllowed: ($r.duplicateEmailsAllowed == true), editUsernameAllowed: ($r.editUsernameAllowed == true)},
    bloqueo: ($b | keys_unsorted | map({(.): $r[.]}) | add), politica: $r.passwordPolicy,
    rexistro: {fluxo: $r.registrationFlow, pasos: $pasos}}')
informe "Keycloak en $url" "$datos"
