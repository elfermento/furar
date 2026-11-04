<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Proba técnica D4-02: autenticación escalonada (LoA) e códigos de recuperación en Keycloak 26.8.1

**Data:** 2026-10-28 · **Tarefa:** D4-02 do [plan do dominio 4](../plan/dominio-4-persoal.md) (§1.2.9) · **Decisión:** [ADR-0027](../adr/0027-persoal-e-permisos-fase-1.md) punto 9, [ADR-0007](../adr/0007-seguridade-pechada.md).

Esta nota non é unha nota de versión: é a proba previa ao cambio do realm. O cambio aplícao D4-11.

**Actualización (2026-10-29):** a orde do formulario cambiou a nivel 1 → plataforma → nivel 2, cun subfluxo «nivel 2 xa dado». O JSON de abaixo xa non é o vixente; véxase [`2026-10-29-orde-do-fluxo-e-segundo-factor.md`](2026-10-29-orde-do-fluxo-e-segundo-factor.md).

**Conclusión:** LoA e os códigos de recuperación funcionan en 26.8.1 sen activar ningunha *feature*. Pero a forma do fluxo do plan non vale tal como está escrita, e hai tres cousas que cambian D4-05, D4-11 e D4-13:

1. Co OTP do rol `furar-2fa` dentro do nivel 1, a plataforma mete o OTP **dúas veces** cando se pide o nivel 2. O fluxo validado pide ese OTP **despois** dos niveis, e só se aínda non se deu ningún segundo factor.
2. Se se configura o OTP no propio acceso, o token sae con `acr` = `furar-2fa` pero `amr` = `["pwd"]`, sen `otp`, e segue así ao renovar e coa cookie. Coa regra actual da API (só mira `amr`), a persoa entraría nun bucle de step-up.
3. O `auth_time` cambia co step-up. `login_hint` enche o campo de **usuario** do rexistro, non o do correo.

## Como se probou

- A imaxe é a do [`Dockerfile`](../../deploy/keycloak/Dockerfile) (`quay.io/keycloak/keycloak:26.8.1@sha256:a78d6b79…dd6b`). Arrincou en modo produción (`start`, non `start-dev`), coas variables de execución do `Dockerfile` (`KC_CACHE=local`, `KC_HTTP_RELATIVE_PATH=/auth`, `KC_FEATURES_DISABLED=impersonation`), base `dev-file`, `-m 1g` e o tema `furar` montado. Usou ~540 MB e borrouse ao acabar. Non había outro Docker en marcha.
- O realm é `deploy/keycloak/import/furar-realm.json`, co fluxo desta nota e os marcadores `${…}` substituídos. Probouse co cliente `furar-web` (código + PKCE S256) e `FURAR_PUBLIC_URL=http://localhost:18099`.
- Todo vai con `curl` e un xarro de cookies por «navegador», sen Playwright: todas as pantallas son formularios de Keycloak. O TOTP calcúlase con `openssl` (RFC 6238, como `totp()` en `scripts/proba-producion.sh`). As persoas son de proba, con contrasinais e segredos aleatorios; os `sub` aparecen recortados.
- Unha primeira volta comparou dous realms no mesmo Keycloak: `furar-plan`, coa forma do plan, e `furar`, coa forma validada. Despois houbo unha volta limpa só coa forma validada, creada desde o JSON exacto desta nota. Desa volta saen as evidencias.

## Resultado

| # | Comprobación | Resultado | Evidencia (claims do access token) |
|---|---|---|---|
| 1 | `acr_values=furar-2fa` obriga a configurar o TOTP a quen non o ten | **OK** | Tras o contrasinal aparece «Configurar o autenticador do móbil» (`CONFIGURE_TOTP`) |
| 1 | Co TOTP xa configurado, `amr` leva `otp` tras o acceso e tras renovar | **OK** | Acceso: `{"acr":"furar-2fa","amr":["pwd","otp"]}`; renovar: igual |
| 1 | Step-up (nivel 1 e despois `furar-2fa` na mesma sesión) | **OK** | Só pide o OTP: `{"acr":"furar-2fa","amr":["pwd","otp"]}`, tamén ao renovar |
| 1′ | Primeiro acceso que **configura** o TOTP | **KO para `amr`** | `{"acr":"furar-2fa","amr":["pwd"]}`; igual ao renovar e ao volver pedir `furar-2fa` coa cookie. Véxase o achado A |
| 2 | Código de recuperación → `amr` `mfa` | **OK** (co autenticador `furar-amr-mfa`) | «Probar outro xeito» → «Código de recuperación n.º 1» → `{"acr":"furar-2fa","amr":["pwd","mfa"]}`; renovar: igual. Un código xa usado rexéitase |
| 3 | `auth_time` no access token | **OK** | Está en todos os tokens. Non cambia co SSO nin ao renovar; **cambia co step-up** (achado B) |
| 4 | `kc_action=CONFIGURE_RECOVERY_AUTHN_CODES` como acción iniciada pola aplicación | **OK, sen `--features`** | Cunha sesión aberta vai directo á pantalla dos 12 códigos (en galego). Volve a `redirect_uri` con `kc_action=CONFIGURE_RECOVERY_AUTHN_CODES&kc_action_status=success`; credenciais: `["password","otp","recovery-authn-codes"]`. `serverinfo`: `RECOVERY_CODES DEFAULT enabled=true`, `STEP_UP_AUTHENTICATION DEFAULT enabled=true` |
| 5 | `prompt=create` + `login_hint` → rexistro co correo posto | **Parcial** | Abre o rexistro (`login-actions/registration`, coa cláusula `#furar-privacy-notice`), pero `username=«persoal-novo@example.org» email=«»`. Con `registrationEmailAsUsername: true` énchese `email` (probado). Achado C |
| 6 | A plataforma (rol `furar-2fa`) non mete o OTP dúas veces ao pedir o nivel 2 | **KO coa forma do plan, OK coa validada** | Forma do plan: o OTP pídese 2 veces, `amr` `["pwd","otp","otp"]`. Forma validada: 1 vez, `{"acr":"furar-2fa","amr":["pwd","otp"],"furar_roles":["*:PLATFORM_ADMIN"]}`. Sen `acr_values`: 1 vez, `{"acr":"furar-pwd","amr":["pwd","otp"]}` |
| 7 | `KC_CACHE=local` segue aplicándose, sen JGroups | **OK** | `KC_CACHE=local`; 0 liñas `ISPN000078\|JGroups` no log; no volcado de fíos non hai ningún de JGroups; portos en escoita `1F92` (8082), `2328` (9000) e un efémero; nin 7800 nin 57800 (`1E78`/`E1C8`, o que mira D31) |

A veciñanza (sen rol e sen `acr_values`) segue entrando só co contrasinal: `{"acr":"furar-pwd","amr":["pwd"]}`.

## Achados

**A. Configurar o OTP no acceso non pon `otp` no `amr`.** Se a persoa configura o OTP no propio acceso (acción requirida desde a execución `auth-otp-form` REQUIRED), a execución queda en estado *setup required*, non en *success*. O mapeador `oidc-amr-mapper` só conta as execucións rematadas con éxito. O nivel si queda en 2 (`acr` `furar-2fa`, con `loa-max-age` 36000), polo que volver pedir `furar-2fa` resólvese coa cookie e o `amr` segue sen `otp`. Hoxe pasa o mesmo no primeiro acceso da plataforma, pero aí a web relanza con `prompt=login`. Hai dúas saídas e probáronse as dúas:
- **(recomendada) A API acepta tamén `acr` = `furar-2fa`** como segundo factor, ademais de `amr` con `otp` ou `mfa`. O nivel 2 só se alcanza polo subfluxo do nivel 2: OTP, código ou alta do OTP cun código válido. Configurar o OTP cun código correcto proba que a persoa ten o dispositivo. Non engade ningún paso para quen entra por primeira vez.
- **(alternativa) Na web:** se despois do step-up a API segue respondendo `auth.step-up-required`, relánzase unha vez con `prompt=login&acr_values=furar-2fa`. Probado: pide contrasinal e OTP e sae `{"acr":"furar-2fa","amr":["pwd","otp"]}`. Custa un contrasinal e un código máis no primeiro acceso, e a xanela antibucle de 5 min de `SessionService` ten que deixar este segundo intento.

**B. O `auth_time` é o da última autenticación, non o do inicio da sesión.** Entrar no nivel 1 dá `auth_time` = T; co SSO e ao renovar segue en T; o step-up a `furar-2fa` pásao a T′ (o momento do OTP). Para D4-05, «último acceso» rexistraría dous `staff.sign-in` se a persoa entra no nivel 1 e despois fai step-up. Non os rexistra se a web pide `acr_values=furar-2fa` xa no acceso á xestión.

**C. `login_hint` no rexistro enche `username`.** O realm ten `registrationEmailAsUsername: false`. Con `true` énchese o correo, pero iso cambia o rexistro de toda a veciñanza (deixaría de escoller nome de usuario), e iso é unha decisión de produto, así que non se toca. Mentres tanto, a pantalla da invitación pode dicir «rexístrate co correo ao que che chegou a invitación». A aceptación xa compara o correo verificado co invitado.

**D. As alternativas sen credencial non levan a configurar nada.** Coa primeira forma probada (nivel 2 = `auth-otp-form` ALTERNATIVE | `auth-recovery-authn-code-form` ALTERNATIVE), a persoa sen OTP remata en «Sentímolo…» (`AuthenticationFlowException`, `invalid_user_credentials`). Keycloak só ofrece configurar unha credencial cando a execución é REQUIRED. Por iso o nivel 2 leva dous subfluxos: «configurado» (`conditional-user-configured`: OTP ou código) e «alta» (`conditional-credential` «non usou `otp` nin `recovery-authn-codes`»: `auth-otp-form` REQUIRED).

**E. A plataforma.** Co OTP do rol dentro do nivel 1 (a forma do plan), pedir o nivel 2 executa outro `auth-otp-form` e o OTP pídese dúas veces. Na forma validada, o subfluxo «plataforma» vai despois dos niveis e só corre se aínda non se usou `otp` nin `recovery-authn-codes` neste acceso (`conditional-credential`). Así hai un só OTP, tanto con `acr_values` como sen el. Se a plataforma xa entrou sen `acr_values` (nivel 1 + OTP do rol) e despois se lle pide `furar-2fa`, Keycloak volve pedir o OTP, porque ese OTP non subiu o nivel. A web non debe pedir step-up a quen xa trae `otp` ou `mfa`, e coa regra de D4-13 non o fai. A plataforma tamén pode usar un código de recuperación.

**F. Textos.** As pantallas novas saen en galego: «Configurar o autenticador do móbil», «Códigos de recuperación», «Gardei estes códigos nun lugar seguro», «Probar outro xeito», «Acceder cun código de recuperación», «Código de recuperación n.º 1». Queda en inglés «Warning alert:», o texto para lectores de pantalla do aviso de PatternFly na páxina dos códigos. Tamén hai que revisar o texto do ficheiro que descarga «Descargar». Ambos van para X09 en D4-11.

**G. `amr` pode repetir valores** (`["pwd","otp","otp"]` coa forma do plan). A API mira se contén o valor, así que non afecta.

## Fluxo validado (JSON exacto)

Substitúe `browserFlow`, `authenticationFlows` e `authenticatorConfig` do realm de produción. `acr.loa.map` súmase aos `attributes` que xa hai (`adminEventsExpiration`). O cliente `furar-web` non cambia: o *scope* `acr` vai por defecto e o `acr` sae no access token. Os mapeadores `furar-amr` e `audience-furar-api` tampouco cambian.

```json
{
  "attributes": {"acr.loa.map": "{\"furar-pwd\":1,\"furar-2fa\":2}"},
  "browserFlow": "furar-browser",
  "authenticationFlows": [
    {
      "alias": "furar-browser",
      "description": "Acceso web: cookie ou formulario. Niveis (acr.loa.map): furar-pwd = 1, furar-2fa = 2 (ADR-0007, ADR-0027)",
      "providerId": "basic-flow",
      "topLevel": true,
      "builtIn": false,
      "authenticationExecutions": [
        {"authenticator": "auth-cookie", "authenticatorFlow": false, "requirement": "ALTERNATIVE", "priority": 10, "userSetupAllowed": false},
        {"authenticatorFlow": true, "requirement": "ALTERNATIVE", "priority": 20, "flowAlias": "furar-browser formulario", "userSetupAllowed": false}
      ]
    },
    {
      "alias": "furar-browser formulario",
      "description": "Nivel 1, nivel 2 se a aplicación pide acr_values=furar-2fa e, para o rol furar-2fa, segundo factor se aínda non o deu",
      "providerId": "basic-flow",
      "topLevel": false,
      "builtIn": false,
      "authenticationExecutions": [
        {"authenticatorFlow": true, "requirement": "CONDITIONAL", "priority": 10, "flowAlias": "furar-browser nivel 1", "userSetupAllowed": false},
        {"authenticatorFlow": true, "requirement": "CONDITIONAL", "priority": 20, "flowAlias": "furar-browser nivel 2", "userSetupAllowed": false},
        {"authenticatorFlow": true, "requirement": "CONDITIONAL", "priority": 30, "flowAlias": "furar-browser plataforma", "userSetupAllowed": false}
      ]
    },
    {
      "alias": "furar-browser nivel 1",
      "description": "Usuario e contrasinal (amr pwd)",
      "providerId": "basic-flow",
      "topLevel": false,
      "builtIn": false,
      "authenticationExecutions": [
        {"authenticator": "conditional-level-of-authentication", "authenticatorFlow": false, "requirement": "REQUIRED", "priority": 10, "userSetupAllowed": false, "authenticatorConfig": "furar-loa-1"},
        {"authenticator": "auth-username-password-form", "authenticatorFlow": false, "requirement": "REQUIRED", "priority": 20, "userSetupAllowed": false, "authenticatorConfig": "furar-amr-pwd"}
      ]
    },
    {
      "alias": "furar-browser nivel 2",
      "description": "Segundo factor: OTP (amr otp) ou código de recuperación (amr mfa); se non ten ningún, configura o OTP",
      "providerId": "basic-flow",
      "topLevel": false,
      "builtIn": false,
      "authenticationExecutions": [
        {"authenticator": "conditional-level-of-authentication", "authenticatorFlow": false, "requirement": "REQUIRED", "priority": 10, "userSetupAllowed": false, "authenticatorConfig": "furar-loa-2"},
        {"authenticatorFlow": true, "requirement": "CONDITIONAL", "priority": 20, "flowAlias": "furar-browser nivel 2 configurado", "userSetupAllowed": false},
        {"authenticatorFlow": true, "requirement": "CONDITIONAL", "priority": 30, "flowAlias": "furar-browser nivel 2 alta", "userSetupAllowed": false}
      ]
    },
    {
      "alias": "furar-browser nivel 2 configurado",
      "description": "Ten OTP ou códigos: pide un dos dous",
      "providerId": "basic-flow",
      "topLevel": false,
      "builtIn": false,
      "authenticationExecutions": [
        {"authenticator": "conditional-user-configured", "authenticatorFlow": false, "requirement": "REQUIRED", "priority": 10, "userSetupAllowed": false},
        {"authenticator": "auth-otp-form", "authenticatorFlow": false, "requirement": "ALTERNATIVE", "priority": 20, "userSetupAllowed": false, "authenticatorConfig": "furar-amr-otp"},
        {"authenticator": "auth-recovery-authn-code-form", "authenticatorFlow": false, "requirement": "ALTERNATIVE", "priority": 30, "userSetupAllowed": false, "authenticatorConfig": "furar-amr-mfa"}
      ]
    },
    {
      "alias": "furar-browser nivel 2 alta",
      "description": "Non ten ningún nin o usou: obriga a configurar o OTP",
      "providerId": "basic-flow",
      "topLevel": false,
      "builtIn": false,
      "authenticationExecutions": [
        {"authenticator": "conditional-credential", "authenticatorFlow": false, "requirement": "REQUIRED", "priority": 10, "userSetupAllowed": false, "authenticatorConfig": "furar-sen-segundo-factor"},
        {"authenticator": "auth-otp-form", "authenticatorFlow": false, "requirement": "REQUIRED", "priority": 20, "userSetupAllowed": false, "authenticatorConfig": "furar-amr-otp"}
      ]
    },
    {
      "alias": "furar-browser plataforma",
      "description": "Rol furar-2fa (só *:PLATFORM_ADMIN) sen segundo factor neste acceso: OTP ou código; así a plataforma non o mete dúas veces",
      "providerId": "basic-flow",
      "topLevel": false,
      "builtIn": false,
      "authenticationExecutions": [
        {"authenticator": "conditional-user-role", "authenticatorFlow": false, "requirement": "REQUIRED", "priority": 10, "userSetupAllowed": false, "authenticatorConfig": "furar-rol-2fa"},
        {"authenticator": "conditional-credential", "authenticatorFlow": false, "requirement": "REQUIRED", "priority": 15, "userSetupAllowed": false, "authenticatorConfig": "furar-sen-segundo-factor"},
        {"authenticatorFlow": true, "requirement": "CONDITIONAL", "priority": 20, "flowAlias": "furar-browser plataforma configurado", "userSetupAllowed": false},
        {"authenticatorFlow": true, "requirement": "CONDITIONAL", "priority": 30, "flowAlias": "furar-browser plataforma alta", "userSetupAllowed": false}
      ]
    },
    {
      "alias": "furar-browser plataforma configurado",
      "description": "Ten OTP ou códigos: pide un dos dous",
      "providerId": "basic-flow",
      "topLevel": false,
      "builtIn": false,
      "authenticationExecutions": [
        {"authenticator": "conditional-user-configured", "authenticatorFlow": false, "requirement": "REQUIRED", "priority": 10, "userSetupAllowed": false},
        {"authenticator": "auth-otp-form", "authenticatorFlow": false, "requirement": "ALTERNATIVE", "priority": 20, "userSetupAllowed": false, "authenticatorConfig": "furar-amr-otp"},
        {"authenticator": "auth-recovery-authn-code-form", "authenticatorFlow": false, "requirement": "ALTERNATIVE", "priority": 30, "userSetupAllowed": false, "authenticatorConfig": "furar-amr-mfa"}
      ]
    },
    {
      "alias": "furar-browser plataforma alta",
      "description": "Non ten ningún nin o usou: obriga a configurar o OTP",
      "providerId": "basic-flow",
      "topLevel": false,
      "builtIn": false,
      "authenticationExecutions": [
        {"authenticator": "conditional-credential", "authenticatorFlow": false, "requirement": "REQUIRED", "priority": 10, "userSetupAllowed": false, "authenticatorConfig": "furar-sen-segundo-factor"},
        {"authenticator": "auth-otp-form", "authenticatorFlow": false, "requirement": "REQUIRED", "priority": 20, "userSetupAllowed": false, "authenticatorConfig": "furar-amr-otp"}
      ]
    }
  ],
  "authenticatorConfig": [
    {"alias": "furar-rol-2fa", "config": {"condUserRole": "furar-2fa", "negateOutput": "false"}},
    {"alias": "furar-amr-pwd", "config": {"default.reference.value": "pwd", "default.reference.maxAge": "36000"}},
    {"alias": "furar-amr-otp", "config": {"default.reference.value": "otp", "default.reference.maxAge": "36000"}},
    {"alias": "furar-amr-mfa", "config": {"default.reference.value": "mfa", "default.reference.maxAge": "36000"}},
    {"alias": "furar-loa-1", "config": {"loa-condition-level": "1", "loa-max-age": "36000"}},
    {"alias": "furar-loa-2", "config": {"loa-condition-level": "2", "loa-max-age": "36000"}},
    {"alias": "furar-sen-segundo-factor", "config": {"credentials": "otp##recovery-authn-codes", "included": "false"}}
  ]
}
```

Cada nivel funciona así:

- **Nivel 1 (`furar-pwd`):** usuario e contrasinal, `amr` `pwd`.
- **Nivel 2 (`furar-2fa`), cando a aplicación pide `acr_values=furar-2fa`:**
  - con OTP ou códigos, un dos dous (`otp` ou `mfa`);
  - sen ningún, alta obrigada do OTP (véxase o achado A).
- **Rol `furar-2fa` (só `*:PLATFORM_ADMIN`):** segundo factor se aínda non o deu neste acceso.
- **`loa-max-age` e `default.reference.maxAge`:** 36000 s, igual que `ssoSessionMaxLifespan`. Durante a sesión, o nivel 2 e o `amr` duran e sobreviven á renovación.

## Plan B (§1.2.9), por se algún día LoA falla

Non fixo falta, porque LoA funciona. O plan B sería así:

- O nivel 1 queda como hoxe.
- Despois do contrasinal, un subfluxo CONDITIONAL «OTP se o ten configurado» (`conditional-user-configured` + `auth-otp-form` ALTERNATIVE | `auth-recovery-authn-code-form` ALTERNATIVE) mantén o OTP do rol `furar-2fa`.
- Despois de aceptar a invitación, a web lanza `kc_action=CONFIGURE_TOTP` e despois `kc_action=CONFIGURE_RECOVERY_AUTHN_CODES` (unha acción por redirección).
- A API segue esixindo `amr`.

O achado A tamén lle afecta: a acción `CONFIGURE_TOTP` non pon `otp` no `amr`. Por iso, despois de configurar, a web tería que relanzar o acceso (`prompt=login`).

## Por que non se aplica aínda ao realm de produción

Déixase para D4-11, por catro motivos:

1. **D48 rompería.** `scripts/proba-producion.sh` espera exactamente 1 `auth-otp-form` en `furar-browser` e o fluxo validado ten 4. D48 precisa a pila completa e reescríbeo D4-11.
2. **Falta unha decisión antes.** Sen a decisión do achado A en D4-05 ou D4-13, a primeira persoa do persoal que configure o OTP queda en step-up.
3. **Rama equivocada.** A rama desta proba (`feat/indicadores`) non é a do persoal.
4. **Mellor nun só cambio.** O realm tamén perde o grupo `persoal` e gaña `requiredActions`, e é mellor cambialo todo nun só cambio coa súa nota de versión.

O demo segue co fluxo `browser` (`overlay.json`) e non lle afecta. `realm-demo.sh` copia os fluxos novos ao realm de demo sen usalos.

## Que cambia no plan

- **D4-05:** acepta `acr` = `furar-2fa` como segundo factor, ademais de `amr` `otp`/`mfa` (achado A, recomendado). Ten un IT cun token `{"acr":"furar-2fa","amr":["pwd"]}`. O último acceso conta co achado B (o `auth_time` cambia co step-up).
- **D4-11:**
  - Usa o JSON desta nota, non a forma literal do §1.2.9.
  - Non fai falta `--features=recovery-codes` no `Dockerfile`: en 26.8.1 xa vén activo.
  - `requiredActions` explícitos `CONFIGURE_TOTP` e `CONFIGURE_RECOVERY_AUTHN_CODES`, que xa veñen activos por defecto pero así quedan escritos.
  - `furar-amr-mfa` validado.
  - D48: a conta de `auth-otp-form` pasa a 4, ou compróbanse os alias dos subfluxos. Casos novos: configurar o OTP con `acr_values` (`acr` `furar-2fa`, sen `otp`), segredo coñecido → `otp`, código → `mfa`, a plataforma cun só OTP.
  - X09: «Warning alert:» e o texto do ficheiro descargado.
- **D4-13:**
  - O step-up pide `acr_values=furar-2fa` sen `prompt=login`. Con D4-05 aceptando o `acr`, non fai falta máis; se non, aplícase a alternativa do achado A.
  - O acceso á xestión pide `furar-2fa` directamente (un só `auth_time`).
  - Para invitar a quen non ten conta: `prompt=create&login_hint=<correo>`. O correo vai no campo de usuario (achado C), e a pantalla tenno que dicir.
  - Despois de aceptar, `kc_action=CONFIGURE_RECOVERY_AUTHN_CODES`. Cunha sesión aberta vai directo á pantalla dos códigos e volve con `kc_action_status=success` (probado). Sen sesión, ou se a persoa cancela (`cancelled`), non se probou.
- **Pregunta de produto (decidida o 2026-10-29: si):** `registrationEmailAsUsername: true` para que o correo saia xa posto no rexistro (achado C). Cambio e paso manual en [`2026-10-29-correo-como-usuario.md`](2026-10-29-correo-como-usuario.md).
