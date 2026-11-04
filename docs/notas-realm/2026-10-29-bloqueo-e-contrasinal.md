<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Bloqueo tras intentos fallidos e política de contrasinais

**Data:** 2026-10-29 · **Orixe:** P2 e P3 do [plan do dominio 4](../plan/dominio-4-persoal.md#6-preguntas-de-produto), decididas polo mantedor · **Seguridade:** [`ens.md`](../ens.md#contrasinais-e-bloqueo) (op.acc.5, op.acc.6, op.acc.6.8; risco R10).

Esta nota non é unha nota de versión: explica un cambio do realm e como levalo a man a un Keycloak xa instalado. A nota da versión que o inclúa remite aquí.

**Conclusión:** para a veciñanza e o persoal, 5 intentos fallidos bloquean a conta 15 minutos (nunca de forma permanente), e o contrasinal ten 12 caracteres ou máis con maiúscula, minúscula, número e carácter especial. O tema de acceso avisa do bloqueo tras cada fallo e marca os requisitos do contrasinal mentres se escribe; eses cambios veñen coa imaxe de Keycloak de Furar e non hai que facer nada.

## O cambio

| Opción do realm | Valor | Antes | Por que |
|---|---|---|---|
| `bruteForceProtected` | `true` | igual | Protección de forza bruta |
| `permanentLockout` | `false` | igual | Un bloqueo permanente deixaría sen acceso a calquera persoa por un ataque alleo |
| `failureFactor` | **`5`** | `10` | Intentos antes do bloqueo |
| `bruteForceStrategy` | `MULTIPLE` | igual (por defecto) | A espera é `waitIncrementSeconds` × (fallos ÷ 5) |
| `waitIncrementSeconds` | **`900`** | `60` | O primeiro bloqueo xa dura 15 minutos |
| `maxFailureWaitSeconds` | `900` | igual | Nunca máis de 15 minutos |
| `minimumQuickLoginWaitSeconds` | **`0`** | `60` (por defecto) | Sen o bloqueo de 60 s que Keycloak pon cando dous fallos chegan en menos de `quickLoginCheckMilliSeconds` |
| `quickLoginCheckMilliSeconds` | `1000` | igual (por defecto) | Sen efecto coa espera mínima a 0 |
| `maxDeltaTimeSeconds` | **`900`** | `43200` (por defecto, 12 h) | O contador volve a cero 15 minutos despois do último fallo: tras o bloqueo hai outros 5 intentos, non un |
| `passwordPolicy` | **`length(12) and upperCase(1) and lowerCase(1) and digits(1) and specialChars(1) and notUsername and notEmail and maxLength(128)`** | sen `upperCase`, `lowerCase`, `digits` nin `specialChars` | Calidade do contrasinal (op.acc.5, op.acc.6) |

Comportamento resultante (comprobado no código de Keycloak 26.8.1, `DefaultBruteForceProtector`): os fallos 1 a 4 non bloquean, aínda que sexan seguidos; o 5.º bloquea 900 s; mentres dura, os intentos non contan e mesmo o contrasinal bo dá o erro dun contrasinal malo; restablecer o contrasinal desde «Esqueciches o contrasinal?» desbloquea; os fallos do OTP contan no mesmo contador.

A política nova vale para os contrasinais que se poñan a partir de agora: as contas cun contrasinal vello que non a cumpre seguen entrando, e a política aplícase cando o cambien.

## Como aplicalo a man

Fai falta en calquera Keycloak instalado antes deste cambio: `--import-realm` non toca un realm que xa existe ([ADR-0015](../adr/0015-despregue.md)). Abre a administración polo túnel ([instalación](../instalacion.md) §5, paso 1) → realm `furar`:

1. *Realm settings* → *Security defenses* → *Brute force detection*: modo **Lockout temporarily**; *Max login failures* **5**; *Wait increment* **15 minutos**; *Max wait* **15 minutos**; *Failure reset time* **15 minutos**; *Quick login check milliseconds* **1000**; *Minimum quick login wait* **0**; estratexia **Multiple**. Gardar.
2. *Authentication* → *Policies* → *Password policy*: engade *Uppercase characters* **1**, *Lowercase characters* **1**, *Digits* **1** e *Special characters* **1**; deixa *Minimum length* 12, *Not username*, *Not email* e *Maximum length* 128. Gardar.

Ou, coa API de administración (o token como en `scripts/comprobar-roles-2fa.sh --vivo`; un `PUT` parcial só cambia os campos que leva):

```sh
curl -sf -X PUT -H "Authorization: Bearer $KC_ADMIN_TOKEN" -H 'Content-Type: application/json' \
  http://127.0.0.1:8082/auth/admin/realms/furar -d '{
    "bruteForceProtected": true, "permanentLockout": false, "failureFactor": 5, "bruteForceStrategy": "MULTIPLE",
    "waitIncrementSeconds": 900, "maxFailureWaitSeconds": 900, "minimumQuickLoginWaitSeconds": 0,
    "quickLoginCheckMilliSeconds": 1000, "maxDeltaTimeSeconds": 900,
    "passwordPolicy": "length(12) and upperCase(1) and lowerCase(1) and digits(1) and specialChars(1) and notUsername and notEmail and maxLength(128)"}'
```

3. **Comproba**, co túnel aberto: `scripts/comprobar-roles-2fa.sh --vivo` (falla se o bloqueo ou a política non son os da táboa, ou se a política ten algo que o tema de acceso non coñece). Despois, en «Entrar», un contrasinal malo mostra «Despois de 5 intentos fallidos, a conta bloquéase 15 minutos…» baixo o campo.

Na demo (`dev`, `full`), o contrasinal dos usuarios pasa a ser `Furar-demo-2026`: recrea o realm como di [`deploy/keycloak/README.md`](../../deploy/keycloak/README.md#recrear-o-keycloak-de-dev-ou-full).

## Que o comproba

- **`scripts/comprobar-roles-2fa.sh`** (X02, sobre o JSON do repositorio; con `--vivo`, sobre un Keycloak en marcha): os valores do bloqueo da táboa; a política con 12 caracteres ou máis, maiúscula, minúscula, número, carácter especial, `notUsername` e `notEmail`; e que o tema coñece cada política (a lista de `password-validation.ftl`).
- **D48** (`scripts/proba-producion.sh`): 4 fallos seguidos non bloquean; o 5.º bloquea, e a API de administración (`attack-detection`) dá `failedLoginNotBefore` 900 s despois do último fallo, sen esperar; o aviso sae en cada fallo; bloqueada, o contrasinal bo dá o mesmo erro ca un malo; desbloqueada desde a administración, entra.
- **D51** (`scripts/proba-identidade-keycloak.sh rexistro`): os requisitos en vivo no cambio de contrasinal (os da política do realm, ✓/✗ coas cores e o texto, `aria-describedby`, o resumo en `aria-live` con retardo, «coinciden»), a validación no servidor, o rexistro sen erros de JavaScript e o mesmo aviso exista ou non a conta.
- **X09** (`scripts/proba-temas-keycloak.sh`): `login.ftl` e `password-validation.ftl` parten da base da versión fixada, e as claves `furar*` que usan están en gl e es.
- **E2E (D34):** o contrasinal da conta que crea o e2e do persoal cumpre a política e a lista queda toda en ✓.
