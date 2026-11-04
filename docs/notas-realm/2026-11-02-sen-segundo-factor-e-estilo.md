<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# 2026-11-02 · Sen segundo factor, contrasinal con lista de comúns e estilo de Furar

Decisión do mantedor do 2026-11-02: ninguén usa segundo factor (nin o persoal nin a plataforma), o contrasinal só pide 12 caracteres ou máis, distinto do correo e fóra dunha lista de contrasinais comúns, e as pantallas e os correos de Keycloak levan o estilo de Furar. Quen perde o acceso ao seu correo de traballo: a xestión desactívaa en Furar e invita o correo novo.

O realm só se importa a primeira vez. Nun Keycloak xa instalado, despois de actualizar a imaxe (que trae os temas e a lista), fai isto na consola de administración (túnel SSH, [`instalacion.md`](../instalacion.md) §5), realm `furar`:

1. **Fluxo de acceso.** *Authentication* → fluxo `browser` → ⋮ → *Bind flow* → *Browser flow*. Despois borra `furar-browser` (os seus subfluxos van con el).
2. **Niveis de acceso.** *Realm settings* → *General* → *ACR to LoA Mapping*: borra `furar-pwd` e `furar-2fa`.
3. **Rol `furar-2fa`.** *Realm roles* → `furar-2fa` → *Delete*. Sae tamén do grupo `plataforma` e do rol composto `*:PLATFORM_ADMIN` (*Clients* → `furar-api` → *Roles* → `*:PLATFORM_ADMIN`: xa non é composto).
4. **Accións.** *Authentication* → *Required actions*: desactiva *Configure OTP* e *Recovery Authentication Codes*.
5. **Credenciais que xa hai.** *Users* → cada persoa → *Credentials*: borra as de tipo *otp* e *recovery-authn-codes*. Co fluxo `browser`, a quen lle quede un OTP pídeselle aínda ao entrar.
6. **Mapeador `amr`.** *Clients* → `furar-web` → *Client scopes* → `furar-web-dedicated` → borra `furar-amr`.
7. **Política de contrasinais.** *Authentication* → *Policies* → *Password policy*: deixa *Minimum length* 12, *Not username*, *Not email* e *Maximum length* 128; quita *Uppercase*, *Lowercase*, *Digits* e *Special characters*; engade *Password blacklist* co valor `furar-contrasinais-comuns.txt` (a imaxe trae o ficheiro en `/opt/keycloak/data/password-blacklists`). Os contrasinais que xa hai seguen valendo; a política aplícase ao crear ou cambiar un.
8. **Caducidade das ligazóns.** *Realm settings* → *Tokens* → *Override action tokens*: *Email Verification* 30 minutos e *Forgot Password* 30 minutos (no ficheiro, os atributos `actionTokenGeneratedByUserLifespan.verify-email` e `actionTokenGeneratedByUserLifespan.reset-credentials`, 1800 s).
9. **Temas.** Veñen coa imaxe: *Realm settings* → *Themes* segue en `furar` (acceso, conta e correo).

**Comprobar:** `scripts/comprobar-realm.sh --vivo` (co túnel aberto) falla se queda o rol `furar-2fa`, `acr.loa.map`, algún fluxo `furar-browser`, as accións de OTP ou de códigos activas, o mapeador `amr`, un rol por concello en `furar-api`, ou se a política non ten a lista de comúns.

Como se via entón: quen chega dunha invitación crea a conta co correo xa posto (correo, nome e apelidos), verifica o correo (a ligazón vale 30 minutos), pon o contrasinal e volve a Furar. Desde a [nota seguinte do mesmo día](2026-11-02-invitacion-verifica-o-correo.md), a invitación xa verifica o correo e o contrasinal vai no formulario de rexistro. Tras un acceso fallido segue o aviso xenérico do bloqueo (5 intentos, 15 minutos).
