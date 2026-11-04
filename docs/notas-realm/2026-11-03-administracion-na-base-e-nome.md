<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# 2026-11-03 · A administración da plataforma, na base de Furar; o nome «Reservas deportivas»

Desde o 2026-11-03 ([ADR-0028](../adr/0028-administracion-da-plataforma-e-primeiro-acceso.md)), Keycloak non dá ningún rol. A administración da plataforma está na base de datos de Furar, coma o persoal de cada concello ([ADR-0027](../adr/0027-persoal-e-permisos-fase-1.md)): a primeira administración é a conta que entra co correo verificado igual a `FURAR_ADMIN_EMAIL` mentres aínda non hai ningunha. As pantallas e os correos de Keycloak levan a marca «Reservas deportivas» e non «Furar».

O realm só se importa a primeira vez. Nun Keycloak xa instalado, fai isto na consola de administración (túnel SSH, [`instalacion.md`](../instalacion.md) §5), realm `furar`:

1. **Nome e remitente** (cando queiras; non depende da API). *Realm settings* → *General* → *Display name*: `Reservas deportivas`. *Realm settings* → *Email* → *From display name*: `Reservas deportivas`. Opcional: *Clients* → `furar-web` → *Name*: `Reservas deportivas` (vese na consola da conta).
2. **Primeiro, a API nova.** Os pasos 3 a 5 fanse **despois** de actualizar á versión da API que le a administración da base de datos: coa API anterior, quen administra deixaría de selo ao borrar o rol.
3. **Grupo `plataforma`.** *Groups* → `plataforma` → *Delete*.
4. **Rol `*:PLATFORM_ADMIN`.** *Clients* → `furar-api` → *Roles* → `*:PLATFORM_ADMIN` → *Delete*. `furar-api` queda sen ningún rol (se quedase algún `<concello>:…` antigo, bórrao tamén).
5. **Mapeador `furar_roles`.** *Clients* → `furar-web` → *Client scopes* → `furar-web-dedicated` → borra `furar-roles` (o que emite o claim `furar_roles`). Deixa `audience-furar-api`.
6. **Quen administraba.** O rol non pasa só desde Keycloak: a administración que había tense que volver crear en Furar. Se aínda non hai ningunha na base, entra coa conta de `FURAR_ADMIN_EMAIL` (co correo verificado) e queda como administración; se esa conta non é a de quen administraba, cambia antes `FURAR_ADMIN_EMAIL` na configuración do despregue. O detalle, en [ADR-0028](../adr/0028-administracion-da-plataforma-e-primeiro-acceso.md).
7. **Temas.** Veñen coa imaxe: a marca é o nome do realm (paso 1), ningún texto di «Furar», a «Verificación do correo» ten un só texto e o botón «Reenviar o correo», e o castelán está ao día. Con `furar_setup=1` no URL de acceso (a web pono mentres non hai concellos), o rexistro non mostra a frase de privacidade.

**Comprobar:** `scripts/comprobar-realm.sh --vivo` (co túnel aberto) falla se queda o grupo `plataforma`, algún rol en `furar-api` ou un mapeador que emita `furar_roles`, ademais do que xa comprobaba ([nota do 2026-11-02](2026-11-02-sen-segundo-factor-e-estilo.md)).
