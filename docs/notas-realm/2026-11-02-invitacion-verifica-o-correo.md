<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# 2026-11-02 · A invitación verifica o correo e o contrasinal vai no rexistro

Decisión do mantedor do 2026-11-02 ([ADR-0027](../adr/0027-persoal-e-permisos-fase-1.md), punto 6): quen crea a conta desde a ligazón da invitación do persoal xa demostrou que o correo é seu, así que Keycloak non lle manda outro correo para verificalo. A API asina unha proba curta co correo e a invitación (`FURAR_INVITATION_PROOF_SECRET`) e a extensión de Furar, que vai na imaxe de Keycloak, compróbaa no rexistro. Por calquera outro camiño (a veciñanza, ou unha proba caducada, manipulada ou cun correo distinto) Keycloak pide verificar o correo coma sempre. O contrasinal pídese no mesmo formulario de rexistro, para todos.

O realm só se importa a primeira vez. Nun Keycloak xa instalado:

1. **Segredo.** Engade ao `.env` `FURAR_INVITATION_PROOF_SECRET` (`openssl rand -base64 48`; o mesmo valor serve á API e a Keycloak, [`instalacion.md`](../instalacion.md) §4). Sen el, a API non arrinca en produción.
2. **Imaxe.** Actualiza á versión que trae a extensión (§7 de `instalacion.md`) e arrinca. Na consola (túnel SSH), *Realm settings* → *Provider info*, `form-action` ten que listar `furar-invitation-email`.
3. **Fluxo de rexistro.** *Authentication* → fluxo `registration` → ⋮ → *Duplicate*, co nome `furar-registration`. No formulario do fluxo novo:
   - *Add step* → «Furar: e-mail verified by the staff invitation» → *Required*, despois de «Password Validation».
   - «Password Validation» → ⚙ → alias `furar-password-on-form` e *Always set password on register form* activado.
   - «Terms and conditions» e reCAPTCHA seguen desactivados.
4. **Usalo.** No fluxo `furar-registration`, ⋮ → *Bind flow* → *Registration flow*.

**Comprobar:** `scripts/comprobar-realm.sh --vivo` (co túnel aberto) falla se o rexistro non é `furar-registration` ou non ten o paso `furar-invitation-email`. Despois, unha invitación de proba: a ligazón leva ao rexistro co correo só de lectura e, ao crear a conta, volve a Furar sen correo de verificación; en *Events* queda un `VERIFY_EMAIL` con `furar_verified_by=staff_invitation`.

Como se ve agora: quen chega dunha invitación escribe nome, apelidos e contrasinal co correo xa posto e fixo, preme «Crear a conta» e volve a Furar e xa está dentro (desde o 2026-11-03, sen modal das obrigas: a invitación acéptase soa). A veciñanza escribe o correo, nome, apelidos e contrasinal, verifica o correo coa ligazón (30 minutos) e volve a Furar. Unha conta que aínda non verificou o correo non entra; quen é dono do correo recupéraa con «Esqueciches o contrasinal?».
