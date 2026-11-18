#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Proba o acceso de Keycloak cun navegador real, sen o compose: arrinca un Keycloak de usar e
# tirar (a imaxe base fixada no Dockerfile, en modo dev, ~1 GB) co tema furar e o realm de demo.
#   rexistro  D51 · RGPD no rexistro (ADR-0017): a información básica nunha frase curta
#             e xenérica en gl e es («Quen presta o servizo trata os teus datos…» e «Privacidade e os teus
#             dereitos», a páxina do concello que trae furar_privacy, na orixe do redirect_uri;
#             revisión do 2026-11-03: sen datos do despregue), sen casiña de «acepto os termos»,
#             sen a frase se non hai concello (sen furar_privacy, ou con un camiño que non vale), e
#             o rexistro funciona
#             co correo como usuario (sen campo de usuario) e co correo xa posto desde
#             login_hint (prompt=create, a ligazón da invitación). Ademais: sen erros de
#             JavaScript nin scroll horizontal a 320 px; ao poñer o contrasinal, a axuda nunha
#             liña («Polo menos 10 caracteres e un número.»), sen lista en vivo nin «Pechar a sesión noutros
#             dispositivos», e os erros do servidor ao enviar (curto, sen número, común, igual ao correo);
#             tras un acceso fallido, o aviso xenérico do bloqueo («5 intentos, 15 minutos»),
#             igual exista ou non a conta. O contrasinal vai no formulario de rexistro
#             e a invitación do persoal verifica o correo (ADR-0027, extensión de Furar): coa
#             proba válida, o correo fixo, sen a frase de privacidade (vai no correo da
#             invitación) e a conta nace verificada, sen correo; sen proba, cunha
#             firma doutro segredo, caducada, xa usada ou cun correo distinto, Keycloak pide
#             verificalo. O tema: claro, e escuro só se a web gardou o esquema escuro (non o do
#             sistema), axe sen violacións. Desde o 2026-11-03 (ADR-0028): a marca «Reservas
#             deportivas» (nome do realm e do remitente), ningún «Furar» nas pantallas, sen o grupo
#             plataforma nin roles en Keycloak; con furar_setup=1 (sen concellos) o rexistro non
#             mostra a frase de privacidade; os realms sen textos furarPrivacy*; «Verificación do correo» cun só texto, sen o aviso de
#             Keycloak, e o botón «Reenviar o correo» (gl e es), que reenvía de verdade (o correo
#             chega a un Mailpit de usar e tirar; reenviar xa, antes da espera, dá o aviso da espera);
#             no correo, baixo o botón, a ligazón curta, que redirixe ao URL do botón,
#             e un código descoñecido dá «A ligazón caducou» cos botóns de entrar e de cambiar o
#             contrasinal. Tamén o segundo factor (abaixo).
#   clave     D52 · Autenticación pública (ADR-0019): entrar por «Cl@ve simulada», IdP
#             SAML 2.0 con sinatura validada; Furar non garda o NIF que envía o IdP. Coa API
#             simulada, a veciñanza entra sen código (furar-post-broker, ADR-0029); coa API
#             parada, ve o erro e non entra.
#   segundo-factor  (só esta parte de D51) Verificación en dous pasos do persoal (ADR-0029), cunha
#             API simulada (a resposta de /actuator/secondfactor por correo e coa clave derivada),
#             cos fluxos de PRODUCIÓN (a demo non leva segundo factor): o persoal coa
#             aplicación xa configurada dá o código (un código malo, o erro en galego); a
#             veciñanza entra sen el; o persoal sen ningún método escolle (a aplicación primeiro e
#             recomendada, ou o código por correo): coa aplicación, a alta (sen nomear ningunha
#             aplicación) e no seguinte acceso o código; co correo, o código chega a Mailpit, un
#             malo dá o erro, pedir outro xa dá a espera, o bo entra e no seguinte acceso pídeo
#             outra vez polo correo; a invitación válida para ese correo pídeo aínda que a API diga
#             que non; o rexistro desde a invitación pide a escolla e a alta antes de volver á web;
#             «Esqueciches o contrasinal?» pide o método de cada quen (aplicación ou correo) antes
#             do contrasinal novo; coa API parada, a veciñanza ve o erro (non entra só co
#             contrasinal) e o persoal dá o código; axe sen violacións nas pantallas.
#   (sen argumento) todo.
# O Keycloak de proba importa o realm de demo (usuarios, Cl@ve simulada) cos fluxos de
# autenticación do de produción, onde o segundo factor é obrigatorio, e cunha aplicación de
# autenticación xa configurada en xestor-naron e xestor-ferrol cun segredo ao chou desta proba.
# Precisa Docker, jq, Node 24 e web/node_modules (npm ci); instala Chromium de Playwright se falta.
# O correo vai a un Mailpit (a imaxe de compose.yaml) na rede do Keycloak de proba. A API
# simulada é un proceso de Node no equipo; Keycloak chega a ela por host.docker.internal.
# Compila a extensión (deploy/keycloak/extension) co wrapper de Maven da API. Nun equipo lento,
# FURAR_PROBA_KC_ESPERA (segundos ata que Keycloak responde; 180 por defecto).
set -euo pipefail
source "$(dirname "$0")/lib/comun.sh"
cd "$FURAR_RAIZ"
modo=${1:-todo}
[[ $modo =~ ^(rexistro|clave|segundo-factor|todo)$ ]] || falla "uso: $0 [rexistro|clave|segundo-factor]"

prod=deploy/keycloak/import/furar-realm.json
demo=deploy/keycloak/import-demo/furar-realm.json
porto=${FURAR_PROBA_KC_PORTO:-18082}
url="http://localhost:$porto"
porto_correo=${FURAR_PROBA_CORREO_PORTO:-18025}
correo_url="http://localhost:$porto_correo"
marca="Reservas deportivas"
# A web da proba (o redirect_uri de furar-web na demo) e a páxina «Privacidade» dun concello.
web_proba="http://localhost:4200"
camino_privacidade="/proba/privacidade"
nif=00000000T
[[ -d web/node_modules/playwright ]] || falla "falta web/node_modules: (cd web && npm ci)"

# --- Comprobacións estáticas -------------------------------------------------------------
titulo "Configuración versionada"
for r in "$prod" "$demo"; do
  jq -e '[.requiredActions[]? | select(.alias == "TERMS_AND_CONDITIONS" and .enabled != false)] | length == 0' "$r" >/dev/null \
    || falla "$r activa TERMS_AND_CONDITIONS («acepto os termos»)"
  # Revisión do 2026-11-03: os datos de privacidade son de cada concello, non do despregue.
  jq -e '[.localizationTexts // {} | .[] | keys[] | select(startswith("furarPrivacy"))] | length == 0' "$r" >/dev/null \
    || falla "$r: aínda leva textos furarPrivacy* (FURAR_PRIVACY_*)"
done
ok "realms sen TERMS_AND_CONDITIONS e sen textos furarPrivacy* do despregue"
for r in "$prod" "$demo"; do
  esperar "$r: nome do realm e do remitente" "$(jq -r '"\(.displayName)|\(.smtpServer.fromDisplayName)"' "$r")" "$marca|$marca"
  esperar "$r: sen grupo plataforma, sen roles de furar-api nin mapeador furar_roles (ADR-0028)" \
    "$(jq -r '"\([.groups[]?.name] | length) \([.roles.client["furar-api"][]?] | length) \([.clients[]?.protocolMappers[]? | select(.config["claim.name"] == "furar_roles")] | length)"' "$r")" "0 0 0"
done
for r in "$prod" "$demo"; do
  # O paso do segundo factor no rexistro: obrigatorio en produción, desactivado na demo (ADR-0029, punto 8).
  sf_rexistro=" furar-second-factor-registration"
  [[ $r == "$demo" ]] && sf_rexistro=""
  esperar "$r: rexistro coa invitación do persoal e o contrasinal no formulario (ADR-0027)" \
    "$(jq -r '.registrationFlow as $f | ([.authenticationFlows[] | select(.alias == "furar-registration-form") | .authenticationExecutions[]
      | select(.requirement == "REQUIRED") | .authenticator] | join(" ")) as $e
      | ([.authenticatorConfig[] | select(.alias == "furar-password-on-form") | .config.always_set_password_on_register_form][0]) as $c
      | "\($f) \($e) \($c)"' "$r")" \
    "furar-registration registration-user-creation registration-password-action$sf_rexistro furar-invitation-email true"
done
scripts/comprobar-realm.sh "$prod" "$demo" >/dev/null || { scripts/comprobar-realm.sh "$prod" "$demo" || true; falla "comprobar-realm.sh (política de contrasinais e bloqueo)"; }
ok "política de contrasinais (10 e un número, lista de comúns) e bloqueo (5 intentos, 15 min) nos dous realms (comprobar-realm.sh)"
for r in "$prod" "$demo"; do
  esperar "$r: o correo é o usuario" "$(jq -r '.registrationEmailAsUsername' "$r")" true
done
if grep -q termsAccepted deploy/keycloak/themes/furar/login/register-commons.ftl; then
  falla "register-commons.ftl aínda pinta a casiña termsAccepted"
fi
if grep -q FURAR_PRIVACY_ compose.yaml compose.prod.yaml .env.example; then
  falla "compose ou .env.example aínda levan FURAR_PRIVACY_* (revisión do 2026-11-03)"
fi
ok "sen FURAR_PRIVACY_* na instalación (os datos son de cada concello)"
esperar "IdP en produción (o simulado só vai na demo)" "$(jq '.identityProviders // [] | length' "$prod")" 0
esperar "IdP clave-simulada valida a sinatura" \
  "$(jq -r '.identityProviders[] | select(.alias == "clave-simulada") | .config | "\(.validateSignature) \(.wantAssertionsSigned) \(.useMetadataDescriptorUrl)"' "$demo")" "true true true"
esperar "ningún mapeador do IdP importa o NIF" \
  "$(jq '[.identityProviderMappers[] | select(.identityProviderAlias == "clave-simulada") | .config["attribute.name"] // "" | select(test("nif"; "i"))] | length' "$demo")" 0
# Segundo factor (ADR-0029, punto 8): na demo ninguén o usa (sen códigos configurados nin o segundo
# factor nos fluxos); en produción é obrigatorio para o persoal e a plataforma (comprobar-realm.sh).
esperar "demo: contas con código do segundo factor (ningunha)" \
  "$(jq -r '[.users[] | select([.credentials[]?.type] | index("otp")) | .username] | join(" ")' "$demo")" ""
esperar "demo: segundo factor desactivado no acceso, «Esqueciches o contrasinal?» e despois dun provedor" \
  "$(jq -r '[.authenticationFlows[].authenticationExecutions[] | select(.flowAlias // "" | endswith(" second factor")) | .requirement] | unique | join(" ")' "$demo")" DISABLED
esperar "produción: segundo factor no acceso, «Esqueciches o contrasinal?» e despois dun provedor" \
  "$(jq -r '[.authenticationFlows[].authenticationExecutions[] | select(.flowAlias // "" | endswith(" second factor")) | .requirement] | unique | join(" ")' "$prod")" CONDITIONAL
esperar "demo: Cl@ve simulada remata en furar-post-broker" \
  "$(jq -r '.identityProviders[] | select(.alias == "clave-simulada") | .postBrokerLoginFlowAlias' "$demo")" furar-post-broker

# --- Keycloak de usar e tirar -------------------------------------------------------------
titulo "Extensión de Keycloak (unitarios e jar)"
api/mvnw -B -q -f deploy/keycloak/extension/pom.xml package >/dev/null || falla "a extensión non compila ou os seus tests fallan"
extension="$FURAR_RAIZ/deploy/keycloak/extension/target/furar-keycloak-extension.jar"
[[ -s $extension ]] || falla "falta $extension"
ok "furar-keycloak-extension.jar"
segredo_proba=$(openssl rand -hex 24)
base=$(awk '/^FROM quay\.io\/keycloak\/keycloak/{print $2; exit}' deploy/keycloak/Dockerfile)
mailpit=$(awk '$1 == "image:" && $2 ~ /^axllent\/mailpit:/ {print $2; exit}' compose.yaml)
[[ -n $mailpit ]] || falla "non atopo a imaxe de Mailpit en compose.yaml"
nome="furar-proba-identidade-$$"
tmp=$(mktemp -d)
# O realm da proba (ver a cabeceira), nun directorio que Docker monta tamén con Colima (dentro de $HOME).
mkdir -p "$FURAR_RAIZ/.verificar"
realm_proba=$(mktemp -d "$FURAR_RAIZ/.verificar/realm-identidade.XXXXXX")
segredo_totp=$(openssl rand -hex 16)
api_pid=""
trap 'docker rm -f "$nome-correo" "$nome" >/dev/null 2>&1 || true; [[ -n $api_pid ]] && kill "$api_pid" 2>/dev/null; rm -rf "$tmp" "$realm_proba"' EXIT
jq --slurpfile p "$prod" --arg s "$segredo_totp" '
  .authenticationFlows = $p[0].authenticationFlows
  | .users |= map(if (.username | IN("xestor-naron", "xestor-ferrol")) then .credentials += [{type: "otp", userLabel: "Proba",
      secretData: ({value: $s} | tojson), credentialData: ({subType: "totp", digits: 6, counter: 0, period: 30, algorithm: "HmacSHA1"} | tojson)}]
    else . end)' "$demo" >"$realm_proba/furar-realm.json"
cp deploy/keycloak/import-demo/clave-simulada-realm.json "$realm_proba/"
chmod 755 "$realm_proba"
chmod 644 "$realm_proba"/*.json

# API simulada (ADR-0029): responde como /actuator/secondfactor da API, coa clave derivada do
# segredo. Persoal = correos persoal-*, xestor-*, recepcion-naron e admin-plataforma; o resto, non.
# POST /parar e /arrincar simulan a API parada (503). Escoita só onde chega o contedor: en Linux,
# na ponte de Docker (host-gateway); en macOS con Colima, host-gateway leva ao loopback do equipo.
porto_api=${FURAR_PROBA_API_PORTO:-18099}
if [[ $(uname) == Linux ]]; then
  enderezo_api=$(docker network inspect bridge -f '{{(index .IPAM.Config 0).Gateway}}')
else
  enderezo_api=127.0.0.1
fi
cat >"$tmp/api.cjs" <<'FIN'
const http = require('node:http');
const crypto = require('node:crypto');
const [porto, segredo, enderezo] = process.argv.slice(2);
const clave = crypto.createHmac('sha256', segredo.trim()).update('furar-second-factor-v1').digest('base64url');
const persoal = (correo) => /^(persoal-|xestor-|recepcion-naron@|admin-plataforma@)/.test(correo || '');
let parada = false;
http.createServer((req, res) => {
  let corpo = '';
  req.on('data', (c) => { corpo += c; });
  req.on('end', () => {
    if (req.method === 'POST' && req.url === '/parar') { parada = true; res.end('parada'); return; }
    if (req.method === 'POST' && req.url === '/arrincar') { parada = false; res.end('en marcha'); return; }
    if (req.method !== 'POST' || req.url !== '/actuator/secondfactor') { res.writeHead(404).end(); return; }
    if (parada) { res.writeHead(503).end(); return; }
    let p;
    try { p = JSON.parse(corpo); } catch { res.writeHead(400).end(); return; }
    if (p.key !== clave) { res.writeHead(403).end(); return; }
    res.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify({ required: persoal(p.email) }));
  });
}).listen(Number(porto), enderezo);
FIN
node "$tmp/api.cjs" "$porto_api" "$segredo_proba" "$enderezo_api" &
api_pid=$!
titulo "Keycloak de proba ($base) en $url"
docker run -d --name "$nome" -p "127.0.0.1:$porto:8082" -p "127.0.0.1:$porto_correo:8025" -m 1g \
  --add-host host.docker.internal:host-gateway \
  -e KC_HTTP_RELATIVE_PATH=/auth -e KC_HTTP_PORT=8082 -e KC_HOSTNAME="$url/auth" -e KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin-proba-identidade \
  -e FURAR_PUBLIC_URL="$url" -e FURAR_SMTP_HOST=localhost -e FURAR_SMTP_PORT=1025 -e FURAR_SMTP_FROM=reservas@example.org \
  -e FURAR_INVITATION_PROOF_SECRET="$segredo_proba" -e FURAR_API_INTERNAL_URL="http://host.docker.internal:$porto_api" \
  -v "$extension:/opt/keycloak/providers/furar-keycloak-extension.jar:ro" \
  -v "$FURAR_RAIZ/deploy/keycloak/themes/furar:/opt/keycloak/themes/furar:ro" \
  -v "$FURAR_RAIZ/deploy/keycloak/password-blacklists:/opt/keycloak/data/password-blacklists:ro" \
  -v "$realm_proba:/opt/keycloak/data/import:ro" \
  "$base" start-dev --import-realm >/dev/null
# Mailpit na rede do Keycloak: Keycloak mándalle o correo a localhost:1025; a API, en $correo_url.
docker run -d --name "$nome-correo" --network "container:$nome" -m 64m "$mailpit" >/dev/null
agardar_http "$url/auth/realms/clave-simulada/protocol/saml/descriptor" 200 "${FURAR_PROBA_KC_ESPERA:-180}" \
  || { docker logs --tail 40 "$nome"; falla "Keycloak non arrincou"; }
ok "realms furar (demo cos fluxos de produción) e clave-simulada importados"
agardar_http "$correo_url/api/v1/messages" 200 60 || falla "Mailpit non arrincou"
(cd web && npx playwright install chromium >/dev/null)

adm=$(curl -sf -d grant_type=password -d client_id=admin-cli -d username=admin -d password=admin-proba-identidade \
  "$url/auth/realms/master/protocol/openid-connect/token" | jq -r .access_token)
kc() { curl -sf -H "Authorization: Bearer $adm" "$url/auth/admin/realms/furar/$1"; }
# Conta co contrasinal temporal (acción UPDATE_PASSWORD): a páxina de cambiar o contrasinal é a
# mesma do restablecemento (o rexistro pídeo no propio formulario).
temporal="Temporal-d51-$(openssl rand -hex 4)"
if [[ $modo == rexistro || $modo == todo ]]; then
  curl -sf -o /dev/null -X POST -H "Authorization: Bearer $adm" -H 'Content-Type: application/json' "$url/auth/admin/realms/furar/users" \
    -d "$(jq -nc --arg p "$temporal" '{username: "contrasinal-d51@example.org", email: "contrasinal-d51@example.org", emailVerified: true,
      enabled: true, firstName: "Proba", lastName: "Contrasinal", attributes: {locale: ["gl"]},
      credentials: [{type: "password", value: $p, temporary: true}]}')" || falla "non se creou a conta co contrasinal temporal"
fi
comun=$(grep -m1 -E '^contrasinal[0-9]{4}$' deploy/keycloak/password-blacklists/furar-contrasinais-comuns.txt)

cat >"$tmp/proba.cjs" <<'FIN'
const { chromium } = require(process.env.PW);
const { AxeBuilder } = require(process.env.AXE);
const [modo, url, web, caminoPrivacidade, nif, temporal, comun, correoUrl, marca] = process.argv.slice(2);
const politica = `${web}${caminoPrivacidade}`;
// O rexistro desde un concello: a web pon o camiño da súa páxina «Privacidade» en furar_privacy.
const acceso = (lang, extra = '') => `${url}/auth/realms/furar/protocol/openid-connect/auth?client_id=furar-web&response_type=code&scope=openid`
  + `&redirect_uri=${encodeURIComponent(`${web}/`)}&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM`
  + `&code_challenge_method=S256&ui_locales=${lang}&prompt=create${extra}`;
const demoPassword = process.env.FURAR_DEMO_PASSWORD;
if (!demoPassword) { console.error('  ✗ sen FURAR_DEMO_PASSWORD'); process.exit(1); }
const conta = `${url}/auth/realms/furar/account`;
// O erro vai nunha soa liña (os textos da páxina, sen saltos), para que saia enteiro ao final do log.
const fallo = (m) => { console.error(`  ✗ ${String(m).replace(/\s+/g, ' ')}`); process.exit(1); };
const ok = (m) => console.log(`  ✓ ${m}`);
// Código TOTP (RFC 6238, HMAC-SHA1, 6 cifras, 30 s) dun segredo en bruto, como a aplicación.
const totp = (segredo, agora = Date.now()) => {
  const contador = Buffer.alloc(8);
  contador.writeBigUInt64BE(BigInt(Math.floor(agora / 30000)));
  const mac = require('node:crypto').createHmac('sha1', Buffer.from(segredo, 'utf8')).update(contador).digest();
  const d = mac[mac.length - 1] & 0x0f;
  return String((mac.readUInt32BE(d) & 0x7fffffff) % 1000000).padStart(6, '0');
};
// Segredo da aplicación xa configurada en xestor-naron e xestor-ferrol (ao chou, desta proba).
const segredoDemo = process.env.PROBA_TOTP_SECRET;
// Un código da aplicación non vale dúas veces (otpPolicyCodeReusable false, como en produción):
// cada segredo usa un intervalo de 30 s novo; Keycloak admite o seguinte, e se xa se usou, espérase.
const usados = new Map();
const totpNovo = async (segredo) => {
  let paso = Math.floor(Date.now() / 30000);
  if (usados.has(segredo) && usados.get(segredo) >= paso) paso = usados.get(segredo) + 1;
  while (paso > Math.floor(Date.now() / 30000) + 1) await new Promise((r) => setTimeout(r, 1000));
  usados.set(segredo, paso);
  return totp(segredo, paso * 30000);
};
const apiSimulada = process.env.API_SIMULADA;
(async () => {
  const navegador = await chromium.launch();
  try {
    if (modo === 'rexistro' || modo === 'todo') {
      const mais = { gl: 'Privacidade e os teus dereitos', es: 'Privacidad y tus derechos' };
      // Frase xenérica: os datos do responsable, o DPD e os dereitos están na páxina do concello.
      const frase = { gl: 'Quen presta o servizo trata os teus datos para xestionar a túa conta.', es: 'Quien presta el servicio trata tus datos para gestionar tu cuenta.' };
      const sen320 = async (p, onde) => {
        await p.setViewportSize({ width: 320, height: 800 });
        await p.reload();
        const ancho = await p.evaluate(() => document.documentElement.scrollWidth);
        if (ancho > 320) fallo(`${onde}: scroll horizontal a 320 px (${ancho} px)`);
        await p.setViewportSize({ width: 1280, height: 900 });
        await p.reload();
      };
      // Marca do despregue (o nome do realm) e ningún «Furar» nas pantallas (2026-11-03).
      const servizo = { gl: 'Deportes municipais', es: 'Deportes municipales' };
      const semFurar = async (p, onde) => {
        const nomeMarca = (await p.locator('.furar-brand__name').innerText()).trim();
        if (nomeMarca !== marca) fallo(`${onde}: a marca é «${nomeMarca}», non «${marca}»`);
        const texto = await p.locator('body').innerText();
        if (/furar/i.test(texto)) fallo(`${onde}: a pantalla di «Furar»:\n${texto.slice(0, 800)}`);
        if (/furar/i.test(await p.title())) fallo(`${onde}: o título da páxina di «Furar»`);
      };
      // «Verificación do correo»: un texto, sen o aviso de Keycloak, e o botón de reenviar.
      const verificar = {
        gl: { titulo: 'Verificación do correo electrónico', texto: 'Mandámosche un correo coas instrucións para verificar a túa conta.',
          pregunta: 'Non recibiches o correo?', boton: 'Reenviar o correo', espera: 'agardar', asunto: 'Verifica o teu correo',
          curta: 'Non funciona o botón? Abre esta ligazón:', caducada: 'A ligazón caducou' },
        es: { titulo: 'Verificación del correo electrónico', texto: 'Te hemos enviado un correo con instrucciones para verificar tu cuenta.',
          pregunta: '¿No has recibido el correo?', boton: 'Reenviar el correo', espera: 'esperar', asunto: 'Verifica tu correo',
          curta: '¿El botón no funciona? Abre este enlace:', caducada: 'El enlace ha caducado' },
      };
      const correos = async (enderezo) => {
        for (let i = 0; i < 20; i++) {
          const r = await fetch(`${correoUrl}/api/v1/search?query=${encodeURIComponent(`to:${enderezo}`)}`);
          const { messages = [] } = await r.json();
          if (messages.length) return messages;
          await new Promise((ok2) => setTimeout(ok2, 500));
        }
        return [];
      };
      for (const lang of ['gl', 'es']) {
        const c0 = await navegador.newContext({ locale: lang === 'gl' ? 'gl-ES' : 'es-ES' });
        await c0.route(`${web}/**`, (r) => r.abort());
        const p = await c0.newPage();
        const erros = [];
        p.on('pageerror', (e) => erros.push(e.message));
        await p.goto(acceso(lang, `&furar_privacy=${encodeURIComponent(caminoPrivacidade)}`));
        const aviso = p.locator('#furar-privacy-notice');
        await aviso.waitFor();
        const texto = (await aviso.innerText()).replace(/\s+/g, ' ');
        for (const t of [frase[lang], mais[lang]]) {
          if (!texto.includes(t)) fallo(`${lang}: a información de protección de datos non contén «${t}»:\n${texto}`);
        }
        if ((await aviso.locator('p').count()) !== 1) fallo(`${lang}: a información de protección de datos non vai nunha frase`);
        if ((await p.locator('#termsAccepted, input[type=checkbox]').count()) !== 0) fallo(`${lang}: hai unha casiña no rexistro`);
        // O correo é o usuario; sen campo de usuario, e o do correo co seu autocompletado.
        if ((await p.locator('#username').count()) !== 0) fallo(`${lang}: o rexistro pide nome de usuario`);
        const autocompletar = await p.locator('#email').getAttribute('autocomplete');
        if (autocompletar !== 'email') fallo(`${lang}: o correo ten autocomplete=${autocompletar}, non email`);
        const etiqueta = await p.locator('label[for="email"]').innerText();
        if (!etiqueta.includes('Correo electrónico')) fallo(`${lang}: a etiqueta do correo é «${etiqueta}»`);
        const href = await aviso.locator('a').getAttribute('href');
        if (href !== politica) fallo(`${lang}: ligazón ${href}, esperada ${politica}`);
        const h = await p.locator('#furar-privacy-notice-title').evaluate((e) => e.tagName);
        if (h !== 'H2') fallo(`${lang}: o título da información é ${h}, non un encabezado`);
        const axe = await new AxeBuilder({ page: p }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
        if (axe.violations.length) fallo(`${lang}: axe no rexistro: ${axe.violations.map((v) => v.id).join(', ')}`);
        await sen320(p, `${lang}: rexistro`);
        ok(`${lang}: «${frase[lang]} ${mais[lang]}» (a páxina do concello) nunha frase curta, sen casiña, axe sen violacións, sen scroll a 320 px`);
        await semFurar(p, `${lang}: rexistro`);
        const servizoVisto = (await p.locator('.furar-brand__service').innerText()).trim();
        if (servizoVisto !== servizo[lang]) fallo(`${lang}: o servizo da cabeceira é «${servizoVisto}»`);
        ok(`${lang}: cabeceira «${marca} · ${servizo[lang]}», sen «Furar» na pantalla`);
        if (erros.length) fallo(`${lang}: erros de JavaScript no rexistro: ${erros.join(' | ')}`);
        ok(`${lang}: rexistro sen erros de JavaScript`);
        {
          const u = `rexistro${lang}${Date.now()}`;
          await p.locator('#email').fill(`${u}@example.org`);
          await p.locator('#firstName').fill('Proba');
          await p.locator('#lastName').fill('Rexistro');
          // O contrasinal vai no mesmo formulario, coa axuda nunha liña.
          if (!(await p.locator('#password').count())) fallo('o rexistro non pide o contrasinal no formulario');
          const axudaRexistro = (await p.locator('#furar-password-help-password').innerText().catch(() => '')).trim();
          const axudaEsperada = { gl: 'Polo menos 10 caracteres e un número.', es: 'Al menos 10 caracteres y un número.' }[lang];
          if (axudaRexistro !== axudaEsperada) fallo(`${lang}: rexistro: axuda do contrasinal «${axudaRexistro}», esperada «${axudaEsperada}»`);
          if ((await p.locator('#email').getAttribute('readonly')) !== null) fallo('sen invitación, o correo do rexistro é só de lectura');
          const orde = await p.evaluate(() => ['email', 'firstName', 'lastName', 'password', 'password-confirm']
            .every((id, i, ids) => i === 0 || Boolean(document.getElementById(ids[i - 1]).compareDocumentPosition(document.getElementById(id)) & Node.DOCUMENT_POSITION_FOLLOWING)));
          if (!orde) fallo('a orde do rexistro non é correo, nome, apelidos, contrasinal e repetilo');
          await p.locator('#password').fill('Rexistro-proba-2026');
          await p.locator('#password-confirm').fill('Rexistro-proba-2026');
          await p.locator('input[type=submit]').click();
          await p.waitForLoadState('networkidle');
          if (await p.locator('#furar-privacy-notice').count()) {
            fallo(`o rexistro non avanzou: ${await p.locator('[id^=input-error], .pf-v5-c-alert').allInnerTexts()}`);
          }
          if (lang === 'gl') console.log(`USUARIO ${u}`);
          ok(`${lang}: o rexistro avanza sen aceptar termos`);

          // Verificación do correo: un só texto, sen o aviso «Tes que verificar…», e un botón de verdade.
          const v = verificar[lang];
          const titulo = (await p.locator('#kc-page-title').innerText()).trim();
          if (titulo !== v.titulo) fallo(`${lang}: tras o rexistro, «${titulo}» e non «${v.titulo}»`);
          if (await p.locator('.pf-v5-c-alert').count()) fallo(`${lang}: verificación co aviso de Keycloak: ${await p.locator('.pf-v5-c-alert').allInnerTexts()}`);
          const textoV = (await p.locator('#furar-verify-email-sent').innerText()).replace(/\s+/g, ' ').trim();
          if (textoV !== v.texto) fallo(`${lang}: texto da verificación «${textoV}»`);
          const preguntaV = (await p.locator('#furar-verify-resend-question').innerText()).trim();
          if (preguntaV !== v.pregunta) fallo(`${lang}: pregunta «${preguntaV}»`);
          const boton = p.locator('#kc-resend');
          const [etiquetaB, tag, tipo, metodo] = await boton.evaluate((b) => [b.innerText.trim(), b.tagName, b.type, b.form && b.form.method]);
          if (etiquetaB !== v.boton || tag !== 'BUTTON' || tipo !== 'submit' || metodo !== 'post') fallo(`${lang}: botón de reenviar «${etiquetaB}» ${tag} ${tipo} ${metodo}`);
          if (await p.locator('#kc-info a').count()) fallo(`${lang}: segue a ligazón «Preme aquí» para reenviar`);
          const centro = await p.locator('.furar-verify-resend').evaluate((e) => getComputedStyle(e).textAlign);
          if (centro !== 'center') fallo(`${lang}: a pregunta e o botón non van ao centro (${centro})`);
          await semFurar(p, `${lang}: verificación do correo`);
          const axeV = await new AxeBuilder({ page: p }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
          if (axeV.violations.length) fallo(`${lang}: axe na verificación: ${axeV.violations.map((x) => x.id).join(', ')}`);
          // O botón chégase co teclado e o foco vese.
          await p.locator('#furar-verify-resend-question').click();
          await p.keyboard.press('Tab');
          const foco = await p.evaluate(() => { const a = document.activeElement; const s = getComputedStyle(a); return [a.id, a.matches(':focus-visible'), s.outlineStyle !== 'none' && s.outlineWidth !== '0px']; });
          if (foco[0] !== 'kc-resend' || !foco[1] || !foco[2]) fallo(`${lang}: o foco do teclado no botón de reenviar non se ve (${foco})`);
          await sen320(p, `${lang}: verificación do correo`);
          // O correo chegou co nome do remitente e o asunto; reenviar xa, antes da espera, dá o aviso da espera.
          const [m] = await correos(`${u}@example.org`);
          if (!m) fallo(`${lang}: o correo de verificación non chegou a Mailpit`);
          if (m.From?.Name !== marca) fallo(`${lang}: remitente «${m.From?.Name}», non «${marca}»`);
          if (m.Subject !== v.asunto) fallo(`${lang}: asunto «${m.Subject}», non «${v.asunto}»`);
          const corpo = await (await fetch(`${correoUrl}/api/v1/message/${m.ID}`)).json();
          // Sen contar as ligazóns (o realm e o cliente chámanse furar no URL).
          if (!corpo.HTML.includes(marca) || /furar/i.test(`${corpo.Text}`.replace(/https?:\/\/\S+/g, ''))) fallo(`${lang}: o correo non leva «${marca}» ou di «Furar»`);
          // Baixo o botón, a ligazón curta: redirixe ao URL do botón; un código que non
          // existe (ou caducou) dá a páxina de erro do tema, co xeito de pedir outra.
          const curta = /https?:\/\/\S+\/auth\/realms\/furar\/l\/(?:[2-9A-HJKMNP-Z]{4}-){2}[2-9A-HJKMNP-Z]{4}/.exec(corpo.Text)?.[0];
          const urlBoton = /href="([^"]+\/login-actions\/action-token\?[^"]+)"/.exec(corpo.HTML)?.[1]?.replaceAll('&amp;', '&');
          if (!curta || !urlBoton || !corpo.HTML.includes(`${v.curta}<br><a href="${curta}"`)) fallo(`${lang}: sen a ligazón curta baixo o botón`);
          const r302 = await fetch(curta, { redirect: 'manual' });
          if (r302.status !== 302 || r302.headers.get('location') !== urlBoton) fallo(`${lang}: a ligazón curta deu ${r302.status} ${r302.headers.get('location')}`);
          const r404 = await fetch(`${url}/auth/realms/furar/l/2222-2222-2222`, { headers: { 'Accept-Language': lang } });
          const t404 = await r404.text();
          if (r404.status !== 404 || !t404.includes(v.caducada)) fallo(`${lang}: a ligazón curta descoñecida deu ${r404.status}: ${t404.slice(0, 300)}`);
          // Os dous botóns: o acceso do cliente da web (con PKCE) e «Esqueciches o contrasinal?», sen nada entre «».
          if (!/id="furar-link-expired-login"[^>]*href="[^"]*\/protocol\/openid-connect\/auth\?client_id=furar-web&amp;[^"]*code_challenge_method=S256/.test(t404)
            || !/id="furar-link-expired-reset"[^>]*href="[^"]*\/login-actions\/reset-credentials\?client_id=furar-web&amp;/.test(t404)
            || /«/.test(t404) || !`${r404.headers.get('cache-control')}`.includes('no-store')) fallo(`${lang}: a páxina da ligazón caducada non leva os dous botóns ou se garda na caché`);
          // Keycloak espera 30 s entre envíos: antes, o aviso da espera; se a proba tardou máis, un segundo correo.
          await boton.click();
          await p.waitForLoadState('networkidle');
          const avisos = (await p.locator('.pf-v5-c-alert').allInnerTexts()).join(' ');
          if (!avisos.includes(v.espera)) {
            if (avisos) fallo(`${lang}: reenviar deu «${avisos}»`);
            let n = 0;
            for (let i = 0; i < 20 && n < 2; i++) {
              n = (await correos(`${u}@example.org`)).length;
              if (n < 2) await new Promise((ok2) => setTimeout(ok2, 500));
            }
            if (n < 2) fallo(`${lang}: reenviar non deu o aviso da espera nin mandou outro correo`);
          }
          if ((await p.locator('#kc-resend').innerText()).trim() !== v.boton) fallo(`${lang}: tras reenviar, sen o botón`);
          ok(`${lang}: «${v.titulo}»: un texto, sen o aviso de Keycloak, «${v.pregunta}» e o botón «${v.boton}» ao centro (teclado e axe); o correo chega de «${marca}» («${v.asunto}») coa ligazón curta (302 ao botón; descoñecida, 404 co tema) e o botón reenvía (aviso da espera ou segundo correo)`);
        }
        await c0.close();
      }
      // A ligazón da invitación abre o rexistro co correo xa posto (prompt=create + login_hint).
      const ctx = await navegador.newContext({ locale: 'gl-ES' });
      await ctx.route('http://localhost:4200/**', (r) => r.fulfill({ status: 200, body: 'web' }));
      const q = await ctx.newPage();
      const convidada = `convidada-d51-${Date.now()}@example.org`;
      await q.goto(`${url}/auth/realms/furar/protocol/openid-connect/auth?client_id=furar-web&response_type=code&scope=openid`
        + `&redirect_uri=${encodeURIComponent('http://localhost:4200/')}&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM`
        + `&code_challenge_method=S256&ui_locales=gl&prompt=create&login_hint=${encodeURIComponent(convidada)}`);
      const posto = await q.locator('#email').inputValue().catch(() => '');
      if (posto !== convidada) fallo(`o rexistro con login_hint non trae o correo posto: «${posto}»`);
      ok('prompt=create + login_hint: o rexistro trae o correo xa posto');
      // A frase só sae co camiño da páxina do concello (furar_privacy) e nunca con furar_setup=1
      // (sen concellos); un camiño que non vale non leva a ningures, así que tampouco sae (só vista).
      const coPrivacidade = `&furar_privacy=${encodeURIComponent(caminoPrivacidade)}`;
      for (const [extra, conFrase] of [
        [`${coPrivacidade}&furar_setup=1`, false], [`${coPrivacidade}&furar_setup=0`, true], [coPrivacidade, true],
        ['', false], ['&furar_privacy=' + encodeURIComponent('//evil.example/privacidade'), false],
        ['&furar_privacy=' + encodeURIComponent('https://evil.example/privacidade'), false]]) {
        await q.goto(acceso('gl', extra));
        await q.locator('#kc-register-form').waitFor();
        const ve = (await q.locator('#furar-privacy-notice').count()) > 0;
        if (ve !== conFrase) fallo(`«${extra}»: ${ve ? 'mostra' : 'non mostra'} a frase de privacidade`);
        if (ve) {
          const href = await q.locator('#furar-privacy-notice a').getAttribute('href');
          if (href !== politica) fallo(`«${extra}»: ligazón ${href}, esperada ${politica}`);
        }
        if ((await q.locator('#email').getAttribute('readonly')) !== null) fallo(`«${extra}»: o correo é só de lectura`);
      }
      ok('frase de privacidade só co camiño do concello (furar_privacy), na orixe da web; sen el, cun camiño que non vale ou con furar_setup=1, sen a frase');
      await ctx.close();

      // A invitación do persoal verifica o correo (ADR-0027): a proba firmada co segredo compartido.
      const crypto = require('node:crypto');
      const proba = (email, { segredo = process.env.PROOF_SECRET, id = crypto.randomUUID(), caduca = Math.floor(Date.now() / 1000) + 1800 } = {}) => {
        const carga = `v1.${id}.${caduca}.${Buffer.from(email).toString('base64url')}`;
        return `${carga}.${crypto.createHmac('sha256', segredo).update(carga).digest('base64url')}`;
      };
      const admin = async () => {
        const r = await fetch(`${url}/auth/realms/master/protocol/openid-connect/token`, { method: 'POST',
          body: new URLSearchParams({ grant_type: 'password', client_id: 'admin-cli', username: 'admin', password: 'admin-proba-identidade' }) });
        return (await r.json()).access_token;
      };
      const usuario = async (email) => {
        const r = await fetch(`${url}/auth/admin/realms/furar/users?email=${encodeURIComponent(email)}&exact=true`, { headers: { Authorization: `Bearer ${await admin()}` } });
        const [u] = await r.json();
        if (!u) fallo(`non se creou a conta ${email}`);
        return u;
      };
      const rexistrar = async (correo, probaUrl, { outroCorreo } = {}) => {
        // A volta á web detéctase pola petición ao redirect_uri co código, non pola páxina que
        // carga: na CI ningún servidor escoita en 4200 e route.fulfill non colle a redirección
        // (en local respondía a web da demo). Córtase sempre, para que local e CI sexan iguais.
        const c = await navegador.newContext({ locale: 'gl-ES' });
        await c.route('http://localhost:4200/**', (r) => r.abort());
        const pg = await c.newPage();
        let aWeb = false;
        const vaiAWeb = new Promise((resolve) => pg.on('request', (r) => {
          const u = new URL(r.url());
          if (u.origin === 'http://localhost:4200' && u.searchParams.has('code')) { aWeb = true; resolve(true); }
        }));
        const erros = [];
        pg.on('pageerror', (e) => erros.push(e.message));
        await pg.goto(`${url}/auth/realms/furar/protocol/openid-connect/auth?client_id=furar-web&response_type=code&scope=openid`
          + `&redirect_uri=${encodeURIComponent('http://localhost:4200/')}&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM`
          + `&code_challenge_method=S256&ui_locales=gl&prompt=create&login_hint=${encodeURIComponent(correo)}`
          + `&furar_privacy=${encodeURIComponent(caminoPrivacidade)}`
          + (probaUrl ? `&furar_invitation=${encodeURIComponent(probaUrl)}` : ''));
        await pg.locator('#kc-register-form').waitFor();
        const fixo = (await pg.locator('#email').getAttribute('readonly')) !== null;
        // A frase de privacidade só sae sen proba válida: o persoal invitado infórmase no correo (RGPD art. 14).
        const aviso = (await pg.locator('#furar-privacy-notice').count()) > 0;
        if (aviso === fixo) fallo(fixo ? 'coa proba da invitación, o rexistro mostra a frase de privacidade' : 'sen proba válida, o rexistro non mostra a frase de privacidade');
        if (outroCorreo) {
          // Quen manipula o formulario: quita o só lectura e escribe outro correo.
          await pg.locator('#email').evaluate((e, v) => { e.readOnly = false; e.value = v; }, outroCorreo);
        }
        await pg.locator('#firstName').fill('Proba');
        await pg.locator('#lastName').fill('Convite');
        await pg.locator('#password').fill('Convite-proba-2026');
        await pg.locator('#password-confirm').fill('Convite-proba-2026');
        if (process.env.CAPTURAS && fixo) await pg.screenshot({ path: `${process.env.CAPTURAS}/rexistro-invitacion.png`, fullPage: true });
        const cargou = pg.waitForEvent('domcontentloaded', { timeout: 30000 }).then(() => false, () => false);
        await pg.locator('input[type=submit]').click();
        let volveu = (await Promise.race([vaiAWeb, cargou])) || aWeb;
        if (!volveu) await pg.waitForLoadState('load');
        if (erros.length) fallo(`erros de JavaScript no rexistro coa invitación: ${erros.join(' | ')}`);
        if (!volveu && (await pg.locator('#kc-register-form').count())) {
          fallo(`o rexistro de ${correo} non avanzou: ${await pg.locator('[id^=input-error], .pf-v5-c-alert').allInnerTexts()}`);
        }
        // Quen vai ser persoal escolle e configura o segundo factor antes de volver á web (ADR-0029):
        // a aplicación (a primeira e marcada).
        if (!volveu && (await pg.locator('#furar-second-factor-choice').count()) > 0) {
          if (!(await pg.locator('#furar-method-app').isChecked())) fallo('na escolla, a aplicación non vén marcada');
          await pg.locator('#furar-second-factor-continue').click();
          await pg.waitForLoadState('load');
        }
        const alta = !volveu && (await pg.locator('#kc-totp-settings-form').count()) > 0;
        if (alta) {
          // Primeiro, que móbil (o resto vén despois).
          if (await pg.locator('button[data-phone="android"]').isVisible()) await pg.locator('button[data-phone="android"]').click();
          await pg.locator('#totp').fill(await totpNovo(await pg.locator('#totpSecret').inputValue()));
          await pg.locator('#saveTOTPBtn').click();
          volveu = (await Promise.race([vaiAWeb, new Promise((r) => setTimeout(() => r(false), 30000))])) || aWeb;
        }
        await c.close();
        return { fixo, volveu, alta };
      };
      const borrar = async (id) => {
        const r = await fetch(`${url}/auth/admin/realms/furar/users/${id}`, { method: 'DELETE', headers: { Authorization: `Bearer ${await admin()}` } });
        if (!r.ok) fallo(`non se borrou a conta ${id}`);
      };
      const sinVerificar = async (correo, que) => {
        const u = await usuario(correo);
        if (u.emailVerified) fallo(`${que}: a conta ${correo} quedou co correo verificado`);
        if (!(u.requiredActions || []).includes('VERIFY_EMAIL')) fallo(`${que}: Keycloak non pide verificar o correo (${u.requiredActions})`);
        return u;
      };

      const sello = Date.now();
      const invitada = `invitada-${sello}@example.org`;
      const valida = proba(invitada);
      const r1 = await rexistrar(invitada, valida);
      if (!r1.fixo) fallo('coa proba da invitación, o correo non é só de lectura');
      if (!r1.alta) fallo('coa proba da invitación, Keycloak non pide configurar o segundo factor antes de volver (ADR-0029)');
      if (!r1.volveu) fallo('coa proba da invitación, Keycloak non volve á web (pide verificar o correo?)');
      const u1 = await usuario(invitada);
      if (!u1.emailVerified) fallo('coa proba da invitación, a conta non nace co correo verificado');
      if ((u1.requiredActions || []).length) fallo(`coa proba da invitación quedan accións pendentes: ${u1.requiredActions}`);
      const evR = await fetch(`${url}/auth/admin/realms/furar/events?type=VERIFY_EMAIL&user=${u1.id}`, { headers: { Authorization: `Bearer ${await admin()}` } });
      const ev = (await evR.json()).find((e) => e.details?.furar_verified_by === 'staff_invitation');
      if (!ev) fallo('sen o evento VERIFY_EMAIL coa orixe na invitación');
      ok('invitación: correo fixo, sen a frase de privacidade (vai no correo), contrasinal no formulario, alta do segundo factor, conta co correo verificado e volta á web sen correo; evento VERIFY_EMAIL (furar_verified_by=staff_invitation)');

      // A mesma proba outra vez (a conta borrada, para que o correo quede libre): xa usada.
      await borrar(u1.id);
      const r2 = await rexistrar(invitada, valida);
      if (r2.volveu) fallo('a proba reutilizada volveu á web sen verificar o correo');
      await sinVerificar(invitada, 'proba reutilizada');
      ok('a mesma proba só verifica unha vez: a segunda conta ten que verificar o correo');

      const casos = [
        ['firma doutro segredo', `outro-segredo-${sello}@example.org`, (c) => proba(c, { segredo: 'x'.repeat(48) })],
        ['caducada', `caducada-${sello}@example.org`, (c) => proba(c, { caduca: Math.floor(Date.now() / 1000) - 5 })],
      ];
      for (const [que, correo, xera] of casos) {
        const r = await rexistrar(correo, xera(correo));
        if (r.fixo) fallo(`${que}: o correo quedou só de lectura`);
        if (r.volveu) fallo(`${que}: volveu á web sen verificar o correo`);
        await sinVerificar(correo, que);
      }
      ok('proba con firma doutro segredo ou caducada: correo editable e Keycloak pide verificalo');
      const alleo = `alleo-${sello}@example.org`;
      const r3 = await rexistrar(`orixe-${sello}@example.org`, proba(`orixe-${sello}@example.org`), { outroCorreo: alleo });
      if (r3.volveu) fallo('outro correo coa proba: volveu á web sen verificar');
      await sinVerificar(alleo, 'outro correo');
      ok('proba válida pero outro correo no formulario: Keycloak pide verificalo');

      // Tema: escuro só se a web gardou o esquema escuro (mesma orixe); o do sistema non conta.
      for (const [que, gardado, sistema, esperado] of [['gardado escuro', 'dark', 'light', 'dark'], ['gardado claro', 'light', 'dark', 'light'], ['sistema escuro', null, 'dark', 'light']]) {
        const c = await navegador.newContext({ locale: 'gl-ES', colorScheme: sistema });
        if (gardado) await c.addInitScript((v) => { try { localStorage.setItem('furar.colorScheme', v); } catch {} }, gardado);
        const pg = await c.newPage();
        await pg.goto(`${conta}?kc_locale=gl`);
        await pg.locator('#kc-form-login, #username').first().waitFor();
        const tema = await pg.evaluate(() => document.documentElement.dataset.theme);
        if (tema !== esperado) fallo(`tema ${que}: data-theme=${tema}, esperado ${esperado}`);
        const fondo = await pg.evaluate(() => getComputedStyle(document.body).backgroundColor);
        if ((esperado === 'dark') !== (fondo === 'rgb(13, 17, 23)')) fallo(`tema ${que}: fondo ${fondo}`);
        if (esperado === 'dark') {
          for (const paxina of ['acceso', 'rexistro']) {
            if (paxina === 'rexistro') await pg.locator('a[href*="login-actions/registration"]').click();
            const axeD = await new AxeBuilder({ page: pg }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
            if (axeD.violations.length) fallo(`axe no ${paxina} escuro (${que}): ${axeD.violations.map((v) => `${v.id} ${v.nodes.map((n) => n.target).join(' ')}`).join(', ')}`);
            if (process.env.CAPTURAS) await pg.screenshot({ path: `${process.env.CAPTURAS}/${paxina}-${que.replace(/ /g, '-')}.png`, fullPage: true });
          }
        }
        await c.close();
      }
      ok('tema: escuro só se o gardou a web, claro co sistema escuro; acceso e rexistro escuros sen violacións de axe');
    }
    if (modo === 'rexistro' || modo === 'todo') {
      // Poñer o contrasinal (login-update-password): a axuda nunha liña e os erros do servidor ao enviar.
      const p = await (await navegador.newContext({ locale: 'gl-ES' })).newPage();
      const erros = [];
      p.on('pageerror', (e) => erros.push(e.message));
      await p.goto(`${conta}?kc_locale=gl`);
      await p.locator('#username').fill('contrasinal-d51@example.org');
      await p.locator('#password').fill(temporal);
      await p.locator('#kc-login').click();
      const novo = p.locator('#password-new');
      await novo.waitFor();
      const axuda = (await p.locator('#furar-password-help-password-new').innerText().catch(() => '')).trim();
      if (axuda !== 'Polo menos 10 caracteres e un número.') fallo(`axuda do contrasinal «${axuda}», esperada «Polo menos 10 caracteres e un número.»`);
      if (await p.locator('#furar-password-rules').count()) fallo('segue a lista de requisitos en vivo');
      if (await p.locator('#logout-sessions').count()) fallo('segue «Pechar a sesión noutros dispositivos»');
      ok('poñer o contrasinal: «Polo menos 10 caracteres e un número.» baixo o campo, sen lista en vivo nin «Pechar a sesión noutros dispositivos»');
      const axe = await new AxeBuilder({ page: p }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
      if (axe.violations.length) fallo(`axe ao poñer o contrasinal: ${axe.violations.map((v) => v.id).join(', ')}`);
      const ancho = await (async () => { await p.setViewportSize({ width: 320, height: 800 }); const w = await p.evaluate(() => document.documentElement.scrollWidth); await p.setViewportSize({ width: 1280, height: 900 }); return w; })();
      if (ancho > 320) fallo(`poñer o contrasinal: scroll horizontal a 320 px (${ancho} px)`);
      // O servidor valida a política ao enviar: curto, sen número, común (lista de Furar) e igual ao correo.
      for (const [pw, esperado] of [['Curto-12', 'polo menos 10'], ['Sen-numero-ningun', 'polo menos 1 número'], [comun, 'non está permitido'], ['contrasinal-d51@example.org', 'non pode ser igual']]) {
        await novo.fill(pw);
        await p.locator('#password-confirm').fill(pw);
        await p.locator('#kc-submit').click();
        const erro = p.locator('.pf-v5-c-alert, [id^=input-error-password]').filter({ hasText: esperado });
        await erro.first().waitFor().catch(async () => fallo(`«${pw}»: o servidor non devolveu «${esperado}»: ${(await p.locator('main, body').first().innerText()).slice(0, 600)}`));
      }
      ok(`o servidor rexeita ao enviar: curto, sen número, común («${comun}», da lista) e igual ao correo (erros en galego)`);
      const bo = 'Cambio-d51-2026';
      await novo.fill(bo);
      await p.locator('#password-confirm').fill(bo);
      await p.locator('#kc-submit').click();
      await p.waitForURL((u) => u.href.startsWith(conta), { timeout: 30000 }).catch(() => fallo(`o cambio de contrasinal non volveu á conta: ${p.url()}`));
      if (erros.length) fallo(`erros de JavaScript ao cambiar o contrasinal: ${erros.join(' | ')}`);
      ok('contrasinal válido: gárdase e volve á conta; sen erros de JavaScript');
      await p.close();

      // Aviso xenérico do bloqueo: o mesmo erro e o mesmo aviso exista ou non a conta.
      const aviso = 'Despois de 5 intentos fallidos, a conta bloquéase 15 minutos. Se xa está bloqueada, agarda ese tempo ou usa «Esqueciches o contrasinal?».';
      const textos = [];
      for (const usuario of ['xestor-naron@example.org', 'non-existe-d51@example.org']) {
        const q = await (await navegador.newContext({ locale: 'gl-ES' })).newPage();
        await q.goto(`${conta}?kc_locale=gl`);
        await q.locator('#username').fill(usuario);
        await q.locator('#password').fill('Mal-contrasinal-1');
        await q.locator('#kc-login').click();
        await q.locator('#furar-lockout-notice').waitFor().catch(() => fallo(`${usuario}: sen aviso do bloqueo tras o fallo`));
        const t = (await q.locator('#furar-lockout-notice').innerText()).replace(/\s+/g, ' ').trim();
        if (t !== aviso) fallo(`${usuario}: aviso «${t}»`);
        textos.push(`${await q.locator('#input-error-username').innerText()} | ${t}`);
        const axe2 = await new AxeBuilder({ page: q }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
        if (axe2.violations.length) fallo(`axe no acceso fallido: ${axe2.violations.map((v) => v.id).join(', ')}`);
        await q.close();
      }
      if (textos[0] !== textos[1]) fallo(`o acceso fallido distingue se a conta existe: ${textos.join(' ≠ ')}`);
      ok(`acceso fallido: «${aviso}», igual exista ou non a conta; axe sen violacións`);
    }
    if (modo !== 'clave') {
      // Verificación en dous pasos do persoal (ADR-0029), coa API simulada (persoal-*, xestor-*…).
      if (!segredoDemo || !apiSimulada) fallo('sen PROBA_TOTP_SECRET ou API_SIMULADA');
      const crypto = require('node:crypto');
      const etiquetas = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
      const axeSen = async (p, onde) => {
        const r = await new AxeBuilder({ page: p }).withTags(etiquetas).analyze();
        if (r.violations.length) fallo(`axe en ${onde}: ${r.violations.map((v) => `${v.id} ${v.nodes.map((n) => n.target).join(' ')}`).join(', ')}`);
      };
      const tokenAdmin = async () => {
        const r = await fetch(`${url}/auth/realms/master/protocol/openid-connect/token`, { method: 'POST',
          body: new URLSearchParams({ grant_type: 'password', client_id: 'admin-cli', username: 'admin', password: 'admin-proba-identidade' }) });
        return (await r.json()).access_token;
      };
      const contrasinalSf = 'Segundo-factor-2026';
      const crear = async (email) => {
        const r = await fetch(`${url}/auth/admin/realms/furar/users`, { method: 'POST',
          headers: { Authorization: `Bearer ${await tokenAdmin()}`, 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: email, email, emailVerified: true, enabled: true, firstName: 'Proba', lastName: 'Código',
            attributes: { locale: ['gl'] }, credentials: [{ type: 'password', value: contrasinalSf, temporary: false }] }) });
        if (r.status !== 201) fallo(`non se creou ${email}: ${r.status}`);
      };
      const probaConvite = (email) => {
        const carga = `v1.${crypto.randomUUID()}.${Math.floor(Date.now() / 1000) + 1800}.${Buffer.from(email).toString('base64url')}`;
        return `${carga}.${crypto.createHmac('sha256', process.env.PROOF_SECRET).update(carga).digest('base64url')}`;
      };
      // Correo e contrasinal na consola da conta (ou nun URL de acceso); devolve a páxina seguinte.
      const entrar = async (email, contrasinal, enderezo = `${conta}?kc_locale=gl`) => {
        const c = await navegador.newContext({ locale: 'gl-ES' });
        await c.route('http://localhost:4200/**', (r) => r.abort());
        const p = await c.newPage();
        await p.goto(enderezo);
        await p.locator('#username').fill(email);
        await p.locator('#password').fill(contrasinal);
        await p.locator('#kc-login').click();
        await p.waitForLoadState('load');
        return p;
      };
      const naConta = async (p, que) => {
        await p.waitForURL((u) => u.href.startsWith(conta), { timeout: 30000 })
          .catch(async () => fallo(`${que}: non volveu á conta (${p.url()}): ${(await p.locator('body').innerText()).slice(0, 400)}`));
      };
      const codigo = async (p, segredo, que) => {
        await p.locator('#otp').waitFor({ timeout: 15000 }).catch(async () => fallo(`${que}: Keycloak non pide o código (${p.url()}): ${(await p.locator('body').innerText()).slice(0, 400)}`));
        await p.locator('#otp').fill(await totpNovo(segredo));
        await p.locator('#kc-login').click();
      };
      const sello = Date.now();

      // 1. Persoal co código xa configurado: código malo → erro en galego; o bo → dentro.
      let p = await entrar('xestor-naron@example.org', demoPassword);
      await p.locator('#otp').waitFor({ timeout: 15000 }).catch(() => fallo('xestor-naron: Keycloak non pide o código'));
      const etiqueta = (await p.locator('label[for="otp"]').innerText()).trim();
      if (etiqueta !== 'Código da aplicación de autenticación') fallo(`etiqueta do código «${etiqueta}»`);
      await axeSen(p, 'a pantalla do código');
      await p.locator('#otp').fill(totp(segredoDemo) === '000000' ? '111111' : '000000');
      await p.locator('#kc-login').click();
      await p.waitForLoadState('load');
      const erroCodigo = (await p.locator('body').innerText()).replace(/\s+/g, ' ');
      if (!erroCodigo.includes('O código non é correcto.')) fallo(`código malo sen o erro en galego: ${erroCodigo.slice(0, 300)}`);
      await codigo(p, segredoDemo, 'xestor-naron');
      await naConta(p, 'xestor-naron');
      await p.context().close();
      ok('persoal co código: Keycloak pídeo («Código da aplicación de autenticación», axe sen violacións); un código malo dá «O código non é correcto.»; o bo entra');

      // 2. Veciñanza: sen código.
      p = await entrar('vecino-naron@example.org', demoPassword);
      await naConta(p, 'vecino-naron');
      await p.context().close();
      ok('a veciñanza entra só co contrasinal');

      // Correos do código (Mailpit): o máis novo para ese enderezo e asunto que aínda non se viu.
      const vistos = new Set();
      const correoNovo = async (enderezo, asunto) => {
        for (let i = 0; i < 30; i++) {
          const r = await fetch(`${correoUrl}/api/v1/search?query=${encodeURIComponent(`to:${enderezo}`)}`);
          const m = ((await r.json()).messages || []).find((x) => x.Subject === asunto && !vistos.has(x.ID));
          if (m) { vistos.add(m.ID); return (await (await fetch(`${correoUrl}/api/v1/message/${m.ID}`)).json()); }
          await new Promise((r2) => setTimeout(r2, 500));
        }
        return fallo(`non chegou o correo «${asunto}» a ${enderezo}`);
      };
      const codigoCorreo = async (enderezo) => {
        const m = await correoNovo(enderezo, 'O teu código para entrar');
        const c = /^\s*(\d{6})\s*$/m.exec(m.Text)?.[1];
        if (!c) fallo(`o correo do código sen as 6 cifras: ${m.Text.slice(0, 300)}`);
        if (/furar/i.test(m.Text.replace(/https?:\/\/\S+/g, ''))) fallo('o correo do código di «Furar»');
        return c;
      };
      const tipos = async (email) => {
        const t = await tokenAdmin();
        const [u] = await (await fetch(`${url}/auth/admin/realms/furar/users?email=${encodeURIComponent(email)}&exact=true`, { headers: { Authorization: `Bearer ${t}` } })).json();
        const cs = await (await fetch(`${url}/auth/admin/realms/furar/users/${u.id}/credentials`, { headers: { Authorization: `Bearer ${t}` } })).json();
        return cs.map((c) => c.type).filter((x) => x !== 'password').sort().join(' ');
      };
      // A escolla: a aplicación primeiro, marcada e recomendada; despois o correo.
      const escolla = async (p2, que) => {
        await p2.locator('#furar-second-factor-choice').waitFor({ timeout: 15000 })
          .catch(async () => fallo(`${que}: sen a escolla do segundo factor (${p2.url()}): ${(await p2.locator('body').innerText()).slice(0, 400)}`));
        const opcions = (await p2.locator('#furar-second-factor-choice .pf-v5-c-radio__label').allInnerTexts()).map((t) => t.replace(/\s+/g, ' ').trim());
        if (opcions.length !== 2 || !opcions[0].startsWith('Aplicación de autenticación') || !opcions[0].includes('Recomendado')
          || opcions[1] !== 'Código por correo electrónico') fallo(`${que}: opcións da escolla ${JSON.stringify(opcions)}`);
        if (!(await p2.locator('#furar-method-app').isChecked())) fallo(`${que}: a aplicación non vén marcada`);
        const texto = await p2.locator('body').innerText();
        if (/freeotp|google|microsoft|aegis|furar/i.test(texto)) fallo(`${que}: a escolla nomea aplicacións ou Furar: ${texto.slice(0, 400)}`);
      };

      // «Cambiar de método» na cabeceira, na fila do selector de idioma (á esquerda; o idioma, á dereita).
      const volverNaCabeceira = async (p2, que) => {
        const cabeceira = p2.locator('.pf-v5-c-login__main-header');
        if (!(await cabeceira.locator('.furar-header-action #furar-second-factor-back button').count())) fallo(`${que}: sen «Cambiar de método» na cabeceira`);
        const volver = await cabeceira.locator('.furar-header-action').boundingBox();
        const idioma = await cabeceira.locator('.pf-v5-c-login__main-header-utilities').boundingBox();
        const tit = await p2.locator('#kc-page-title').boundingBox();
        if (!volver || !idioma || !tit || Math.abs((volver.y + volver.height / 2) - (idioma.y + idioma.height / 2)) > 12
          || volver.x >= idioma.x || volver.y + volver.height > tit.y) fallo(`${que}: «Cambiar de método» fóra da fila do idioma`);
        const texto = (await p2.locator('#furar-second-factor-back button').innerText()).trim();
        if (texto !== 'Cambiar de método') fallo(`${que}: o botón de volver di «${texto}»`);
      };

      // 3. Persoal sen método: a escolla; coa aplicación, a alta (exemplos da política do realm),
      // «Cambiar de método» volve á escolla e, no seguinte acceso, o código.
      const novo = `persoal-sf-${sello}@example.org`;
      await crear(novo);
      p = await entrar(novo, contrasinalSf);
      await escolla(p, 'persoal sen método');
      await axeSen(p, 'a escolla do segundo factor');
      await p.locator('#furar-second-factor-continue').click();
      await p.locator('#kc-totp-settings-form').waitFor({ state: 'attached', timeout: 15000 }).catch(() => fallo('persoal sen código: sen a alta da aplicación'));
      const titulo = (await p.locator('#kc-page-title').innerText()).trim();
      if (titulo !== 'Verificación en dous pasos') fallo(`título da alta «${titulo}»`);
      // Ao chegar, só «Que móbil tes?» e o selector (iPhone | Android), sen ningún marcado.
      const movil = (cal) => p.locator(`button[data-phone="${cal}"]`);
      if ((await movil('ios').getAttribute('aria-pressed')) !== 'false' || (await movil('android').getAttribute('aria-pressed')) !== 'false') fallo('a alta xa trae un móbil marcado');
      if (await p.locator('#totp').isVisible()) fallo('a alta mostra o campo antes de escoller o móbil');
      await axeSen(p, 'a alta da aplicación, sen escoller');
      await movil('ios').click();
      if ((await movil('ios').getAttribute('aria-pressed')) !== 'true') fallo('iPhone escollido sen aria-pressed');
      const textoIos = (await p.locator('.furar-totp__after').innerText()).replace(/\s+/g, ' ');
      if (!textoIos.includes('Non tes que instalar nada: o iPhone garda o código na app Contraseñas.') || !textoIos.includes('Abre a cámara do iPhone.')
        || /Google Play/.test(textoIos)) fallo(`a alta para iPhone: «${textoIos.slice(0, 400)}»`);
      if (!textoIos.includes('Xérao o móbil e cambia cada 30 segundos; non o enviamos nós.') || !textoIos.includes('Non recargues ata rematar: o QR cambiaría.')) fallo('a alta sen as dúas frases curtas');
      await movil('android').click();
      const exemplos = (await p.locator('#kc-totp-supported-apps').innerText()).replace(/\s+/g, ' ').trim();
      if (!/^Instala unha destas desde Google Play: .+ ou .+\.$/.test(exemplos)
        || !/FreeOTP/.test(exemplos) || !/Google Authenticator/.test(exemplos)) fallo(`a alta sen as aplicacións da política: «${exemplos}»`);
      const textoAlta = await p.locator('body').innerText();
      if (/aegis|furar/i.test(textoAlta) || /Abre a cámara do iPhone/.test(textoAlta)) fallo(`a alta para Android: ${textoAlta.slice(0, 400)}`);
      if (!(await p.locator('#kc-totp-secret-qr-code').count()) || !(await p.locator('#mode-manual').count())) fallo('a alta sen o QR ou sen «Non podes escanear o código?»');
      await volverNaCabeceira(p, 'alta da aplicación');
      await axeSen(p, 'a alta da aplicación');
      await p.locator('#furar-second-factor-back button').click();
      await escolla(p, 'volver desde a alta da aplicación');
      if ((await tipos(novo)) !== '') fallo(`volver desde a alta gardou credenciais: ${await tipos(novo)}`);
      await p.locator('#furar-second-factor-continue').click();
      await p.locator('#kc-totp-settings-form').waitFor({ state: 'attached', timeout: 15000 }).catch(() => fallo('tras volver, sen a alta da aplicación'));
      // O móbil escollido lémbrase na sesión do navegador: xa vén aberta en Android.
      if ((await p.locator('button[data-phone="android"]').getAttribute('aria-pressed')) !== 'true') fallo('tras volver, a alta esqueceu o móbil escollido');
      const segredo = await p.locator('#totpSecret').inputValue();
      await p.locator('#totp').fill(await totpNovo(segredo));
      await p.locator('#saveTOTPBtn').click();
      await naConta(p, 'alta do código');
      await p.context().close();
      p = await entrar(novo, contrasinalSf);
      await codigo(p, segredo, 'persoal tras a alta');
      await naConta(p, 'persoal tras a alta');
      await p.context().close();
      if ((await tipos(novo)) !== 'otp') fallo(`coa aplicación, credenciais ${await tipos(novo)}`);
      ok('persoal sen método: a escolla (aplicación primeiro, marcada e «Recomendado»; código por correo; axe) e, coa aplicación, a alta cun QR (que é, os exemplos da política, «Cambiar de método» na fila do idioma, que volve á escolla sen gardar nada; axe); o seguinte acceso pide o código');

      // 3b. O código por correo: escollelo, un malo dá o erro, pedir outro xa dá a espera, o bo entra
      // e garda o método; no seguinte acceso, o código polo correo outra vez (sen escolla nin aplicación).
      const porCorreo = `persoal-correo-${sello}@example.org`;
      await crear(porCorreo);
      p = await entrar(porCorreo, contrasinalSf);
      await escolla(p, 'código por correo');
      await p.locator('#furar-method-email').check();
      await p.locator('#furar-second-factor-continue').click();
      await p.locator('#code').waitFor({ timeout: 15000 }).catch(async () => fallo(`código por correo: sen a pantalla do código: ${(await p.locator('body').innerText()).slice(0, 400)}`));
      const tituloCorreo = (await p.locator('#kc-page-title').innerText()).trim();
      if (tituloCorreo !== 'Código por correo') fallo(`título da pantalla do código por correo «${tituloCorreo}»`);
      const enviado = (await p.locator('#furar-email-code-sent').innerText()).replace(/\s+/g, ' ').trim();
      if (enviado !== `Mandámosche un código de 6 cifras a ${porCorreo}. Vale 5 minutos.`) fallo(`texto da pantalla do código: «${enviado}»`);
      await volverNaCabeceira(p, 'código por correo');
      await axeSen(p, 'a pantalla do código por correo');
      // «Cambiar de método»: volve á escolla; escoller outra vez o correo manda outro código.
      await codigoCorreo(porCorreo);
      await p.locator('#furar-second-factor-back button').click();
      await escolla(p, 'volver desde o código por correo');
      await p.locator('#furar-method-email').check();
      await p.locator('#furar-second-factor-continue').click();
      await p.locator('#code').waitFor({ timeout: 15000 }).catch(() => fallo('tras volver, sen a pantalla do código por correo'));
      const c1 = await codigoCorreo(porCorreo);
      await p.locator('#code').fill(c1 === '000000' ? '111111' : '000000');
      await p.locator('#furar-email-code-submit').click();
      await p.waitForLoadState('load');
      if (!(await p.locator('body').innerText()).includes('O código non é correcto.')) fallo('código por correo malo sen «O código non é correcto.»');
      await p.locator('#furar-email-code-resend').click();
      await p.waitForLoadState('load');
      if (!/Agarda \d+ segundos para pedir outro código\./.test(await p.locator('body').innerText())) fallo('pedir outro código xa non dá a espera');
      await p.locator('#code').fill(c1);
      await p.locator('#furar-email-code-submit').click();
      await naConta(p, 'alta do código por correo');
      await p.context().close();
      if ((await tipos(porCorreo)) !== 'furar-email-code') fallo(`co correo, credenciais «${await tipos(porCorreo)}»`);
      p = await entrar(porCorreo, contrasinalSf);
      await p.locator('#code').waitFor({ timeout: 15000 }).catch(async () => fallo(`código por correo: o seguinte acceso non o pide: ${(await p.locator('body').innerText()).slice(0, 400)}`));
      if (await p.locator('#otp, #furar-second-factor-choice').count()) fallo('código por correo: o seguinte acceso pide a aplicación ou a escolla');
      if (await p.locator('#furar-second-factor-back').count()) fallo('código por correo: no acceso (non na alta) sae «Cambiar de método»');
      await p.locator('#code').fill(await codigoCorreo(porCorreo));
      await p.locator('#furar-email-code-submit').click();
      await naConta(p, 'acceso co código por correo');
      await p.context().close();
      ok('código por correo: «Mandámosche un código de 6 cifras… Vale 5 minutos.» (axe), «Cambiar de método» volve á escolla, chega a Mailpit, un malo dá «O código non é correcto.», pedir outro xa dá a espera, o bo entra e garda furar-email-code; o seguinte acceso pide outro código polo correo');

      // 4. Desde unha invitación válida para ese correo: pídeo aínda que a API diga que non.
      const convidada = `convidada-sf-${sello}@example.org`;
      await crear(convidada);
      p = await entrar(convidada, contrasinalSf, `${url}/auth/realms/furar/protocol/openid-connect/auth?client_id=furar-web&response_type=code&scope=openid`
        + `&redirect_uri=${encodeURIComponent('http://localhost:4200/')}&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM`
        + `&code_challenge_method=S256&ui_locales=gl&prompt=login&login_hint=${encodeURIComponent(convidada)}&furar_invitation=${encodeURIComponent(probaConvite(convidada))}`);
      await escolla(p, 'acceso desde a invitación');
      await p.context().close();
      ok('acceso desde unha invitación válida: a escolla e a alta do segundo factor antes de volver á web');

      // 5. «Esqueciches o contrasinal?»: a ligazón do correo e despois o código do método de cada
      // quen (a aplicación ou o do correo), antes do contrasinal novo.
      const restablecer = async (email, darFactor, que) => {
        const q = await (await navegador.newContext({ locale: 'gl-ES' })).newPage();
        await q.goto(`${conta}?kc_locale=gl`);
        await q.locator('a[href*="login-actions/reset-credentials"]').first().click();
        await q.locator('#username').fill(email);
        await q.locator('input[type=submit], button[type=submit]').first().click();
        await q.waitForLoadState('load');
        const html = (await correoNovo(email, 'Cambia o teu contrasinal')).HTML;
        const ligazon = /href="([^"]+\/login-actions\/action-token\?[^"]+)"/.exec(html)?.[1]?.replaceAll('&amp;', '&');
        if (!ligazon) fallo(`${que}: o correo do contrasinal sen a ligazón`);
        await q.goto(ligazon);
        if (await q.locator('#password-new').count()) fallo(`${que}: o contrasinal novo sen o código (o correo salta o segundo factor)`);
        await darFactor(q);
        await q.locator('#password-new').waitFor({ timeout: 15000 }).catch(() => fallo(`${que}: tras o código, sen o contrasinal novo`));
        await q.context().close();
      };
      await restablecer('xestor-ferrol@example.org', (q) => codigo(q, segredoDemo, 'Esqueciches o contrasinal? (aplicación)'), '«Esqueciches o contrasinal?» coa aplicación');
      await restablecer(porCorreo, async (q) => {
        await q.locator('#code').waitFor({ timeout: 15000 }).catch(() => fallo('«Esqueciches o contrasinal?» co correo: non pide o código do correo'));
        await q.locator('#code').fill(await codigoCorreo(porCorreo));
        await q.locator('#furar-email-code-submit').click();
      }, '«Esqueciches o contrasinal?» co código por correo');
      ok('«Esqueciches o contrasinal?»: despois da ligazón do correo, o código do método de cada quen (aplicación ou correo); despois, o contrasinal novo');

      // 6. API parada: a veciñanza non entra só co contrasinal (o erro); o persoal dá o código.
      await fetch(`${apiSimulada}/parar`, { method: 'POST' });
      try {
        p = await entrar('vecino-naron@example.org', demoPassword);
        const caido = (await p.locator('body').innerText()).replace(/\s+/g, ' ');
        if (!caido.includes('Agora non se pode comprobar a túa conta. Proba outra vez nuns minutos.')) fallo(`API parada: a veciñanza non ve o erro: ${caido.slice(0, 300)}`);
        if (p.url().startsWith(conta) && !p.url().includes('/protocol/') && !p.url().includes('/login-actions/')) fallo('API parada: a veciñanza entrou só co contrasinal');
        await axeSen(p, 'o erro sen API');
        await p.context().close();
        p = await entrar('xestor-naron@example.org', demoPassword);
        await codigo(p, segredoDemo, 'API parada, persoal');
        await naConta(p, 'API parada, persoal');
        await p.context().close();
      } finally {
        await fetch(`${apiSimulada}/arrincar`, { method: 'POST' });
      }
      ok('API parada: «Agora non se pode comprobar a túa conta…» sen entrar só co contrasinal; quen ten o código dáo e entra');
    }
    if (modo === 'clave' || modo === 'todo') {
      if (!apiSimulada) fallo('sen API_SIMULADA');
      // Entrar por Cl@ve: cada vez nun contexto novo, sen sesión no IdP nin en Furar.
      const porClave = async () => {
        const p = await navegador.newPage({ locale: 'gl-ES' });
        await p.goto(conta);
        await p.locator('#social-clave-simulada').click();
        await p.waitForURL(/\/realms\/clave-simulada\//);
        await p.locator('#username').fill(nif);
        await p.locator('#password').fill(demoPassword);
        await p.locator('#kc-login').click();
        return p;
      };
      // A API simulada di que a veciñanza non leva código: furar-post-broker remata en
      // allow-access-authenticator, así que entra (sen el, o fluxo quedaba sen ningún paso feito).
      let p = await porClave();
      ok('o botón «Cl@ve (simulada)» leva ao IdP SAML');
      await p.waitForURL((u) => u.href.startsWith(conta) && u.searchParams.has('code'), { timeout: 30000 })
        .catch(async () => fallo(`non volveu á conta: ${p.url()}\n${(await p.content()).slice(0, 1500)}`));
      ok('volta a Furar con sesión (resposta SAML asinada e validada)');
      await p.context().close();
      // API parada (ADR-0029): sen saber se é persoal, a veciñanza sen código non entra por Cl@ve.
      await fetch(`${apiSimulada}/parar`, { method: 'POST' });
      try {
        p = await porClave();
        await p.locator('body').getByText('Agora non se pode comprobar a túa conta.', { exact: false }).waitFor({ timeout: 30000 })
          .catch(() => null);
        const caido = (await p.locator('body').innerText()).replace(/\s+/g, ' ');
        if (!caido.includes('Agora non se pode comprobar a túa conta. Proba outra vez nuns minutos.')) fallo(`Cl@ve coa API parada: a veciñanza non ve o erro: ${p.url()} ${caido.slice(0, 300)}`);
        if (p.url().startsWith(conta) && new URL(p.url()).searchParams.has('code')) fallo('Cl@ve coa API parada: a veciñanza entrou');
        await p.context().close();
      } finally {
        await fetch(`${apiSimulada}/arrincar`, { method: 'POST' });
      }
      ok('Cl@ve coa API parada: «Agora non se pode comprobar a túa conta…», sen entrar');
    }
  } finally {
    await navegador.close();
  }
})().catch((e) => fallo(e.message));
FIN

titulo "Navegador"
saida=$(FURAR_DEMO_PASSWORD="$FURAR_DEMO_PASSWORD" PROBA_TOTP_SECRET="$segredo_totp" API_SIMULADA="http://$enderezo_api:$porto_api" PROOF_SECRET="$segredo_proba" PW="$FURAR_RAIZ/web/node_modules/playwright" AXE="$FURAR_RAIZ/web/node_modules/@axe-core/playwright" \
  node "$tmp/proba.cjs" "$modo" "$url" "$web_proba" "$camino_privacidade" "$nif" "$temporal" "$comun" "$correo_url" "$marca" 2>"$tmp/erro-navegador") \
  || { echo "$saida"; docker logs --tail 30 "$nome"; cat "$tmp/erro-navegador" >&2; falla "proba no navegador (o paso que fallou, xusto enriba)"; }
cat "$tmp/erro-navegador" >&2
grep -v '^USUARIO ' <<<"$saida"

# O token de master caduca aos 60 s: renóvase despois do navegador.
adm=$(curl -sf -d grant_type=password -d client_id=admin-cli -d username=admin -d password=admin-proba-identidade \
  "$url/auth/realms/master/protocol/openid-connect/token" | jq -r .access_token)

esperar "nome do realm importado" "$(kc '' | jq -r .displayName)" "$marca"
esperar "grupos do realm (sen plataforma: a administración está na base de Furar, ADR-0028)" "$(kc groups | jq -r '[.[].name] | join(" ")')" ""
api_cid=$(kc 'clients?clientId=furar-api' | jq -r '.[0].id')
esperar "roles de furar-api (ningún)" "$(kc "clients/$api_cid/roles" | jq -r '[.[].name] | join(" ")')" ""
if [[ $modo == rexistro || $modo == todo ]]; then
  esperar "fluxo de rexistro (ADR-0027)" "$(kc '' | jq -r .registrationFlow)" furar-registration
  esperar "pasos do fluxo de rexistro (sen «Terms and conditions»)" \
    "$(kc authentication/flows/furar-registration/executions | jq -r '[.[] | select(.requirement != "DISABLED") | .providerId // .displayName] | join(" ")')" \
    "registration-page-form registration-user-creation registration-password-action furar-second-factor-registration furar-invitation-email"
  esperar "acción TERMS_AND_CONDITIONS" "$(kc authentication/required-actions/TERMS_AND_CONDITIONS | jq -r .enabled)" false
  u=$(sed -n 's/^USUARIO //p' <<<"$saida")
  esperar "usuario rexistrado en Keycloak, co correo como usuario" "$(kc "users?email=$u@example.org&exact=true" | jq -r '.[0].username')" "$u@example.org"
  esperar "rexistro sen invitación: o correo segue sen verificar e Keycloak pide verificalo" \
    "$(kc "users?email=$u@example.org&exact=true" | jq -r '.[0] | "\(.emailVerified) \(.requiredActions | index("VERIFY_EMAIL") != null)"')" "false true"
fi
if [[ $modo == clave || $modo == todo ]]; then
  titulo "O NIF non entra en Furar"
  usuario=$(kc "users?idpAlias=clave-simulada" | jq '.[0]')
  [[ $usuario != null ]] || falla "non se creou o usuario federado"
  id=$(jq -r .id <<<"$usuario")
  completo=$(kc "users/$id")
  federado=$(kc "users/$id/federated-identity")
  esperar "correo e nome desde o IdP" "$(jq -r '"\(.email) \(.firstName)"' <<<"$completo")" "cidada.clave@example.org Uxía"
  esperar "o correo é o usuario federado" "$(jq -r .username <<<"$completo")" cidada.clave@example.org
  nif_min=$(tr '[:upper:]' '[:lower:]' <<<"$nif")
  if grep -qiE "$nif|$nif_min" <<<"$completo$federado"; then
    falla "o NIF aparece no usuario de Furar ou na ligazón federada: $completo $federado"
  fi
  ok "usuario $(jq -r .username <<<"$completo") sen NIF (nin nome de usuario, nin atributos, nin ligazón federada)"
fi
echo "proba-identidade-keycloak ($modo) OK"
