<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# O correo como usuario (`registrationEmailAsUsername`)

**Data:** 2026-10-29 · **Orixe:** P13 do [plan do dominio 4](../plan/dominio-4-persoal.md) (achado C da [nota do 2026-10-28](2026-10-28-loa-codigos-recuperacion.md)) · **Decisión:** [ADR-0027](../adr/0027-persoal-e-permisos-fase-1.md), punto 6.

Esta nota non é unha nota de versión: explica un cambio do realm e como levalo a man a un Keycloak xa instalado. A nota da versión que o inclúa remite aquí.

**Conclusión:** no realm `furar`, o correo é o nome de usuario. O rexistro non pide usuario, a pantalla de acceso pide «Correo electrónico» e a invitación do persoal leva o correo xa posto no rexistro (`login_hint`). As contas que xa existen seguen entrando co seu nome de usuario de antes.

## O cambio

| Opción do realm | Valor | Por que |
|---|---|---|
| `registrationEmailAsUsername` | **`true`** (antes `false`) | O correo é o usuario |
| `loginWithEmailAllowed` | `true` (igual) | Entrar co correo |
| `duplicateEmailsAllowed` | `false` (igual) | Un correo, unha conta: sen isto, o correo non identifica a ninguén |
| `editUsernameAllowed` | `false` (igual) | O usuario cambia só se cambia o correo |

## Como se comporta Keycloak 26.8.1

Comprobado no código da versión fixada (`deploy/keycloak/Dockerfile`):

- **Acceso.** Keycloak busca primeiro por correo, se o texto leva `@`, e despois por nome de usuario. Non mira `registrationEmailAsUsername`. Por iso as contas creadas antes co usuario `xestor-naron` entran tanto con `xestor-naron` como con `xestor-naron@example.org`. Non hai que migrar nada.
- **O nome de usuario pasa a ser o correo** cada vez que Keycloak garda o perfil dunha conta: no rexistro, ao actualizar os datos (acción requirida ou consola da conta) e ao crear ou editar a conta pola API ou pola consola de administración, aínda que se mande outro usuario. Unha conta vella co usuario `xestor-naron` pasa a ser `xestor-naron@example.org` a primeira vez que garda o perfil. O `sub` non cambia, así que Furar non perde nada (o persoal e as reservas van polo `sub`). Desde ese momento entra só co correo.
- **Importar o realm** (`--import-realm`) non pasa polo perfil: os usuarios de demo conservan o seu nome de usuario.
- **Cl@ve e Chave365** (first broker login): a conta nova colle como usuario **o correo que manda o IdP**. O mapeador *Username template importer* (`${ALIAS}.${NAMEID}`) xa non conta para isto, e tampouco renomea a conta en cada acceso (*Sync mode* `force`). O usuario segue sen ser o NIF ([ADR-0019](../adr/0019-autenticacion-publica-clave.md)). Se o IdP non manda correo, a revisión do perfil do first broker login pídeo antes de crear a conta. Se o correo xa ten conta, Keycloak pide entrar con ela antes de ligalas, como antes (`duplicateEmailsAllowed: false`).
- **Tema.** O tema base xa cambia a etiqueta: «Correo electrónico» no acceso, na páxina do contrasinal esquecido e no usuario xa escrito (`template.ftl` de Furar ten a mesma condición). O rexistro non pinta o campo `username`. O campo `email` leva `autocomplete="email"` e a etiqueta «Correo electrónico». O campo do acceso segue sendo `#username`, con `autocomplete="username"`, que é o que esperan os xestores de contrasinais nun formulario de acceso. Furar só cambia textos: o castelán de Keycloak dicía «Email» e «Usuario o email», e as mensaxes de erro falaban do «nome de usuario» (`messages_gl` e `messages_es`).

## Como aplicalo a man

Fai falta en calquera Keycloak instalado antes deste cambio: `--import-realm` non toca un realm que xa existe ([ADR-0015](../adr/0015-despregue.md)). Abre a administración polo túnel ([instalación](../instalacion.md) §5, paso 1) → realm `furar` → *Realm settings* → *Login*:

1. Activa **Email as username**.
2. Deixa como estaban **Login with email** (activado), **Duplicate emails** (desactivado) e **Edit username** (desactivado).
3. **Comproba**, co túnel aberto: `scripts/comprobar-roles-2fa.sh --vivo` (falla se algunha das catro opcións non é a da táboa). Despois, en `https://reservas.concello.gal/naron` → «Entrar» → «Rexistrarse», o formulario non ten «Nome de usuario».

Non hai que tocar as contas que xa existen: seguen entrando co nome de usuario ou co correo.

## Que o comproba

- **`scripts/comprobar-roles-2fa.sh`** (X02, sobre o JSON do repositorio; con `--vivo`, sobre un Keycloak en marcha): as catro opcións da táboa.
- **D51** (`scripts/proba-identidade-keycloak.sh rexistro`): o rexistro, en gl e es, sen `#username`, co correo con `autocomplete="email"` e etiqueta «Correo electrónico». A conta creada ten o correo como usuario.
- **D52** (`… clave`): a conta que crea «Cl@ve simulada» ten como usuario o correo que manda o IdP, e non o NIF.
- **D48** (`scripts/proba-producion.sh`): a pantalla de acceso di «Correo electrónico». Unha conta creada pola API de administración co usuario `vecino-proba` queda como `vecino-proba@example.org`, e todas as persoas da proba entran co correo.
- **E2E (D34):** o rexistro da persoa invitada (`persoal.e2e.ts`) non ten campo de usuario e trae o correo xa posto; `loginAs` e `keycloakLogin` entran co correo de demo, coa etiqueta «Correo electrónico».
