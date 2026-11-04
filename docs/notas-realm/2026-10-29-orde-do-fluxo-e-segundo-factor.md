<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Orde do fluxo `furar-browser` e cambio do segundo factor

**Data:** 2026-10-29 · **Orixe:** A1 da revisión de seguridade do dominio 4 ([ENS](../ens.md), op.acc.6) · **Cambia:** a forma do fluxo da [nota do 2026-10-28](2026-10-28-loa-codigos-recuperacion.md) · **Decisión:** [ADR-0007](../adr/0007-seguridade-pechada.md), [ADR-0027](../adr/0027-persoal-e-permisos-fase-1.md).

Esta nota non é unha nota de versión: explica un cambio do realm e como levalo a man a un Keycloak xa instalado. A nota da versión que o inclúa remite aquí.

**Conclusión:** o subfluxo «furar-browser nivel 2» ten que ser o último de «furar-browser formulario». Se non, unha sesión só de contrasinal pode xerar códigos de recuperación, engadir outro OTP ou borrar o que hai.

## O problema

Keycloak 26 dá a cada tipo de credencial un nivel (LoA): **o do último subfluxo con nivel onde aparece** o seu autenticador. Para decidir se unha acción sobre esa credencial (`kc_action=CONFIGURE_TOTP`, `CONFIGURE_RECOVERY_AUTHN_CODES` ou `delete_credential:<id>`) precisa reautenticarse, compara ese nivel co da sesión.

Na forma da nota do 2026-10-28, o formulario era nivel 1 → nivel 2 → plataforma. O OTP e os códigos de recuperación aparecen tamén no subfluxo «plataforma», que non ten condición de nivel, e ese ía despois do nivel 2. Así, Keycloak víaos sen nivel e deixaba cambialos cunha sesión de nivel 1: cun contrasinal roubado chegábase a configurar un segundo factor propio e, con el, ao nivel 2. D48 detectouno cunha persoa con OTP: as tres accións seguían sen pedir o OTP nin un código.

## A forma nova

«furar-browser formulario» queda **nivel 1 → plataforma → nivel 2**:

| Prioridade | Subfluxo | Que fai |
|---|---|---|
| 10 | `furar-browser nivel 1` | Usuario e contrasinal (`amr` `pwd`) |
| 20 | `furar-browser plataforma` | Rol `furar-2fa` sen segundo factor neste acceso: OTP ou código (ou alta do OTP) |
| 30 | `furar-browser nivel 2` | Cando a web pide `acr_values=furar-2fa`. Vai o último, así que o OTP e os códigos quedan no nivel 2 |

Como a plataforma xa pode ter dado o OTP antes de chegar ao nivel 2, o nivel 2 cambia para non pedilo outra vez:

- **`furar-browser nivel 2 configurado`** leva, despois de `conditional-user-configured`, un `conditional-credential` REQUIRED coa config **`furar-sen-segundo-factor`** (`credentials` `otp##recovery-authn-codes`, `included` `false`): só pide o OTP ou un código se aínda non se deu ningún neste acceso.
- **`furar-browser nivel 2 xa dado`** (novo, CONDITIONAL, prioridade 40, o último do nivel 2): `conditional-credential` REQUIRED coa config **`furar-con-segundo-factor`** (as mesmas credenciais, `included` `true`) e `allow-access-authenticator` REQUIRED. Se a plataforma xa deu o OTP ou un código, o nivel 2 vale sen outro.
- `furar-browser nivel 2 alta` non cambia: xa usaba `furar-sen-segundo-factor`.

O JSON exacto está en [`deploy/keycloak/import/furar-realm.json`](../../deploy/keycloak/import/furar-realm.json) (e igual no realm de demo, `deploy/keycloak/import-demo/`). Do resto da nota do 2026-10-28 non cambia nada: niveis, `amr`, configs `furar-amr-*` e `furar-loa-*`, accións requiridas.

## Como aplicalo a man

Fai falta en calquera Keycloak instalado antes deste cambio: `--import-realm` non toca un realm que xa existe ([ADR-0015](../adr/0015-despregue.md)). Abre a administración polo túnel ([instalación](../instalacion.md) §5, paso 1) → realm `furar` → *Authentication* → *Flows* → `furar-browser`.

1. **Orde.** En «furar-browser formulario», arrastra «furar-browser nivel 2» debaixo de «furar-browser plataforma». Os tres seguen *Conditional*.
2. **Nivel 2 configurado.** En «furar-browser nivel 2 configurado» → *Add condition* → *Condition - credential*, *Required*, xusto despois de *Condition - user configured* (antes do OTP). Na roda dentada: alias `furar-sen-segundo-factor` (ou outro, se a consola non deixa repetilo), credenciais `otp` e `recovery-authn-codes`, *Included* desactivado.
3. **Nivel 2 xa dado.** En «furar-browser nivel 2» → *Add sub-flow* «furar-browser nivel 2 xa dado» (*Generic*), *Conditional*, o último do nivel 2. Dentro:
   - *Add condition* → *Condition - credential*, *Required*, alias `furar-con-segundo-factor`, credenciais `otp` e `recovery-authn-codes`, *Included* activado;
   - *Add step* → *Allow access*, *Required*.
4. **Comproba** que a árbore de `furar-browser` queda como no JSON do repositorio. A orde que mostra a consola ten que ser: formulario · nivel 1 · plataforma · plataforma configurado · plataforma alta · nivel 2 · nivel 2 configurado · nivel 2 alta · nivel 2 xa dado. Despois, co túnel aberto, pasa `scripts/comprobar-roles-2fa.sh --vivo`.

## Que o comproba

- **`scripts/comprobar-roles-2fa.sh`** (X02, sobre o JSON do repositorio; con `--vivo`, sobre un Keycloak en marcha): falla se «furar-browser nivel 2» non é o último subfluxo de «furar-browser formulario».
- **D48** (`scripts/proba-producion.sh`): espera os subfluxos de `furar-browser` na orde nova e, cunha xestora con OTP e unha sesión de nivel 1, comproba que `kc_action=CONFIGURE_RECOVERY_AUTHN_CODES`, `kc_action=CONFIGURE_TOTP` e `kc_action=delete_credential:<id>` piden antes o OTP ou un código, e que o OTP segue alí. Tamén mantén os casos da nota do 2026-10-28, entre eles a plataforma cun só OTP con `acr_values=furar-2fa`.
