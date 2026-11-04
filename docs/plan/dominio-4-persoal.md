<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Plan do dominio 4: persoal e permisos, fase 1

> **Histórico: manda a especificación e as ADR.** Este plan guiou a construción do dominio e non se mantén ao día; se algo non coincide coa [especificación](../produto/especificacion.md) ou coas [ADR](../adr/), valen elas.

**Estado:** executado (histórico). Era a proposta inicial, v1 · 2026-10-28 · do 5 ao 13 de novembro (D4-01 e D4-02 poden empezar antes, mentres remata o dominio 3). **Fonte de produto:** [especificación](../produto/especificacion.md), bloque 7 (e «Conta e acceso» do bloque 5), recortado para a fase 1 coas decisións do 2026-10-28. Onde este plan e a especificación non coincidan, manda a especificación. O que falta de produto está no §6 e **non se decide ao construír**: constrúese o valor por defecto marcado e cámbiase cando o mantedor responda.

Hai que ler isto, as regras do perfil (§4) e a tarefa propia (§3). As regras comúns son as do [plan do dominio 1](dominio-1-instalacions.md) §2.1 e §3.5: carrís, lock de Docker, ficheiros quentes e rebase das migracións.

---

## 1. Alcance técnico

**Entra:**
- **Pertenza do persoal en Furar, por concello:** táboa propia (correo, rol `MANAGER`|`STAFF`, estado `INVITED`|`ACTIVE`|`REVOKED`, quen invitou e cando, cando aceptou, quen deu de baixa e cando) con RLS. **Os roles de persoal saen desta táboa en cada petición, non do token:** a baixa ou o cambio de rol valen na seguinte petición. `*:PLATFORM_ADMIN` segue en Keycloak.
- **Invitación propia de Furar:** o xestor invita por correo e escolle o rol. O token é aleatorio, dun só uso e caduca aos 7 días; só se garda o seu hash. O envía `notification`. A ligazón leva a entrar ou crear conta en Keycloak. Só se acepta se o correo verificado do token coincide co invitado. Antes hai unha pantalla coas obrigas (custodia, confidencialidade, avisar se se perden as credenciais, art. 5 LOPDGDD); non é consentimento RGPD e a aceptación queda rexistrada. A API **non** ten credenciais de administración de Keycloak.
- **Pantalla «Persoal»** (`/:concello/xestion/persoal`): lista, invitar, reenviar, cambiar o rol e dar de baixa (con confirmación).
- **Dobre factor do persoal** por autenticación escalonada (`acr_values` → nivel 2 en Keycloak), sen que Keycloak coñeza os roles por concello. Inclúe códigos de recuperación. A demo segue sen OTP para os usuarios de sempre, pero ten un usuario que entra con OTP (§1.2.9).
- **Rexistro de actividade:** `staff.invite`, `staff.invite-resend`, `staff.accept`, `staff.role-change`, `staff.revoke` (e `staff.sign-in`, P11). Pantalla «Rexistro de actividade» (`/:concello/xestion/rexistro`), só de lectura, para o xestor e co seu concello, con filtros básicos. Eventos e conservación documentados en `ens.md`.
- **PLATFORM_ADMIN deixa de ter todos os permisos.** Perde `booking.*`, `staff-data.view`, `indicators.view`, `facility.*`, `customfields.manage`, `municipality.update` e `municipality.export`. Proba en `RolePermissionsTest`. **Feito (D4-03, 2026-10-28).** Revisión do 2026-11-03: recupera `facility.*`, `customfields.manage`, `municipality.update` e `audit.view` ([ADR-0028](../adr/0028-administracion-da-plataforma-e-primeiro-acceso.md) punto 9).
- **«O teu último acceso foi o …»** na xestión (op.acc.6 R5).
- **STAFF con permisos fixos en todo o concello**, como hoxe.
- **Demo:** `recepcion-naron` (STAFF) e exemplos de persoal invitado, activo e revogado.

**Non entra (capas posteriores documentadas, sen data):** alcance por instalación e interruptores do conserxe (bloque 7) · chaves de acceso e WebAuthn · entrada do persoal con Cl@ve ou certificado de empregado público · revisión periódica de permisos (op.acc.4; na fase 1 a lista mostra o último acceso como axuda) · restablecer o dobre factor desde Furar (hoxe, consola de Keycloak) · volver aceptar as obrigas cando cambie o texto · texto das obrigas editable por concello · responsables de entidade.

### 1.1 Que existe e que se usa

| Peza | Estado a 2026-10-28 | Que pasa |
|---|---|---|
| `JwtCurrentActor` le `furar_roles` (`naron:MANAGER`, `*:PLATFORM_ADMIN`) e, con `require-otp`, deixa en `withheld` os roles con privilexios se `amr` non trae `otp` | Feito | Engade a pertenza da táboa no concello do `TenantContext`. O claim só conta para `*:PLATFORM_ADMIN`; os roles de concello do claim, nunca (§1.2.4). Acepta `amr` con `otp` ou `mfa` |
| `RolePermissions`: PLATFORM_ADMIN só ten o que o lista (`municipality.create`, `municipality.import`) | Feito (D4-03) | Engádense os permisos de persoal e rexistro da plataforma (§1.2.10, D4-04) |
| `PermissionAuthorizationManager` e o 403 `auth.step-up-required` | Feito | Sen cambios: os roles da táboa tamén van a `withheld` sen OTP |
| Web: `roles.ts`, `SessionService.stepUp` (`prompt=login`), gardas `managerGuard`/`bookingsGuard`/`platformGuard` | Feito, todo sobre o token | O rol do concello vén da API (`GET …/me/staff`), o step-up pide `acr_values` e a plataforma xa non «xestiona» (D4-13) |
| Keycloak: fluxo `furar-browser` (OTP se hai rol `furar-2fa`), grupos `persoal`, `naron-xestores`…, `comprobar-roles-2fa.sh`, `instalacion.md` §5 | Feito | Engádese un subfluxo de nivel 2 (LoA) con OTP ou código de recuperación. `furar-2fa` queda só para `*:PLATFORM_ADMIN`. Os roles por concello desaparecen do realm e da guía (D4-11) |
| `audit_event` + `AuditLog` (ADR-0020), usado en `facility`, `booking` e portabilidade | Feito, sen lectura | Columna `actor_roles` e porto de lectura `AuditTrail` (D4-09) |
| `notification`: catro correos de reservas, `MailTemplate`, `notification_delivery` (só `booking_id`) | Feito | API pública nova `StaffInvitationMailer`; `notification_delivery` acepta invitacións (D4-06) |
| `@PersonUseCase` co erro `auth.email-not-verified` | Feito (D4-03) | Sen cambios: tamén o usa aceptar a invitación |
| `people` en `arquitectura.md` («veciño, entidade, persoal e roles») | Só previsto | O persoal vai noutro módulo, `staff` (§1.2.1) |
| `db/demo`, overlay de Keycloak con grupos `naron-xestores`… | Feito | Pertenzas na táboa e ids fixos nos usuarios (D4-12) |
| `shared/ui`: `furar-dialog`, `furar-confirm`, `[furarMenu]`, `furar-select`, `tag`, `tabs`, `status-state`, `page-header`, `user-menu`; `furar-data-table` (D3-07) se xa está en `main` | Feitos | Reutilízanse; sen dependencias novas na web |

### 1.2 Decisións técnicas (non son produto)

1. **Módulo novo `staff`** (`scripts/novo-modulo.sh staff StaffMember staff persoal`, reducido: sen entidade JPA nin CRUD xerado). O glosario xa di «Persoal → `staff`». **Non vai en `people`**, porque `people` será o perfil da veciñanza (RIDEGA, dereitos, anonimización, ADR-0017) e mesturalos faría depender o control de acceso do ciclo de vida dos datos da veciñanza. Dependencias: `staff → shared, notification`. **SQL nativo con RLS, como `booking`**, e non JPA con `@TenantId`. Motivo: a pertenza non é contido portable do concello, porque o `sub` só vale no Keycloak de cada despregue. Con JPA, A21 obrigaría a ter unha sección en `furar-export` sen sentido e a tocar D50. **Descartado:** JPA con `StaffDataSection` que importase as persoas como invitadas (máis código e D50 para nada). Nota en ADR-0018: o persoal non entra en `furar-export`.
2. **Roles efectivos = táboa no concello da petición.** Engádese a SPI `shared.security.StaffRoles` (`Optional<Role> activeRole(String subject, TenantRef municipality, @Nullable Instant authTime)`), que implementa `staff`; `shared` trae `NoStaffRoles` para `@ApplicationModuleTest(STANDALONE)`, como `NoBookingImpact`. `JwtCurrentActor` chámaa só se hai `TenantContext` e garda o resultado na petición (atributo de `RequestAttributes`) para non repetir a lectura. A lectura vai nunha transacción curta propia (`TransactionTemplate`): a autorización corre antes da transacción do caso de uso e, sen transacción, RLS non devolve nada. A consulta é `select role from staff_member where municipality_id = :m and subject = :s and status = 'ACTIVE'` sobre un índice. **Sen caché entre peticións:** a baixa vale na seguinte. **Descartados:** (a) unha táboa global espello `staff_grant` sen RLS, porque serían dúas fontes; (b) unha función `security definer` que liste todos os concellos da persoa, porque a fase 1 non o necesita (a web pregunta polo concello do URL); (c) escribir os roles en Keycloak, porque precisaría credenciais de administración na API.
3. **OTP para os roles da táboa:** a mesma regra de hoxe. Con `require-otp`, se `amr` non trae `otp` nin `mfa`, a pertenza vai a `withheld` e a API responde 403 `auth.step-up-required`.
4. **Sen transición co claim (P5, decidida o 2026-10-30).** Os `slug:MANAGER|STAFF` do claim non contan en ningún perfil; non hai propiedade nin variable para sumalos. Os IT danlle o rol ao `sub` do token cunha pertenza `ACTIVE` na táboa (`TestTokens`); `JwtCurrentActorTest` e `JwtValidationIT` proban que un `naron:MANAGER` no token non dá ningún permiso.
5. **Último acceso con `auth_time`** (o claim que Keycloak pon no token ao autenticar; confírmao D4-02). Cando `activeRole` ve un `auth_time` maior ca `last_sign_in_at`, fai `update … set previous_sign_in_at = last_sign_in_at, last_sign_in_at = :t where … and (last_sign_in_at is null or last_sign_in_at < :t)`. Iso é unha escritura por sesión, non por petición, e só a petición que gaña a carreira (1 fila) rexistra `staff.sign-in`. A web mostra `previousSignInAt`. **Descartado:** un `POST` desde a web, porque se podería saltar e non o faría o servidor.
6. **Invitación.**
   - **Token:** 32 bytes de `SecureRandom` en base64url. Gárdase SHA-256 en `staff_invitation.token_hash` (único), con caducidade de 7 días desde a emisión.
   - **Envío:** síncrono, despois do commit e fóra da transacción. O caso de uso escribe a invitación nunha transacción, chama a `notification.StaffInvitationMailer.send(…)` co token en memoria e garda o resultado (`SENT`/`FAILED`) noutra transacción curta. O xestor ve ao momento «Enviada» ou «Non se puido enviar · Reenviar». **Descartado o evento despois do commit** (o patrón de ADR-0025): o rexistro de publicacións serializa o evento en `event_publication` e gardaría o token en claro; xeralo no escoitador obrigaría a unha SPI de escritura de `staff` chamada por `notification`.
   - **Ligazón:** `${FURAR_PUBLIC_URL}/{slug}/convite#<token>`. O token vai no fragmento, que nin chega ao servidor, nin aos rexistros de Caddy, nin ao `Referer`. A web gárdao en `sessionStorage` e limpa o URL. Para a API vai sempre no corpo dun `POST`. Abrir a ligazón (GET) non consome nada: os analizadores de ligazóns do correo non a gastan.
   - **Aceptar:** precisa sesión e `email_verified`. O correo do token normalízase (minúsculas, sen espazos) e ten que ser igual ao invitado. Se a persoa xa é ACTIVE nese concello, 409. A aceptación pasa a pertenza a `ACTIVE` co `sub`, o nome do token, `accepted_at` e `obligations_version`; a invitación queda `used_at`. Reenviar ou dar de baixa substitúen a invitación anterior (`superseded_at`).
   - **Respostas uniformes:** token descoñecido, usado, substituído ou de outro concello → 404 `staff.invitation-invalid`; caducado → 410 `staff.invitation-expired`. O correo da persoa invitada só se lle devolve a quen ten o token (é o mesmo que xa leva o correo). Sen límite de intentos en `preview` (D4-08): o token ten 256 bits e caduca aos 7 días, así que adiviñalo é inviable (mil millóns de intentos por segundo contra mil invitacións abertas: probabilidade arredor de 10⁻⁵⁹); un límite por IP non engade seguridade e obrigaría a gardar IPs.
7. **Límites de envío** (dominio, sen Caddy): como moito 30 invitacións (altas e reenvíos) por concello en 24 h; 2 min entre dous envíos á mesma pertenza; 5 envíos por pertenza. Se non, 429 `staff.invitation-rate-limited` con `Retry-After`. Cóntanse as filas de `staff_invitation` (P12). A invitación da lista leva `resendAvailableAt` e `resendsExhausted`: a web desactiva «Reenviar» coa conta atrás («Podes reenviar en 1:45») en vez de esperar ao 429.
8. **Regras do dominio:**
   - Unha pertenza non revogada por concello e correo, e unha ACTIVE por concello e `sub` (índices únicos parciais).
   - Transicións: `INVITED → ACTIVE`, `INVITED|ACTIVE → REVOKED`. O rol cámbiase en `INVITED` ou `ACTIVE`.
   - **Nunca pode quedar un concello sen xestor ACTIVE:** baixar de rol ou dar de baixa ao último → 409 `staff.last-manager`. Faise baixo `select … for update` das pertenzas `MANAGER` activas do concello (IT con dúas baixas simultáneas).
   - Volver invitar a alguén revogado crea unha fila nova (o historial queda).
   - Unha persoa pode estar en varios concellos con roles distintos (unha fila por concello).
9. **Dobre factor en Keycloak (autenticación escalonada).**
   - Mapa `acr.loa.map` = `{"furar-pwd":1,"furar-2fa":2}`.
   - `furar-browser` pasa á forma da guía de Keycloak: cookie | (nivel 1: contrasinal, e OTP se ten o rol `furar-2fa`, que agora só é da plataforma) | (nivel 2: OTP **ou** código de recuperación, `loa-max-age` 36000 como o `amr`).
   - Código de recuperación con `amr` `mfa` (RFC 8176); a API acepta `otp` ou `mfa`. Accións requiridas `CONFIGURE_TOTP` e `CONFIGURE_RECOVERY_AUTHN_CODES`.
   - A web pide `acr_values=furar-2fa` en dous casos: cando a API responde `auth.step-up-required`, e antes de entrar na xestión se `GET …/me/staff` di `secondFactor.required && !present`. Se a persoa non ten OTP, Keycloak obriga a configuralo.
   - Despois de aceptar, a web lanza a acción iniciada pola aplicación `kc_action=CONFIGURE_RECOVERY_AUTHN_CODES`.
   - **D4-02 confirma en Keycloak 26.8.1:** que os códigos de recuperación son compatibles (ou se precisan `--features=recovery-codes` no `Dockerfile`), que hai `auth_time`, o `amr` de cada camiño e que non se pide OTP dúas veces. **Plan B se LoA falla:** subfluxo «OTP se o ten configurado» e `kc_action=CONFIGURE_TOTP` despois de aceptar; a API segue esixindo `amr`.
   - **Demo:** segue co fluxo `browser` (sen OTP obrigatorio, `require-otp=false`), que pide OTP a quen o ten configurado. O usuario novo `xestor-naron-otp` (MANAGER de Narón) trae un TOTP de segredo coñecido (só demo, `otpauth://` e QR no README de Keycloak): enseña a pantalla do código e, desde «Seguridade da conta», os códigos de recuperación. O step-up de verdade próbao D48 contra o realm de produción. **Descartado:** activar OTP en toda a demo, porque o xurado precisaría móbil para entrar como `admin-plataforma`.
10. **PLATFORM_ADMIN explícito.** `PermissionKey.roles()` pode levar `PLATFORM_ADMIN`; a matriz xa non llo dá todo nin falla por iso. Fila da plataforma:
    - `municipality.create`, `municipality.import` (feito, D4-03);
    - `staff.view`, `staff.invite` (só rol MANAGER: alta e recuperación do concello), `staff.revoke`;
    - `audit.platform-view`.

    **Descartado:** quitar só os tres permisos que nomea o bloque 7. `facility.*` e `municipality.export` levan datos da veciñanza ou traballo do concello, e a especificación di «Non ve reservas nin datos da veciñanza».
11. **Matriz do módulo** (`StaffPermission` e `shared.audit.AuditPermission`):

    | Permiso | Roles |
    |---|---|
    | `staff.view` | MANAGER, PLATFORM_ADMIN |
    | `staff.invite` (inclúe reenviar) | MANAGER, PLATFORM_ADMIN |
    | `staff.manage` (cambiar o rol; invitar co rol STAFF) | MANAGER |
    | `staff.revoke` | MANAGER, PLATFORM_ADMIN |
    | `audit.view` | MANAGER |
    | `audit.platform-view` | PLATFORM_ADMIN |

    Invitar con STAFF sen `staff.manage` → 403 `staff.role-not-allowed`, comprobado no caso de uso con `Actor.can`. STAFF non ten ningún permiso destes. **Revisado o 2026-11-02 ([ADR-0027](../adr/0027-persoal-e-permisos-fase-1.md)):** a plataforma ten tamén `staff.manage` e fai co persoal o mesmo ca o xestor; `staff.role-not-allowed` desaparece.
12. **Rexistro de actividade.**
    - Nova columna `audit_event.actor_roles text[]`: os roles efectivos de quen actúa nese concello, que enche `JdbcAuditLog`. Responde á P1 (rol e concello de cada acción).
    - Detalles sen datos persoais, como xa di ADR-0020: `memberId`, `role`, `fromRole`/`toRole`, `obligationsVersion`, `code`. Nunca correo nin token.
    - Lectura: porto `shared.audit.AuditTrail` e `JdbcAuditTrail` (`@NativeSql(GLOBAL)`, `municipality_id = :m` explícito, paxinación por `(occurred_at, id)`). O caso de uso vive en `staff`, que pon nome e correo ao `sub` coas súas pertenzas (tamén revogadas). Unha conta da plataforma sae como «Administración da plataforma»; o resto, «Conta sen rol».
    - O xestor ve todo o do seu concello, tamén o que fixo a plataforma. A plataforma ve só os eventos con `PLATFORM_ADMIN` en `actor_roles` ou sen concello (P4).
    - Etiquetas das accións na web (`activity.actions.<código>`), cun texto de reserva se o código é descoñecido.
13. **Obrigas.** Texto fixo de Furar en gl/es (web, `staff.obligations.*`) coa versión `StaffObligations.CURRENT` (`2026-11`). A API rexeita outra versión (409 `staff.obligations-outdated`). O botón di «Comprométome a cumprilas»: sen casiña de «acepto». Inclúe a primeira capa RGPD do persoal (o concello trata correo, nome e rexistro de actividade por seguridade, ENS; base 6.1.c/e) e a ligazón a «Privacidade».
14. **Conservación:** o rexistro, `FURAR_AUDIT_RETENTION` (1 ano). As pertenzas revogadas manteñen a fila; correo e nome anonimízanse cando pasa ese mesmo prazo, para que o rexistro poida dicir quen foi mentres existe (P8). As invitacións non aceptadas e caducadas bórranse aos 30 días; cando cae a última dunha pertenza que nunca aceptou, a pertenza pasa a REVOKED (`revoked_by` = `furar:staff-retention`) e anonimízase na mesma pasada (RGPD art. 5.1.c).
15. **Web:** feature `features/staff/` con `members/`, `invitation/` e `activity/`, cada páxina con `loadComponent`. A plataforma usa a mesma páxina «Persoal» (garda `staffGuard`: xestor ou plataforma); a páxina esconde o que non pode facer. O rexistro da plataforma vai en `/plataforma/rexistro`. Sen dependencias novas.

---

## 2. Modelo de datos e API

### 2.1 Táboas
Mesmas regras ca nos dominios anteriores (`municipality_id`, `unique (municipality_id, id)`, FK compostas, RLS e política `<táboa>_tenant` na mesma migración, `version`).

| Táboa | Columnas | Garantías |
|---|---|---|
| `staff_member` (D4-04) | `email` (minúsculas), `display_name`, `subject`, `role` (`MANAGER`, `STAFF`), `status` (`INVITED`, `ACTIVE`, `REVOKED`), `language` (`gl`, `es`), `invited_by`, `invited_at`, `accepted_at`, `obligations_version`, `revoked_by`, `revoked_at`, `last_sign_in_at`, `previous_sign_in_at`, `created_at`, `updated_at`, `version` | `check (email = lower(email))`, lonxitude 3–254; por estado: `ACTIVE ⇒ subject, accepted_at, obligations_version not null`; `INVITED ⇒ subject is null and accepted_at is null`; `REVOKED ⇔ revoked_at and revoked_by not null`. Únicos parciais `(municipality_id, email) where status <> 'REVOKED'` e `(municipality_id, subject) where status = 'ACTIVE'`; índice `(municipality_id, subject)` |
| `staff_invitation` (D4-04) | `member_id` (FK `(municipality_id, member_id)`), `token_hash bytea`, `requested_by`, `created_at`, `expires_at`, `used_at`, `superseded_at`, `delivery` (`PENDING`, `SENT`, `FAILED`) | `token_hash` único con `octet_length = 32`; `expires_at = created_at + 7 días`; nunca `used_at` e `superseded_at` á vez; índices `(municipality_id, created_at)` (límite) e `(municipality_id, member_id, created_at desc)` |
| `notification_delivery` (D4-06) | `invitation_id uuid` nova; `booking_id` pasa a ser opcional; `kind` engade `STAFF_INVITATION` | `check (num_nonnulls(booking_id, invitation_id) = 1)`; único parcial `(municipality_id, invitation_id, event_id) where invitation_id is not null` |
| `audit_event` (D4-09) | `actor_roles text[] not null default '{}'` | Segue sen `UPDATE`/`DELETE` para `furar_app`; índice GIN non (volume baixo) |

Migracións: `V…__staff_member.sql`, `V…__staff_invitation.sql`, `V…__notification_staff_invitation.sql` e `V…__audit_actor_roles.sql`, rexistradas con `scripts/migracions.sh fixar`. Datos persoais (`email`, `display_name`, `subject`, `recipient`) en `datos-persoais.md`.

### 2.2 API pública entre módulos
- `shared.security.StaffRoles` (SPI, §1.2.2) e `NoStaffRoles`.
- `shared.audit.AuditTrail`: `page(UUID municipalityId, AuditFilter, cursor)` e `platformPage(…)`, records sen tipos internos.
- `notification.StaffInvitationMailer`: `DeliveryResult send(StaffInvitationMail)`, con `StaffInvitationMail(UUID invitationId, String recipient, Language language, String role, String inviterName, URI link, Instant expiresAt)`. `notification` pon o nome, a cor e o contacto do concello (`MunicipalityQueries.mailContext`).
- `staff.StaffQueries`: `members(Set<String> subjects)`, para poñer nomes no rexistro; tamén lle pode servir a outro módulo.

### 2.3 HTTP (prefixo `/api/v1/municipalities/{slug}` salvo indicación)

| Método e ruta | Permiso | Que |
|---|---|---|
| `GET /staff?status=` | `staff.view` | Lista: `id`, `email`, `name`, `role`, `status`, `invitedAt`, `invitedBy {name}`, `acceptedAt`, `revokedAt`, `lastSignInAt`, `invitation {expiresAt, delivery}`, `version` |
| `GET /staff/{id}` | `staff.view` | Detalle con `ETag` |
| `POST /staff` | `staff.invite` (+ `staff.manage` se é STAFF) | `{email, role, language}` → 201 + `Location` + `ETag`, coa entrega |
| `POST /staff/{id}/invitation` | `staff.invite`, `If-Match` | Reenviar (só `INVITED`): substitúe a anterior e conta outros 7 días |
| `PUT /staff/{id}/role` | `staff.manage`, `If-Match` | `{role}` |
| `POST /staff/{id}/deactivate` | `staff.deactivate`, `If-Match` | Desactivar (`INVITED` ou `ACTIVE`); ata o 2026-10-30, `POST …/revoke` («Dar de baixa», §9) |
| `POST /staff/{id}/reactivate` | `staff.deactivate`, `If-Match` | Reactivar (`DEACTIVATED` que aceptou), co mesmo rol (§9) |
| `POST /staff-invitations/preview` | `@PublicEndpoint` + `@PublicUseCase` | `{token}` → `{municipalityName, role, email, expiresAt}` |
| `POST /staff-invitations/accept` | `@PersonUseCase` | `{token, obligationsVersion}` → `{role}` |
| `GET /me/staff` | `@PersonUseCase` | `{role \| null, previousSignInAt, secondFactor {required, present}, workAccount}` (o rol saia aínda que estea en `withheld`) |
| `GET /audit-events?from=&to=&area=&memberId=&outcome=&cursor=` | `audit.view` | Páxina de 50: `at`, `action`, `outcome`, `actor {kind, name, email, roles}`, `target {kind, id, memberEmail?}`, `requestId` |
| `GET /api/v1/audit-events?from=&to=&cursor=` | `audit.platform-view` | Os da plataforma (§1.2.12) |

**Erros** (`messages/staff_{gl,es}.properties`, `messages/audit_*`): `staff.not-found` (404), `staff.already-member` (409), `staff.last-manager` (409), `staff.transition-invalid` (409), `staff.role-not-allowed` (403), `staff.invitation-rate-limited` (429), `staff.invitation-invalid` (404), `staff.invitation-expired` (410), `staff.invitation-email-mismatch` (403), `staff.obligations-outdated` (409), `audit.range-invalid` (400); `auth.email-not-verified` substitúe a `booking.email-not-verified`.

---

## 3. Tarefas

Tamaño: **S** ≤ ½ día; **M** ≈ 1 día. Un só carril pesado á vez: Docker (IT, Keycloak de usar e tirar, D48), `ng build` e Playwright nunca en paralelo. Rama `feat/d4-<id>-<nome>`.

### 3.1 Docs (carril D)

| ID | Que fai | Ficheiros | Criterios de aceptación | Tam. | Depende | Rol |
|---|---|---|---|---|---|---|
| **D4-01** | Documentación da fase:<br>· **ADR-0027** «Persoal e permisos, fase 1»: cada punto do §1.2 coa alternativa descartada.<br>· Revisións: ADR-0007 (roles da táboa, PLATFORM_ADMIN explícito, LoA, `amr mfa`, `token-staff-roles`), ADR-0020 (`actor_roles`, eventos `staff.*`, lectura), ADR-0017 e ADR-0018 (o persoal fóra de `furar-export`), ADR-0025 (`notification` con API pública).<br>· `arquitectura.md` §3 e §5: módulo `staff`; `people` só veciñanza e entidades.<br>· `glosario.md`: pertenza, invitación, obrigas do persoal, autenticación escalonada, código de recuperación, último acceso, rexistro de actividade.<br>· Especificación bloque 7: recorte da fase 1 e P1–P12 en «Pendente de decidir».<br>· `ens.md`, renumerado ao RD 311/2022 (op.acc.5 usuarios externos, op.acc.6 usuarios da organización): op.acc.3, op.acc.4, op.acc.6 (R5 último acceso, 6.1/6.2 obrigas, 6.6 baixa, 6.8 bloqueo), op.exp.8 (eventos e conservación), mp.per.2; pendente 1 pasa a feito. Táboa de eventos con detalles e conservación.<br>· `datos-persoais.md`: tratamento «Persoal do concello».<br>· `deseno.md` P2: os compoñentes da feature, se algún pasa a `shared/ui` | `docs/` | Ningún documento contradí o plan; `verificar.sh --parte compliance` (D44) | S | — | Arquitecto |

### 3.2 Keycloak (carril K)

| ID | Que fai | Ficheiros | Criterios de aceptación | Tam. | Depende | Rol |
|---|---|---|---|---|---|---|
| **D4-02** | **Proba técnica** nun Keycloak 26.8.1 de usar e tirar, cun fluxo `furar-browser` con LoA e códigos de recuperación. Comproba: `acr_values=furar-2fa` pide configurar o TOTP a quen non o ten; co TOTP, `amr` con `otp` tras o acceso e tras renovar; co código de recuperación, `amr` `mfa`; `auth_time` no access token; `kc_action=CONFIGURE_RECOVERY_AUTHN_CODES`; `prompt=create` + `login_hint`; se a plataforma recibe dúas veces o OTP. O resultado é unha nota en `docs/notas-realm/` co JSON exacto do fluxo e, se fai falta, a *feature* do `Dockerfile` | `docs/notas-realm/`, borrador de `deploy/keycloak/import/furar-realm.json` | Nota con cada punto OK/KO e a evidencia (claims); se LoA falla, o plan B do §1.2.9 queda escrito; ~1 GB, só, sen outro Docker á vez | S | — | Infra |
| **D4-11** | Realm de produción co fluxo validado; `requiredActions` (`CONFIGURE_TOTP`, `CONFIGURE_RECOVERY_AUTHN_CODES`); `furar-amr-mfa`; sen o grupo `persoal`. `comprobar-roles-2fa.sh`: só roles globais. `instalacion.md` §5 reescrito: a plataforma crea o concello e invita á primeira persoa xestora desde Furar; nada de roles por concello en Keycloak (sen transición desde o 2026-10-30, P5). Temas: claves novas en gl (X09). `scripts/proba-producion.sh` (D48): pertenza ACTIVE sen OTP → 403 `auth.step-up-required`; acceso con `acr_values` → configurar OTP; segredo coñecido → `amr otp` → 200; código de recuperación → `amr mfa` → 200; baixa → 403 na seguinte petición; plataforma: alta 201 e exportación **403** | `deploy/keycloak/`, `scripts/comprobar-roles-2fa.sh`, `scripts/proba-producion.sh`, `docs/instalacion.md`, `definicion-de-feito.md` (D48, X02: mesma fila) | `realm-demo.sh --check`; X02, X09, D51, D52 e **D48** en verde; D54 se se tocan `.sh` | M | D4-02, D4-05, D4-08 | Infra |
| **D4-12** | Demo: o overlay xa non trae roles nin grupos por concello. Ids fixos en `xestor-naron`, `xestor-ferrol`, `recepcion-naron` (novo, STAFF), `ex-recepcion-naron` (revogado) e `xestor-naron-otp` (TOTP coñecido). `db/demo/R__demo_staff.sql` (CC0, idempotente): pertenzas ACTIVE, unha INVITED caducada e outra vixente (`persoal-novo@example.org`, sen conta), unha REVOKED, e eventos de exemplo en `audit_event`. README de Keycloak (táboa de usuarios, segredo e QR de demo), `web/e2e/fixtures.ts` | `deploy/keycloak/demo/overlay.json`, `import-demo/`, `db/demo/`, `deploy/keycloak/README.md`, `web/e2e/fixtures.ts` | Arrinca con `dev,demo` nunha base baleira; `xestor-naron` xestiona Narón (D33 en verde sen claim); `recepcion-naron` ve reservas e non «Persoal»; `ex-recepcion-naron` → 403; `xestor-naron-otp` entra con contrasinal + código | S | D4-05, D4-07, D4-02 | Infra + Backend |

### 3.3 API (carril A)

| ID | Que fai | Ficheiros | Criterios de aceptación | Tam. | Depende | Rol |
|---|---|---|---|---|---|---|
| **D4-03** · **feita** (2026-10-28) | `RolePermissions` sen «todo para PLATFORM_ADMIN»; `roles()` admite PLATFORM_ADMIN; `MunicipalityPermission.CREATE` e `PortabilityPermission.IMPORT` coa plataforma; `AuthError.EMAIL_NOT_VERIFIED` → `auth.email-not-verified` (gl/es, web). Axustar os IT que usaban a plataforma para exportar | `shared.security`, `*Permission.java`, `MunicipalityPortabilityIT`, `messages*`, `web/src/i18n` | `RolePermissionsTest`: a fila da plataforma é exactamente a do §1.2.10 e **non** contén `booking.*`, `staff-data.view`, `indicators.view`, `facility.*`, `municipality.export`; `RESIDENT` baleiro; IT: plataforma → 403 en `GET …/bookings`, `…/indicators` e `…/export`, e `Audiences` dálle `PUBLIC`; D50 en verde (mesmos 6 tests) | S | D4-01 en revisión | Backend seguridade |
| **D4-04** | `novo-modulo.sh staff …` reducido. Migracións §2.1 con RLS. Dominio puro: `StaffMember` (estados, transicións, último xestor), `EmailAddress` (normalizar), `InvitationToken` (xerar, hash, caducidade), `SendLimits`, `StaffObligations`, `StaffError`. `StaffMemberSql` e `StaffInvitationSql` (`@NativeSql(MUNICIPALITY)`, A8b); `StaffPermission`, `AuditPermission`; `package-info` | `gal.furar.staff`, `shared.audit`, `db/migration`, `migrations.lock`, `ModularityTest`, `messages/staff_*` | Tests de dominio de cada transición, límite e caducidade (reloxo fixo); `StaffMemberSqlTenantIT`, `StaffInvitationSqlTenantIT`; `SchemaTenancyIT`, `NativeSqlRowLevelSecurityIT`, `MigrationsIT`; índices únicos (dúas invitacións ao mesmo correo → unha); `rapido.sh --tenancy api`; cobertura ≥ 80 % en `domain` | M | D4-01 | Backend PostgreSQL |
| **D4-05** | Roles efectivos: `StaffRoles` + `NoStaffRoles`, implementación en `staff` (transacción curta, caché da petición, último acceso e `staff.sign-in`); `JwtCurrentActor` (union coa táboa, `amr` `otp`\|`mfa`; desde o 2026-10-30 sen `token-staff-roles`, P5); `FurarProperties.Security`; `GET /me/staff` | `shared.security`, `shared.config`, `gal.furar.staff`, `application*.yml`, `openapi.json` | `StaffAccessIT` (tokens sen claim): ACTIVE → 200; **revogada → 403 na seguinte petición co mesmo token**; o cambio de rol vale na seguinte; persoa MANAGER en Narón e STAFF en Ferrol; ACTIVE en Narón → 403 en Ferrol; sen `otp` con `require-otp` → `auth.step-up-required`; `mfa` vale; un `auth_time` novo móvese a `previous` unha soa vez con 10 peticións simultáneas. `JwtCurrentActorTest`: o claim de concello non conta. `JwtValidationIT`, `CrossTenantAuthorizationIT`, `EndpointSecurityIT` en verde; unha soa sentenza extra por petición (contador) | M | D4-04, D4-02 | Backend seguridade |
| **D4-06** | `notification`: `StaffInvitationMailer` público; plantilla `mail/staff-invitation.{html,txt}` gl/es («Crea a túa conta»: quen invita, concello e rol nunha frase, «Crear a miña conta», o correo que hai que usar coa caducidade exacta, «se non o esperabas, ignórao», sen «darse de baixa»); `notification_delivery` para invitacións. O token non vai a ningún rexistro | `gal.furar.notification`, `resources/mail/`, migración, `ModularityTest` | Test do renderizador (escapado, gl e es, sen claves sen traducir); IT co `JavaMailSender` de proba: asunto, ligazón co fragmento, parte de texto; SMTP caído → `FAILED` sen excepción cara ao chamador; un *appender* de proba comproba que o token non aparece nos logs | S | D4-01 | Backend |
| **D4-07** | Casos de uso de xestión: listar, detalle, invitar (escribe → envía → garda a entrega), reenviar, cambiar o rol, dar de baixa. Último xestor baixo bloqueo; límites; `staff.role-not-allowed` para a plataforma; `AuditLog` con éxito e fallo (`staff.invite`, `staff.invite-resend`, `staff.role-change`, `staff.revoke`); controlador, DTO, `@VersionedResource`, `openapi.json` | `gal.furar.staff`, `openapi.json` | `StaffManagementIT`: cada transición válida e inválida; dúas baixas simultáneas dos dous últimos xestores (`CyclicBarrier`) → unha 409; 31.ª invitación en 24 h e reenvío antes de 2 min → 429 con `Retry-After`; plataforma: invita MANAGER 201 e STAFF 403; STAFF → 403 en todo; outro concello → 404; `If-Match` (412, 428); **enumeración:** mesma resposta e mesmos campos se o correo ten pertenza noutro concello ou non; auditoría sen correo nos detalles; `EndpointSecurityIT`, `ContractHeadersIT`, `ErrorCatalogTest`, `OpenApiContractIT` | M | D4-04, D4-06 | Backend |
| **D4-08** | `preview` e `accept`: hash, estado, caducidade, correo verificado e igual, xa membro, versión das obrigas; `staff.accept` (tamén fallido, co código) | `gal.furar.staff`, `openapi.json` | `StaffInvitationSecurityIT`: correo distinto → 403 `staff.invitation-email-mismatch` e a pertenza segue INVITED; sen verificar → 403 `auth.email-not-verified`; caducado (reloxo) → 410; reutilizado, substituído por un reenvío ou de pertenza revogada → 404 igual ca un token inventado; token de Narón no URL de Ferrol → 404; versión vella → 409; aceptar → ACTIVE e acceso na seguinte petición; `preview` sen sesión; o token non sae nos logs nin na resposta | M | D4-05, D4-07 | Backend seguridade |
| **D4-09** | Columna `actor_roles` e `JdbcAuditLog`; `AuditTrail` + `JdbcAuditTrail` (`GLOBAL`, motivo, keyset); casos de uso `ReadActivity` (concello) e `ReadPlatformActivity`; nomes con `StaffQueries`; filtros (datas ≤ 366 días, área = prefixo da acción, persoa, resultado) | `shared.audit`, `shared.adapter.out.persistence`, `gal.furar.staff`, migración, `openapi.json` | `ActivityIT`: o xestor de Narón só ve Narón (con eventos de Ferrol na base); ve as accións da plataforma marcadas; STAFF → 403; a plataforma só as súas; `actor_roles` correcto para MANAGER, STAFF, plataforma e persoa con dous roles; páxinas estables con eventos no mesmo instante; `DatabaseRolesIT` (sen UPDATE/DELETE), `AuditRetentionIT` | M | D4-04 | Backend |
| **D4-10** | Conservación: tarefa diaria (`@PlatformTask`) que anonimiza o correo e o nome das revogadas máis vellas ca `FURAR_AUDIT_RETENTION` e borra as invitacións non aceptadas caducadas hai máis de 30 días | `gal.furar.staff` | `StaffRetentionIT` con reloxo fixo; o rexistro di «Persoa anonimizada»; `ens.md` e `datos-persoais.md` coinciden | S | D4-07 | Backend |

### 3.4 Web (carril W)

| ID | Que fai | Ficheiros | Criterios de aceptación | Tam. | Depende | Rol |
|---|---|---|---|---|---|---|
| **D4-13** | Núcleo:<br>· `StaffAccessService` (`GET …/me/staff` por concello e signal, invalidado ao cambiar de concello).<br>· Gardas: `managerGuard`/`bookingsGuard` co rol da API e o claim como reserva só se o devolve a API; `staffGuard` (xestor ou plataforma); `activityGuard`.<br>· `SessionService.stepUp(url)` con `acr_values: 'furar-2fa'`; step-up proactivo antes da xestión; `configureRecoveryCodes()` (`kc_action`).<br>· `roles.ts`: a plataforma xa non `canManage`.<br>· Aviso do último acceso na cabeceira da xestión (texto, non `role=alert`, data con `Intl`).<br>· «Seguridade da conta» no `furar-user-menu`.<br>· Entradas «Persoal» e «Rexistro de actividade» no menú da xestión. Tras crear un concello, a plataforma vai a «Persoal» | `core/auth/`, `core/layout/`, `app.routes.ts`, `features/municipality/` (despois de crear), i18n | Specs: sen OTP e `required` → `signinRedirect` con `acr_values` unha vez cada 5 min (sen bucle); STAFF non ve «Persoal» nin «Rexistro»; a plataforma non ve «Instalacións» nin «Reservas»; o aviso só sae se hai `previousSignInAt`; `session.service.spec.ts` e `guards.spec.ts` actualizados; orzamento inicial igual (D27) | M | D4-05 | Frontend features |
| **D4-14** | `features/staff/members/`:<br>· Lista: táboa en escritorio, tarxetas en móbil; nome ou «Pendente de aceptar», correo, rol, chip de estado (Activa `success`, Convidada `warn`, Caducada `danger`, Baixa `muted`), último acceso, invitación.<br>· «Invitar» en `furar-dialog`: correo, rol con `[furarSegmented]` e idioma do correo.<br>· Accións por fila con `[furarMenu]`: Reenviar, Cambiar o rol, Dar de baixa (`furar-confirm`, que di o que perde).<br>· Erros 409/429 coa súa mensaxe; resultado en `role="status"`.<br>· Modo plataforma: só MANAGER e sen cambiar o rol | `features/staff/members/`, i18n `staff.members.*` | Specs do store e de cada acción; foco: tras invitar volve ao botón e anuncia «Invitación enviada a …»; tras pechar un diálogo, ao disparador; `If-Match` e 412 → aviso e recarga; axe gl/es; 320 px sen scroll horizontal; formulario accesible (erros con `aria-describedby`) | M | D4-07, D4-13 | Frontend features |
| **D4-15** | `features/staff/invitation/` en `/:concello/convite` (pública):<br>· Le o fragmento e o garda en `sessionStorage`; `preview`.<br>· «Entrar» (`login_hint`) e «Crear conta» (`prompt=create`).<br>· Ao volver, as obrigas e a primeira capa RGPD, con «Comprométome a cumprilas»; `accept`.<br>· Despois, step-up (OTP), códigos de recuperación e entrada na xestión.<br>· Estados: caducada, inválida, correo distinto («Entraches como X; esta invitación é para Y» + «Pechar sesión»), xa membro | `features/staff/invitation/`, `app.routes.ts`, i18n `staff.invitation.*` | Specs: o token non queda no URL nin no historial; volver de Keycloak co mesmo paso; cada erro co seu texto e unha acción; ningún «acepto os termos»; título enfocado en cada paso; axe; 320 px | M | D4-08, D4-13 | Frontend features |
| **D4-16** | `features/staff/activity/` en `/:concello/xestion/rexistro` e `/plataforma/rexistro`:<br>· Filtros: período con `furar-calendar` `range`, área e persoa con `furar-select`, «Só fallidos» con `[furarFilterChip]`; filtros na URL.<br>· Táboa (`caption`, `th scope`): data, persoa e rol, acción, sobre que (ligazón á instalación ou á reserva; nome da instalación desde o cliente xerado), resultado.<br>· «Cargar máis» co foco na primeira fila nova; baleiro e erro.<br>· Etiquetas `activity.actions.*` de todos os códigos de hoxe (inventario con `grep` no PR), con reserva | `features/staff/activity/`, i18n `activity.*` | Specs: URL ↔ filtros; código descoñecido → texto de reserva, nunca baleiro; datas con `Intl` e `Europe/Madrid`; axe; 320 px (filas apiladas) | M | D4-09, D4-13 | Frontend features |

### 3.5 Final (carril E)

| ID | Que fai | Ficheiros | Criterios de aceptación | Tam. | Depende | Rol |
|---|---|---|---|---|---|---|
| **D4-17** | `web/e2e/persoal.e2e.ts`:<br>· `xestor-naron` invita a `conserxe-<aleatorio>@example.org` como STAFF.<br>· Correo en Mailpit; rexistro en Keycloak e verificación por Mailpit; obrigas; aceptar; ve «Reservas» e non «Persoal».<br>· O xestor dálle de baixa; á seguinte navegación, 403.<br>· Reenviar dúas veces → 429; a ligazón vella → «inválida»; aceptar con `vecino-naron` → correo distinto.<br>`web/e2e/rexistro.e2e.ts`: os eventos anteriores, filtros e teclado.<br>`xestor-naron-otp` entra con TOTP calculado en Node (`crypto`, sen dependencias). A plataforma non ve reservas. VoiceOver e teclado en `docs/accesibilidade.md`; D34 coa cifra nova | `web/e2e/`, `docs/accesibilidade.md`, `definicion-de-feito.md` (D34) | `gl`, `es` e `gl-mobile` en verde con axe en cada paso e nos dous temas; ningunha petición fóra de localhost; sen `securitypolicyviolation`; o correo lido en Mailpit bórrase; `verificar.sh --estrito` en verde | M | todas | QA accesibilidade |

**Total: 17 tarefas** (1 D, 3 K, 8 A, 4 W, 1 E). Ningunha L.

### 3.6 Orde e paralelismo
- **Desde o primeiro día:** D4-01 (D) e D4-02 (K, Docker pola mañá); D4-03 e D4-06 sen Docker ata os IT; D4-04 colle o Docker despois de D4-02.
- **Camiño crítico:** D4-04 → D4-05 → D4-07 → D4-08 → D4-15 → D4-17. D4-09 en paralelo despois de D4-04. D4-11 despois de D4-02, D4-05 e D4-08 (D48 é o paso máis pesado: só, sen nada máis). D4-12 despois de D4-05 e D4-07.
- **Web:** D4-13 cando estea o contrato de D4-05; despois D4-14 (D4-07), D4-15 (D4-08) e D4-16 (D4-09). `ng test` e `ng build` por quendas.
- **Ficheiros quentes:** `JwtCurrentActor`, `RolePermissions`, `FurarProperties`, `application*.yml`, `ModularityTest`, `openapi.json`, `migrations.lock`, `app.routes.ts`, `site-bar`/`user-menu`, `session.service.ts`, `guards.ts`, `gl.json`/`es.json`, `furar-realm.json` + `overlay.json` (sempre seguidos de `realm-demo.sh`), `proba-producion.sh`, `definicion-de-feito.md`. Claves i18n só no seu prefixo (`staff.members.*`, `staff.invitation.*`, `staff.obligations.*`, `activity.*`, `ui.*`).
- **Dev local:** o realm só se importa se non existe. Despois de D4-11 e D4-12 hai que recrear o Keycloak de `dev` (avísase no PR).

| Día | Carril A | Carril K | Carril W | D/E |
|---|---|---|---|---|
| Xov 5 | D4-03 · D4-06 | D4-02 | — | D4-01 |
| Ven 6 | D4-04 | — | — | |
| Sáb 7 | D4-05 | — | D4-13 (contra o contrato) | |
| Lun 9 | D4-07 · D4-09 | — | D4-13 (remate) | |
| Mar 10 | D4-08 | D4-12 | D4-14 | |
| Mér 11 | D4-10 | D4-11 (D48, só) | D4-15 | |
| Xov 12 | correccións | — | D4-16 | |
| Ven 13 | — | — | correccións | D4-17 · `verificar.sh --estrito` |

**Poden escorregar sen romper a demo:** D4-10 (conservación), o rexistro da plataforma (`/plataforma/rexistro`), o filtro por persoa, `staff.sign-in` no rexistro. **Non escorregan:** D4-03, D4-04, D4-05, D4-07, D4-08, D4-11, D4-13, D4-14 e D4-15 (son «xestión segura de usuarios» das bases).

---

## 4. Perfís e regras comúns
Os do dominio 2 §4 cambiando «D2» por «D4». Ademais:
- **Arquitecto (D4-01):** le ADR-0005, 0006, 0007, 0017, 0018, 0020, 0025 e 0026, `ens.md` e este plan. Renumera `ens.md` ao RD 311/2022 sen inventar requisitos: o que non estea claro vai como «concello» ou «pendente». Non escribe produto novo.
- **Infra (D4-02, D4-11, D4-12):** le ADR-0007, ADR-0015, ADR-0019, `deploy/keycloak/README.md`, `proba-producion.sh` e `proba-identidade-keycloak.sh`. Un só Keycloak á vez. Todo cambio no realm leva o `realm-demo.sh` e D54 se toca `.sh`. Nunca credenciais de administración de Keycloak na API.
- **Backend seguridade (D4-03, D4-05, D4-08):** le `shared.security` enteiro, `JwtValidationIT`, `CrossTenantAuthorizationIT` e `EndpointSecurityIT`. Non hai caché de roles entre peticións. O token nunca vai a logs, eventos nin respostas. Os IT novos usan tokens **sen** `furar_roles`.
- **Backend PostgreSQL (D4-04):** le A8b, `BookingSql` e `NotificationDeliverySql`. Usa o patrón `(:all or …)` e nada de `or`, `union` ou `case` no nivel 0.
- **Backend (D4-06, D4-07, D4-09, D4-10):** SMTP nunca dentro dunha transacción; auditoría con éxito e fallo (patrón `audited` de `FacilityService`); detalles sen correo.
- **Frontend features (D4-13 a D4-16):** como D2 §4. Os textos das obrigas e a primeira capa RGPD van só en i18n, sen inventar responsables (saen da API de privacidade). Os estados das pertenzas levan a paleta de estados. Toda capa pecha con Esc e clic fóra.
- **QA (D4-17):** como D2; Mailpit en `localhost:8025`; TOTP calculado no test, sen dependencias.

---

## 5. Riscos

| Risco | Mitigación |
|---|---|
| LoA, códigos de recuperación ou `auth_time` non se portan como se espera en 26.8.1 | D4-02 antes de nada; plan B escrito (§1.2.9); D48 próbao cun Keycloak real |
| OTP dúas veces para a plataforma ou bucle de step-up | Proba en D4-02; xanela de 5 min de `SessionService` (xa probada) |
| O claim legado nos tests esconde o camiño da táboa | IT novos sen claim; `JwtCurrentActorTest` con `false`; `prod` avisa ao arrincar |
| Latencia: unha transacción máis por petición autorizada | Índice `(municipality_id, subject)`, caché da petición e contador de sentenzas en D4-05 |
| Fuga do token (logs, `Referer`, analizadores de correo) | Fragmento, `POST`, só hash, un uso, 7 días, aceptar pide sesión; test de logs |
| SMTP caído ao invitar | Entrega `FAILED` á vista e «Reenviar»; sen reintento automático (o token non se garda) |
| Concello sen xestor | Regra do último xestor baixo bloqueo; a plataforma pode invitar MANAGER |
| A plataforma invítase a si mesma como xestora e ve datos da veciñanza | Rexistrado con `actor_roles` e visible para o concello; P6 |
| O correo de Keycloak cambia despois de aceptar | A pertenza está ligada ao `sub`; o correo gardado só serve para mostrar e para a invitación |
| Cl@ve sen correo verificado | Keycloak pide e verifica o correo no primeiro acceso; se non, 403 `auth.email-not-verified` cun texto claro |
| Realm só importado na creación (dev, instalacións existentes) | Aviso no PR e `docs/notas-realm/`; sen migración dos roles, que non hai instalacións coa versión anterior (P5) |
| Conflitos en `openapi.json`, i18n, `migrations.lock` e realm | Merges en serie, rexenerar sempre, `realm-demo.sh --check` |

**Orde de merge:** D4-01 → D4-03, D4-06 → D4-04 → D4-05 → D4-07, D4-09 → D4-08, D4-13 → D4-12, D4-14 → D4-10, D4-15 → D4-16 → D4-11 → D4-17. Un merge cada vez, despois de `rapido.sh` e as probas da fila.

---

## 6. Preguntas de produto
Validadas polo mantedor o 2026-10-29 e o 2026-10-30, agás a 9: vale a recomendación ou o decidido en cada unha.

1. **¿A mesma conta pode ser de veciña e de persoal?** (op.acc.1.2). **Decidido polo mantedor (2026-10-30): non.** A conta de traballo vai á parte da persoal, en todos os concellos: con pertenza activa non reserva (`booking.staff-account`) e cunha reserva, en calquera estado e concello, non acepta unha invitación (`staff.invitation-personal-account`) nin se reactiva (`staff.personal-account`). As actividades do concello fanse con bloqueos da instalación co seu motivo. Detalle no §9. *Recomendación previa: si; cada acción queda rexistrada co rol e o concello.*
2. **¿Bloqueo permanente ou temporal tras intentos fallidos?** (op.acc.6.8). **Decidido polo mantedor (2026-10-29):** temporal, 5 intentos → 15 minutos, cun aviso xenérico tras cada fallo, máis dobre factor para o persoal ([`ens.md`](../ens.md#contrasinais-e-bloqueo)). *Recomendación previa: temporal (10 intentos, de 60 s a 900 s).*
3. **`ens.md` e a autenticación da veciñanza (op.acc.5, usuarios externos) en MEDIA.** **Decidido polo mantedor (2026-10-29):** contrasinal de 12 ou máis con maiúscula, minúscula, número e carácter especial, para todas as contas, cos requisitos en vivo no tema de acceso ([`ens.md`](../ens.md#contrasinais-e-bloqueo)). *Recomendación previa: renumerar ao RD 311/2022; veciñanza con contrasinal ≥ 12, bloqueo, correo verificado e Cl@ve/Chave365 como mecanismo de máis garantía; o concello confírmao na súa análise de riscos; OTP voluntario desde a conta.*
4. **¿Ve a plataforma o rexistro?** *Recomendación: só as súas accións (alta de concellos, importar, invitar ou dar de baixa xestores). O concello ve todo o do seu concello, tamén o que fixo a plataforma.* **Validado (2026-10-30).**
5. **Migración dos grupos e roles actuais de Keycloak.** **Decidido polo mantedor (2026-10-30):** sen transición nin migración, porque non hai ningunha instalación coa versión anterior: os roles de persoal do token non contan nunca e non existe `FURAR_STAFF_TOKEN_ROLES` ([ADR-0027](../adr/0027-persoal-e-permisos-fase-1.md), punto 4). *Recomendación previa: durante unha versión, `FURAR_STAFF_TOKEN_ROLES=true`; cada persoa é invitada e acepta; despois bórranse os roles e grupos en Keycloak e apágase a variable.*
6. **¿Que pode facer a plataforma co persoal dun concello?** *Recomendación: ver, invitar só xestores e desactivalos (alta e recuperación); nin conserxes nin cambios de rol.* Desde o 2026-10-30, tamén reactiva, só xestores (§9). **Validado (2026-10-30).**
7. **¿Pode un xestor darse de baixa a si mesmo ou baixarse o rol?** *Recomendación: si, se queda outro xestor activo.* **Decidido polo mantedor (2026-10-30):** «Dar de baixa» pasa a «Desactivar», reversible con «Reactivar», sen borrar nada; desactivar pide escribir «desactivar» e unha casiña, e baixar unha persoa xestora a conserxe, escribir «conserxe» (§9). A regra do último xestor queda.
8. **Conservación do persoal revogado.** *Recomendación: o mesmo prazo ca o rexistro (`FURAR_AUDIT_RETENTION`, 1 ano); despois anonimízanse o correo e o nome.* Hoxe: bloqueo ao ano e anonimización aos 3 desde a última desactivación (reactivar reinicia o contador, §9). **Validado (2026-10-30).**
9. **Texto das obrigas.** *Recomendación: fixo de Furar e versionado na fase 1; volver aceptalo ao cambiar de versión e un texto propio do concello quedan para despois.* **Pendente de validar.**
10. **Idioma do correo de invitación.** *Recomendación: escóllea o xestor (gl ou es); por defecto, o idioma da súa pantalla.* **Validado (2026-10-30).**
11. **¿Entra «entrou na xestión» (`staff.sign-in`) no rexistro?** *Recomendación: si, un por sesión e concello.* **Validado (2026-10-30).**
12. **Límites de envío.** *Recomendación: 30 envíos por concello en 24 h, 2 min entre reenvíos (eran 10; 2026-10-29) e 5 por persoa invitada.* **Validado (2026-10-30).**

---

## 7. Filas da DoD que se tocan
A DoD está conxelada: ningunha fila nova.

| Fila | Que cambia | Tarefa |
|---|---|---|
| D48 | Mesma fila, criterio novo: step-up con `acr_values`, configurar OTP, `amr otp`/`mfa`, baixa → 403 na seguinte petición, plataforma con exportación 403 e sen «rol MANAGER creado en Keycloak» | D4-11 |
| X02 | Roles con privilexios en Keycloak = só `*:PLATFORM_ADMIN`; o realm trae o nivel 2 | D4-11 |
| D34 | O mínimo de e2e sobe cos dous ficheiros novos | D4-17 |
| D33 | Mesmo texto: o MANAGER de `xestor-naron` sae agora da táboa (demo) | D4-12 |
| D50 | Mesmos 6 tests; a plataforma xa non exporta (403) e o xestor si | D4-03 |
| D15 | Mesmo texto: `RolePermissionsTest` coa fila nova da plataforma, rutas novas en `EndpointSecurityIT` e `CrossTenantAuthorizationIT`, `amr mfa` en `JwtValidationIT` | D4-03, D4-05 |
| D11, D12, D14, D16, D17, D20 | Seguen en verde con `StaffMemberSqlTenantIT`, `StaffInvitationSqlTenantIT`, `ModularityTest` (`staff → shared, notification`), os erros novos, o contrato e `ProdProfileGuardIT` | D4-04 a D4-09 |
| D51, D52, X09, X10 | Seguen en verde co realm novo (rexistro, Cl@ve simulada, claves en gl de OTP e códigos) | D4-11 |
| D44 | ADR-0027 | D4-01 |

---

### Critical Files for Implementation
- api/src/main/java/gal/furar/shared/security/config/JwtCurrentActor.java
- api/src/main/java/gal/furar/shared/security/RolePermissions.java
- deploy/keycloak/import/furar-realm.json (+ deploy/keycloak/demo/overlay.json)
- api/src/main/java/gal/furar/shared/adapter/out/persistence/JdbcAuditLog.java
- web/src/app/core/auth/session.service.ts (+ web/src/app/core/auth/guards.ts)

Outros ficheiros de referencia:
- api/src/main/java/gal/furar/notification/application/service/BookingMailService.java
- api/src/main/resources/db/migration/V202610262054__notification_delivery.sql
- scripts/proba-producion.sh
- docs/ens.md

Notas para quen lance o plan:
- ~~Hai probas que esperan que a plataforma exporte~~: resolto en D4-03 (`MunicipalityPortabilityIT` e `proba-producion.sh` xa esperan 403).
- ~~`ens.md` aínda usa a numeración do ENS de 2010~~: renumerado en D4-01 (op.acc.5 veciñanza, op.acc.6 persoal).
- O step-up con LoA, a compatibilidade dos códigos de recuperación en Keycloak 26.8.1 e o `auth_time` no access token non se puideron comprobar sen executar nada; por iso D4-02 vai primeiro e ten plan B.

---

## 8. Axustes tras a proba D4-02 (2026-10-28)

Resultado completo en [`notas-realm/2026-10-28-loa-codigos-recuperacion.md`](../notas-realm/2026-10-28-loa-codigos-recuperacion.md). Mandan sobre o §1.2.9 e as filas afectadas:

- **Fluxo:** o do §1.2.9 pide o OTP dúas veces á plataforma. Úsase o fluxo validado da nota (nivel 1 contrasinal; nivel 2 con subfluxo «configurado», OTP ou código como alternativas, e subfluxo «alta», OTP requirido; subfluxo «plataforma» igual). Sen `--features` no `Dockerfile`: `RECOVERY_CODES` e `STEP_UP_AUTHENTICATION` veñen activos.
- **D4-05:** a API acepta como segundo factor `acr == furar-2fa` **ou** `amr` con `otp`/`mfa`. Motivo: quen configura o TOTP no propio acceso recibe `acr=furar-2fa` e `amr=["pwd"]` (tamén ao renovar). IT co token `{"acr":"furar-2fa","amr":["pwd"]}`. O step-up cambia `auth_time`: rexistrar `staff.sign-in` unha vez por sesión de nivel 2, non no salto de nivel 1 a 2.
- **D4-11:** fluxo da nota; `requiredActions` explícitos; `furar-amr-mfa` validado. D48: catro `auth-otp-form` e casos novos (configurar o OTP no acceso, código de recuperación → `mfa`, plataforma cun só OTP). X09: «Warning alert:» en inglés na páxina dos códigos e texto do ficheiro descargado.
- **D4-13:** o step-up pide `acr_values=furar-2fa` sen `prompt=login`; entrar na xestión pedindo xa o nivel 2 para ter un só `auth_time`. Na invitación, `login_hint` enche o correo, no acceso e (desde P13) tamén no rexistro; o texto da pantalla dío. Despois de aceptar, `kc_action=CONFIGURE_RECOVERY_AUTHN_CODES` (probado con sesión aberta).
- ~~**P13:** ¿`registrationEmailAsUsername: true` para que o correo saia xa posto no rexistro?~~ **Decidido (2026-10-29): si.** O correo é o usuario para toda a veciñanza e o persoal; as contas vellas seguen entrando co seu usuario e a Cl@ve crea a conta co correo do IdP. [ADR-0027](../adr/0027-persoal-e-permisos-fase-1.md) punto 6 e [`notas-realm/2026-10-29-correo-como-usuario.md`](../notas-realm/2026-10-29-correo-como-usuario.md).

---

## 9. Revisión do 2026-10-30: conta de traballo e desactivar

Decisións do mantedor sobre P1 e P7 ([especificación](../produto/especificacion.md), bloque 7; [ADR-0027](../adr/0027-persoal-e-permisos-fase-1.md), revisión do 2026-10-30). Mandan sobre o §1.2.8, o §1.2.14, o §2 e o §6.

- **Desactivar en vez de dar de baixa.** O estado `REVOKED` pasa a `DEACTIVATED`, `revoked_at`/`revoked_by` a `deactivated_at`/`deactivated_by` (migración `V202610301000__staff_member_deactivation.sql`, que tamén renomea os checks e o índice único do correo), o permiso `staff.revoke` a `staff.deactivate`, a ruta `POST /staff/{id}/revoke` a `POST /staff/{id}/deactivate` e o evento `staff.revoke` a `staff.deactivate` (a web segue lendo os `staff.revoke` gravados como «Desactivar»).
- **Reactivar:** `POST /staff/{id}/reactivate` (`If-Match`, permiso `staff.deactivate`; desde o 2026-11-02, a plataforma tamén reactiva conserxes). `DEACTIVATED → ACTIVE` co mesmo rol, a mesma persoa e as obrigas que aceptou, sen invitación nova; o segundo factor, coma sempre (o rol sae da táboa en cada petición). Quita `deactivated_at`, así que o bloqueo (1 ano) e a anonimización (3 anos) contan desde a seguinte desactivación. Non vale se nunca aceptou (`staff.transition-invalid`: invítase de novo), se está anonimizada (`staff.anonymised`), se hai outra pertenza viva co mesmo correo ou a mesma persoa (`staff.already-member`, polos índices únicos) nin se a conta reservou mentres tanto (`staff.personal-account`). Unha bloqueada si se reactiva: o bloqueo só a esconde mentres está desactivada. Evento `staff.reactivate` (`memberId`, `role`).
- **Non se borra nada ao desactivar:** nin a conta de Keycloak (a API non ten credenciais de administración), nin a pertenza (`furar_app` non ten `DELETE` en `staff_member`), nin o rexistro. Co tempo só borra a conservación de sempre: as invitacións sen usar aos 30 días de caducar (tamén a que substitúe a desactivación dunha invitada), os seus envíos e os eventos aos 3 anos; o correo e o nome anonimízanse aos 3 anos da última desactivación.
- **Confirmacións escribindo** (`furar-type-to-confirm`, `shared/ui`): desactivar pide «desactivar» e unha casiña obrigatoria («Entendo que non se borra a conta nin o seu historial: o acceso pódese reactivar e os datos consérvanse segundo a política de conservación»; para unha invitada, «… a invitación deixa de valer, que se pode invitar de novo…»); baixar unha persoa xestora a conserxe pide «conserxe»/«conserje». A acción queda con `aria-disabled` (patrón `furarAction`) ata que coincide, sen distinguir maiúsculas nin tildes.
- **Conta de traballo (P1):** `booking` non depende de `staff` (`staff → notification → booking` faría un ciclo). A dependencia invértese cun porto (ADR-0005): `booking` declara a SPI `booking.StaffAccounts` (`isStaffAccount(sub)`, con `NoStaffAccounts` sen o módulo) e `staff` implementaa; `staff` pregunta pola API pública `BookingQueries.hasResidentBookings(sub)`. Así `staff → booking` é a única frecha nova (`staff → shared, notification, booking`). As dúas lecturas cruzan concellos con funcións `security definer` que só devolven si ou non (`is_active_staff_account`, `has_resident_bookings`); as clases que as chaman son `@NativeSql(GLOBAL)` sen táboas no SQL. `GET …/me/staff` trae `workAccount`, e a reserva pública avisa antes e desactiva «Confirmar reserva».
- **Actividades municipais:** con bloqueos da instalación co seu motivo (bloque 2); nada novo que construír.
- **Probas:** `StaffMemberTest`, `StaffReactivationIT`, `WorkAccountIT`, `StaffRetentionIT` (reactivar reinicia o contador), `StaffManagementIT`, `ContractHeadersIT` e `CrossTenantAuthorizationIT` coa ruta nova; na web, `type-to-confirm.component.spec.ts`, `confirm.component.spec.ts` e os specs de «Persoal» e da reserva; e2e en `persoal.e2e.ts` (desactivar escribindo, reactivar, baixar a conserxe) e `persoal-contas.e2e.ts` (a última xestora, a conta de traballo e a conta con reservas).

