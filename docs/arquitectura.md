<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Arquitectura de Furar

**En unha frase:** un único programa Java dividido en módulos que non se pisan, unha web Angular xerada contra o contrato da API, PostgreSQL e Keycloak; todo se levanta cun comando e cada regra importante compróbaa un test que rompe o build.

- Decisións razoadas: [`adr/`](adr/) (un ficheiro curto por decisión).
- Que significa «feito»: [`definicion-de-feito.md`](definicion-de-feito.md).
- Engadir un módulo: [`como-engadir-un-modulo.md`](como-engadir-un-modulo.md).
- Que fai o produto (acordado coa persoa responsable de produto): [`produto/especificacion.md`](produto/especificacion.md).
- Termos galego/castelán ↔ inglés: [`glosario.md`](glosario.md).

## 1. Pezas

```
 navegador ──► Caddy :8080 ─┬─ /         → web (Angular compilada, estática)
                            ├─ /api/*    → api :8081 (Spring Boot; actuator :9081 interno: health e metrics)
                            ├─ /auth/*   → keycloak :8082 (OIDC, mesma orixe)
                            ├─ /tiles/*  → servidor de teselas (sen cookies, Referer nin IP de quen mira)
                            └─ /actuator → 404
 api ──► PostgreSQL (base furar)      keycloak ──► PostgreSQL (base keycloak)
 api ──► SMTP (Mailpit na demo)
 api ──► CartoCiudad (buscador de enderezos; proxy opcional; FURAR_GEOCODER=fixed sen rede)
 migrate (unha vez, como furar_owner) ──► Flyway; despois arrinca api (furar_app, sen DDL)
```

| Peza | Tecnoloxía | Cartafol |
|---|---|---|
| API | Java 25 · Spring Boot 4.1.1 · Spring Modulith 2.1.1 | `api/` |
| Web | Angular 22.2 (standalone, signals, zoneless) · Tailwind 4.3 · CDK · Transloco 8 | `web/` |
| Datos | PostgreSQL 18.6 · Flyway 12.4 | `api/src/main/resources/db/migration/` |
| Identidade | Keycloak 26.8 (OIDC, código + PKCE) | `deploy/keycloak/` |
| Proxy e estáticos | Caddy 2.11.7 | `deploy/caddy/Caddyfile`, `web/Dockerfile` |
| Despregue | Docker Compose, perfís `dev` e `full` | `compose.yaml`, `deploy/` |
| Comprobacións | `scripts/verificar.sh` e CI | `scripts/`, `.github/workflows/` |

Versións exactas e fontes: [ADR-0004](adr/0004-versions-fixadas.md).

## 2. Repositorio

```
api/            Spring Boot (Maven Wrapper). openapi.json = contrato versionado
web/            Angular. src/app/api/ xérase do contrato (non se versiona)
deploy/         keycloak (realm de demo), postgres (init), produción
docs/           arquitectura, ADR, manual, glosario (CC0-1.0)
scripts/        verificar.sh, novo-modulo.sh, probar-*.sh, allowed-licenses.txt
templates/      plantilla de módulo (api e web)
LICENSES/       textos EUPL-1.2, CC0-1.0, Apache-2.0, CC-BY-4.0 e OFL-1.1 (REUSE)
compose.yaml    perfís dev e full
```

## 3. API: monolito modular hexagonal

Cada módulo de negocio é un **módulo de Spring Modulith** (`gal.furar.<modulo>`) e, por dentro, **portos e adaptadores**. Detalle e regras exactas: [ADR-0005](adr/0005-monolito-modular-hexagonal.md).

```
gal.furar.<modulo>/
  package-info.java        @ApplicationModule(allowedDependencies = {...})
  *.java                   API pública do módulo: XxxQueries + records, eventos (A19: sen tipos internos)
  domain/                  Java puro: agregados, valores, eventos, erros
  application/port/in/     casos de uso (interfaces) + comandos (records)
  application/port/out/    o que o caso de uso necesita de fóra (interfaces)
  application/service/     implementación dos casos de uso
  adapter/in/web/          controladores REST, DTO (records), mapeadores
  adapter/out/persistence/ entidades JPA, repositorios, mapeadores
gal.furar.shared/          módulo aberto: concello actual, erros, seguridade, IDs, hora
```

- **Entre módulos:** só a API pública (paquete raíz) e eventos. Evento se o outro reacciona despois; consulta síncrona (`XxxQueries`, records) se precisa o dato na mesma transacción. Ninguén le nin bloquea as táboas doutro: a única excepción son as FK compostas cara á raíz doutro módulo (`municipality`) ou cara a táboas publicadas, en lista pechada (`CrossModuleForeignKeys`; nas reservas, `booking` → `facility_use` e `facility_use_part`), ADR-0005 e ADR-0012. Para bloquear unha instalación, `booking` chama a `FacilityQueries.lockFacility(id)`; non toca a táboa. Modulith e A19 fallan no build polo código; `SchemaTenancyIT`, polas FK.
- **Dominio rico e puro:** sen Spring, JPA, Jackson nin Lombok; sen setters; invariantes no construtor e nos métodos.
- **Persistencia separada:** a entidade JPA vive no adaptador e mapéase ao dominio. Custa un mapeador por agregado; a cambio, o dominio pódese probar sen base e a API nunca devolve unha entidade.

### Módulos

| Módulo (código) | Contido | Estado |
|---|---|---|
| `shared` | Concello actual, erros, seguridade, IDs, hora, rexistro de actividade, portabilidade | Feito |
| `municipality` | Concello: slug, nome, idiomas, tema, datos legais opcionais (DIR3, DPD, accesibilidade), exportación e importación, a publicación dos datos abertos, o interruptor «Buscar xente para xogar» e as súas horas ([ADR-0040](adr/0040-buscar-xente-para-xogar.md)) | Feito |
| `customfields` | Campos personalizados por concello | Feito |
| `facility` | Instalación (datos, estado, enderezo, imaxes e biblioteca do concello, deportes, servizos), usos e partes; no subpaquete `schedule` de cada capa, tempadas, semana, excepcións, festivos e bloqueos | Feito ([ADR-0022](adr/0022-instalacions-horario-e-orde-de-bloqueo.md)) |
| `policy` | Normas de reserva e herdanza | **Fóra da fase 1** (chega coa herdanza): as catro normas da fase 1 viven no uso, en `facility` ([ADR-0025](adr/0025-reservas-fase-1.md)) |
| `pricing` | Tarifas, colectivos, exencións | **Fóra da fase 1** (chega coa herdanza e os colectivos): o prezo da fase 1 vive no uso, en `facility` |
| `booking` | Reserva, ocupación, estado e pago, código e QR, `.ics`, xestión e calendario, `BookingImpact` real; a asistencia (validar o QR, «non presentado» manual ou pola tarefa `NoShowService` e bloqueo por faltas, [ADR-0032](adr/0032-non-presentados-e-validacion.md)); a lista de espera (`booking_wait`, como moito 3 esperas por conta, o evento `BookingSlotReleased` cando unha hora esperada queda libre e a tarefa `WaitExpiryService`, que ao pasar a hora deixa só un reconto anónimo) e os ocos parecidos por regras, detrás do porto `SlotRecommender` ([ADR-0033](adr/0033-lista-de-espera-e-ocos-parecidos.md)); as valoracións (`booking_review`, unha por reserva á que se foi, públicas e anónimas, [ADR-0037](adr/0037-valoracions.md)); as entidades e as súas tempadas (`booking_organization`, `booking_series`, `booking_series_request`: as sesións son reservas da entidade, sen correo de titular, [ADR-0039](adr/0039-tempada-de-entidades.md)) e as partidas abertas (`booking_open_match`, quen se une sen reserva e a tarefa `OpenMatchDeadlineService`, [ADR-0040](adr/0040-buscar-xente-para-xogar.md)) | Fase 1 feita ([ADR-0025](adr/0025-reservas-fase-1.md)) |
| `people` | O que é da conta dunha persoa e non dun concello: as persoas a cargo (fichas con nome, ano de nacemento e a declaración «está ao meu cargo», `/api/v1/me/dependents`) e as contas coñecidas polo seu correo verificado (`account_email`), en táboas de plataforma sen concello; a SPI `shared.security.VerifiedAccounts` e a súa conservación; desde o 2026-11-17, eliminar a propia conta (`/api/v1/me/account-deletion` e, para Keycloak, `/actuator/accountdeletion` e `/actuator/accountclosed`; a SPI `shared.account.AccountClosureStep`, que implementa `booking`, [ADR-0043](adr/0043-eliminar-a-propia-conta.md)). Despois, o perfil da veciñanza (dereitos, RIDEGA); **non** o persoal nin as entidades (son dun concello: van en `booking`, [ADR-0039](adr/0039-tempada-de-entidades.md)) | Persoas a cargo feitas ([ADR-0031](adr/0031-persoas-a-cargo.md)): SQL nativo, fóra de `furar-export` |
| `staff` | Persoal do concello: pertenzas (correo, rol `MANAGER`\|`STAFF`, estado; desactivar e reactivar), invitación por correo (aceptada ao volver de Keycloak, sen obrigas que aceptar), último acceso, roles efectivos para a seguridade (`StaffRoles`), administración da plataforma (`PlatformAdmins`, táboa `platform_admin` sen concello, primeiro acceso con `FURAR_ADMIN_EMAIL`, [ADR-0028](adr/0028-administracion-da-plataforma-e-primeiro-acceso.md)), conta de traballo (`booking.StaffAccounts`) e lectura do rexistro de actividade | Fase 1 feita ([ADR-0027](adr/0027-persoal-e-permisos-fase-1.md)): SQL nativo con RLS, fóra de `furar-export` |
| `notification` | Avisos e modelos por idioma; na fase 1, catro correos fixos en gl e es (confirmada, cambiada, cancelada pola persoa e polo concello) e, desde o 2026-11-10, o de cada falta ([ADR-0032](adr/0032-non-presentados-e-validacion.md)) e «Quedou libre», o aviso a quen esperaba por unha hora ([ADR-0033](adr/0033-lista-de-espera-e-ocos-parecidos.md)); desde o 2026-11-11, «Lista de espera cancelada» e o recordatorio da reserva ([ADR-0035](adr/0035-recordatorio-por-correo.md)), a invitación do persoal (`StaffInvitationMailer`, síncrona) e `notification_delivery`; desde o 2026-11-12, os da tempada dunha entidade (asignada, rematada, solicitude non aprobada, [ADR-0039](adr/0039-tempada-de-entidades.md)) e os das partidas abertas (cancelada, cambiada, solicitude nova, aceptada, non aceptada e oculta, [ADR-0040](adr/0040-buscar-xente-para-xogar.md)); desde o 2026-11-17, «¿Es un concello?»: `POST /api/v1/municipality-join-requests`, pública e sen concello, envía a solicitude de alta por correo a quen opera o servizo (`FURAR_JOIN_REQUEST_EMAIL`), no idioma do formulario; só se o correo non sae, gárdaa en `join_request` (táboa da plataforma) e `JoinRequestRetryService` reinténtaa ata 7 días ([ADR-0042](adr/0042-es-un-concello.md), revisión do 2026-11-17) | Fase 1 feita (sen pantalla propia: a de «¿Es un concello?» é da web) |
| `indicators` | Indicadores do concello: cifras, gráficos e as súas táboas; o CSV e o PDF xéraos a web. Os da plataforma (desde o 2026-11-11): o panel de cada concello no seu contexto e os totais. Despois, RIDEGA, asistencia, demanda sen atender e indicadores da Deputación; publica `IndicatorsQueries` (a ocupación por cuartos de hora, para `opendata`) | Fase 1 feita ([ADR-0026](adr/0026-indicadores-fase-1.md)): sen táboas propias |
| `opendata` | Datos abertos: instalacións cos seus usos, peches e ocupación por cuartos de hora en CSV, JSON e GeoJSON, a ficha de catálogo e a tarefa nocturna `OpenDataRefreshService`; por concello e de toda a instalación | Feito ([ADR-0034](adr/0034-datos-abertos.md)): SQL nativo con RLS, fóra de `furar-export` |
| `incident` | Incidencias: a veciñanza avisa dun problema nunha instalación (tipo, texto e ata 3 fotos) e Xestión e Recepción cambian o estado e resólvena; correos pola API pública de `notification` ([ADR-0036](adr/0036-incidencias.md)) | Feito |
| `event` | Eventos do concello: o concello publica eventos deportivos puntuais e a veciñanza apúntase ou pide praza; prazas sen carreiras (bloqueo do evento), pago no mostrador e correos pola API pública de `notification` ([ADR-0038](adr/0038-eventos.md)); actividades con horario (sesións que ocupan a instalación, lista de espera automática e asistencia, [ADR-0041](adr/0041-actividades-con-horario.md)) | Feito |
| ~~`recommendation`~~ | Ocos parecidos ao pedido: non será un módulo; van dentro de `booking`, detrás do porto `SlotRecommender` ([ADR-0033](adr/0033-lista-de-espera-e-ocos-parecidos.md)) | Feito, dentro de `booking` |
| `platform` | Novidades, aviso de versión | Pendente (o rexistro de actividade está en `shared.audit`, ADR-0020) |

Dependencias (Modulith verifícaas): `shared` ← todos; `customfields` (só depende de `shared`) ← `municipality`, `facility`, `booking` (e `people` cando exista); na fase 1, `booking` → `shared`, `facility`, `municipality`, `customfields` (campos da reserva) e, desde o 2026-11-10, `people` (as persoas a cargo da conta, `PeopleQueries`; `people` só depende de `shared` e `booking` implementa a súa SPI `DependentUsage`, [ADR-0031](adr/0031-persoas-a-cargo.md)) e `notification` → `shared`, `booking`, `facility`, `municipality` (só escoita eventos de `booking`, despois do commit); máis adiante, `booking` → `policy`, `pricing`, `people`. **`staff` → `shared`, `notification`, `booking`, `municipality`** (o nome dos concellos da persoa; [ADR-0027](adr/0027-persoal-e-permisos-fase-1.md)): implementa as SPI `shared.security.StaffRoles` (os roles de concello saen da súa táboa en cada petición; `shared` trae `NoStaffRoles` para os tests de módulo illado) e `shared.security.PlatformAdmins` (a administración da plataforma, igual; `NoPlatformAdmins`) e chama a `notification.StaffInvitationMailer` despois do commit; `notification` non depende de `staff`. Como a conta de traballo vai á parte da persoal, `staff` le `booking.BookingQueries.hasResidentBookings` e implementa a SPI `booking.StaffAccounts` (sen ela, `NoStaffAccounts`): `booking` non depende de `staff`, porque `staff → notification → booking` faría un ciclo. O persoal non vai en `people`: o control de acceso non debe depender do ciclo de vida dos datos da veciñanza. **`indicators` → `shared`, `booking`, `facility`, con lectura síncrona na fase 1** ([ADR-0026](adr/0026-indicadores-fase-1.md); e `municipality` desde o 2026-11-11, o nome de cada concello nos indicadores da plataforma): compón `BookingStatistics` (agregados de reservas xa filtrados polo concello; o SQL vive en `booking`) e `FacilityQueries` (horas abertas e usos), sen táboas propias nin proxección por eventos; os eventos e as táboas agregadas de ADR-0006 chegan cos indicadores da Deputación; unha entidade conta como unha persoa en «persoas distintas» ([ADR-0039](adr/0039-tempada-de-entidades.md)). **`opendata` → `shared`, `facility`, `indicators`, `municipality`** ([ADR-0034](adr/0034-datos-abertos.md)): só serializa. Le as instalacións cos seus usos, o nome e o modo dos usos que cita a ocupación, os peches e os festivos de `FacilityQueries`, a ocupación por cuartos de hora de `IndicatorsQueries` (o mesmo peso de cada modo ca o panel) e a publicación e a entidade que se cita de `MunicipalityQueries`. Ten dúas táboas propias sen datos de quen reserva (`open_data_occupancy`, cunha FK composta a `facility` en `CrossModuleForeignKeys`, e `open_data_dataset`), con RLS e SQL nativo. **Normas e prezo no uso (fase 1, [ADR-0025](adr/0025-reservas-fase-1.md)):** `facility` valídaos e publícaos en `FacilityQueries.use(useId)` (`UseTerms`: `price`, `rules`); `booking` avalíaos. **A persoa que reserva non ten rol no concello:** os seus casos de uso levan `@PersonUseCase` (sesión e correo verificado; filtran polo `sub`). **No mostrador, a reserva é do correo** ([ADR-0031](adr/0031-persoas-a-cargo.md)): `shared.security.VerifiedAccounts` (impleméntaa `people`) garda que conta entrou con que correo verificado, e `shared.security.PersonArrival` (impleméntaa `booking`) pásalle á conta as reservas feitas a nome dese correo cando a persoa visita o concello; as dúas chámaas `ActorPreloadInterceptor` antes do controlador. **Non hai módulo `calendar`:** o horario e os bloqueos van dentro de `facility`, así «¿pódese reservar aquí e agora?» ten un só dono e un só bloqueo.

**`facility` en catro ideas** ([ADR-0022](adr/0022-instalacions-horario-e-orde-de-bloqueo.md)): (1) **orde de bloqueo única**: todo o que cambia a reservabilidade (estado, usos, horario, excepcións, bloqueos) toma primeiro `select … for no key update` da fila da instalación (`FacilityLock`), e `booking` toma o mesmo antes de escribir a ocupación; (2) **unha SPI cara a `booking`**, `BookingImpact` (`affectedBy`, `apply`), cun só bean: `facility` trae `NoBookingImpact` e `booking` substitúeo; os avisos saen despois do commit por evento; (3) **modal de reservas afectadas en dous pasos** sobre o mesmo endpoint: 409 `facility.affected-bookings` con `affected[]` e reenvío con `decisions[]`; (4) **borrador calculado**, non gardado: `missing ∈ {SPORTS, USES, SCHEDULE}`. Cada sección da ficha garda no seu endpoint con `If-Match`. Enderezos e mapa: [ADR-0023](adr/0023-enderezos-e-mapa-sen-terceiros.md).

## 4. Multiconcello

Unha base, un esquema; cada táboa de negocio leva `municipality_id`. O concello sae do URL (`/api/v1/municipalities/{slug}/...`; na web, `/naron/...`; slug inmutable), un filtro fíxao para a petición e Hibernate (`@TenantId`) filtra e enche a columna só. Sen concello, o adaptador lanza un erro e, por debaixo, Hibernate usa un concello inexistente: nunca devolve datos de todos. Os eventos levan o concello. Na base, as referencias son FK compostas `(municipality_id, x_id)` e, para todo SQL (tamén o nativo de `booking`), **Row Level Security** por concello, activa en toda táboa do concello: o concello fíxase ao comezar cada transacción e `SchemaTenancyIT` falla se unha táboa nova non a trae. Sen SQL en anotacións de Hibernate (A22). Cinco tests o garanten. [ADR-0006](adr/0006-multiconcello.md).

**Portada común** ([ADR-0030](adr/0030-portada-comun.md)): con varios concellos, a raíz da web mostra as instalacións de todos. A API dáas en tres rutas públicas sen concello, por páxinas de concellos: `GET /api/v1/catalog/facilities` (`facility`), `GET /api/v1/catalog/availability` (`booking`) e `GET /api/v1/catalog/open-matches` (`booking`, as partidas abertas, [ADR-0040](adr/0040-buscar-xente-para-xogar.md)). Un concello sae só se ten os datos legais completos e algunha instalación activa á vista (decídeo a API; non hai botón de publicar). Ningunha consulta ve dous concellos á vez: o porto `shared.tenancy.CatalogMunicipalities` (impleméntao `municipality`) dá os concellos e cada un lese co seu `TenantContext.runAs` e a súa transacción, coa RLS de sempre; `facility` segue sen depender de `municipality`.

**Datos abertos** ([ADR-0034](adr/0034-datos-abertos.md)): cada concello publica tres conxuntos sen datos de quen reserva en `GET /api/v1/municipalities/{slug}/open-data/…` e a instalación enteira, os dos concellos publicados que o teñen aceso, en `GET /api/v1/catalog/open-data/…`, sen concello no URL. O conxunto de todos lese coma a portada común: concello a concello, cada un no seu `TenantContext.runAs` e na súa transacción. A ocupación sae dunha táboa que enche unha tarefa cada noite; a lectura pública non toca as reservas. A descarga é síncrona, no fío da petición, por tramos curtos en transaccións propias (nunca noutro fío: `TenantContext` é dun fío), cun tope de descargas de ocupación á vez. Só os GET dos ficheiros e da ficha levan `Access-Control-Allow-Origin: *`.

## 5. Seguridade

Pechada por defecto: toda ruta pide sesión salvo as marcadas `@PublicEndpoint`. Keycloak autentica (na mesma orixe, `/auth`); **cada caso de uso** declara o seu permiso (`@RequiresPermission`, `@PublicUseCase` ou, para o que unha persoa fai sobre o seu sen rol no concello, `@PersonUseCase`; regra ArchUnit) e cada módulo declara os seus permisos e a súa fila de roles nun `enum` propio; `RolePermissions` xúntaos nunha matriz. **Roles:** ningún vén do token. `*:PLATFORM_ADMIN` sae da táboa `platform_admin` do módulo `staff` en cada petición; a primeira administración é a conta co correo verificado de `FURAR_ADMIN_EMAIL` mentres non hai ningunha ([ADR-0028](adr/0028-administracion-da-plataforma-e-primeiro-acceso.md)). Só ten o que o nomea: alta e importación de concellos, persoal e o seu rexistro e, desde o 2026-11-03, a configuración do concello (datos e datos legais, instalacións, campos personalizados e rexistro de actividade); nunca reservas, indicadores, exportación nin datos da veciñanza ([ADR-0028](adr/0028-administracion-da-plataforma-e-primeiro-acceso.md) punto 9). Os roles de concello (`MANAGER`, `STAFF`) saen da táboa do módulo `staff` en cada petición: a baixa vale na seguinte; un `naron:MANAGER` no token non dá nada ([ADR-0027](adr/0027-persoal-e-permisos-fase-1.md)). Desde o 2026-11-09 o persoal e a plataforma entran tamén co código dunha aplicación de autenticación: Keycloak pregunta á API en cada acceso quen o necesita (`POST /actuator/secondfactor`, porto interno), e a API segue sen mirar `acr` nin `amr` ([ADR-0029](adr/0029-segundo-factor-do-persoal.md)); desde o 2026-11-17, tamén se se pode eliminar unha conta e que xa se eliminou, con outra clave ([ADR-0043](adr/0043-eliminar-a-propia-conta.md)); D48 e D51 próbano cun Keycloak real. As accións sensibles (exportar e importar o concello, escrituras de instalacións, cobros e cancelacións desde a xestión e o persoal) quedan en `audit_event` cos roles de quen actúa (só INSERT e SELECT para `furar_app`, visibles `FURAR_AUDIT_VISIBLE`, 1 ano, e despois bloqueados ata `FURAR_AUDIT_RETENTION`, 3 anos; ADR-0020); o xestor leo na pantalla «Rexistro de actividade» e exporta os bloqueados cun motivo. A API só acepta tokens do cliente `furar-web` (`azp`; a demo, tamén `furar-e2e`). Tests: todos os endpoints sen token, un xestor dun concello contra outro e tokens con emisor, audiencia ou cliente errados. [ADR-0007](adr/0007-seguridade-pechada.md). Autenticación pública: Cl@ve e Chave365 como provedores SAML de Keycloak; a demo entra xa por «Cl@ve simulada» sen gardar o NIF (D52, [ADR-0019](adr/0019-autenticacion-publica-clave.md)).

## 6. Contrato e erros

- `/api/v1`, JSON. springdoc xera o OpenAPI; un test compárao co `api/openapi.json` versionado; a web xera o cliente TS dese ficheiro antes de compilar. Se cambia a API, a web non compila. [ADR-0009](adr/0009-contrato-api-cliente-xerado.md).
- Erros RFC 9457 (`application/problem+json`) cun `code` estable e traducible (`municipality.slug-taken`) e `errors[]` por campo. [ADR-0008](adr/0008-erros-rfc9457.md).

## 7. Datos

Flyway con SQL plano; IDs UUIDv7 xerados en Java; marcas de tempo en UTC (`timestamptz`), mostradas en Europe/Madrid; `jsonb` só para textos traducidos e tema. **Horario e bloqueos son hora de reloxo de parede:** na API, `date` + `startMinute` + `endMinute` (0–1440, múltiplos de 15); o servidor converte os bloqueos a `tstzrange` en Europe/Madrid e dá `today` e `now` para que o navegador non calcule zonas. Catálogos de plataforma (`sport_catalog`, `public_holiday`, `drawing`) sen `municipality_id`, en `TenancyAllowlist` e de só lectura para `furar_app`. Fotos na base (`bytea` con `storage external`, táboa aparte do metadato), nunha biblioteca por concello que reutilizan as instalacións; os debuxos de Furar, no classpath ([ADR-0024](adr/0024-biblioteca-de-imaxes.md)). Orde alfabética no servidor coa colación ICU do idioma de quen mira. [ADR-0010](adr/0010-persistencia.md), [ADR-0006](adr/0006-multiconcello.md).

## 8. Campos personalizados

Definicións por concello no módulo `customfields` (só depende de `shared`), en **táboas tipadas** (especificación, bloque 1b): `custom_field` (código, tipo, nome e axuda gl/es, regras tipadas, dato persoal), `custom_field_option`, `custom_field_usage` (onde se usa, con obrigatorio e visibilidade; desde o 2026-11-04, **un campo, un sitio**: un só uso por definición), `custom_field_value` (unha columna por tipo; a base garante o tipo e que a opción é do campo) e `custom_field_value_option` (selección múltiple). `entity_id` non ten FK porque é polimórfico: RLS, comprobación no almacén e borrado polo módulo dono no seu caso de uso (`CustomFieldValueStore.deleteAll`; para o RGPD, `personalData` e `erasePersonalData`, aínda sen camiño que os chame). O módulo dono valida con `CustomFieldValidator`, garda con `CustomFieldValueStore.replace` na mesma transacción e le por lote con `loadAll`; no dominio segue o tipo `CustomFieldValues` (só o crea o validador). Sen orde manual nin grupos: listas alfabéticas. JSON Schema xerado para terceiros; formulario dinámico accesible na web. Campos desactivados (non arquivados): seguen gardados e lexibles, pero non entran en formularios nin valores novos. Destinos: Concello, Instalación e Reserva (`BOOKING`, só `OWNER` ou `STAFF`). [ADR-0011](adr/0011-campos-personalizados.md).

## 9. Ocupación e reservas

A instalación é o que se reserva, cos seus **usos**: enteira, por partes con nome ou por prazas. **A base garante, declarativamente, un uso por instalación e franxa e o cupo:** exclusión por instalación e intervalo con uso distinto; enteira, unha reserva por franxa; por partes, unha por parte; con prazas, contador por franxa con `CHECK (taken <= capacity)` e **unha praza por persoa** (exclusión en `booking_participant` por uso, persoa e intervalo: unha reserva leva de 1 a 11 persoas, quen a ten e as súas persoas a cargo ou acompañantes, [ADR-0031](adr/0031-persoas-a-cargo.md)); FK compostas instalación–uso–parte e modo. Bloqueos, peche, horario, «bloquea os demais» e as normas **non** van na exclusión: compróbaos `booking` baixo o bloqueo da instalación (§3), que toma antes de comprobar nada ao reservar, cambiar ou cancelar. Así unha reserva «mantida» no modal segue na ocupación e o bloqueo simplemente non a toca, e o límite por persoa conta sen carreiras. Os ocos empezan na apertura de cada tramo, en pasos da duración do uso. Cambiar é unha transacción sobre a mesma reserva; o prezo e as normas quedan conxelados nela; estado (confirmada, cancelada pola persoa, polo concello), pago (pendente, pagada, a devolver, devolta) e, desde o 2026-11-10, asistencia (sen marca, validada, non presentado; [ADR-0032](adr/0032-non-presentados-e-validacion.md)) van separados: unha reserva validada ou con falta segue confirmada e ocupando a súa hora. Validar e marcar a falta toman só o bloqueo da fila da reserva (`for update`), porque non tocan a ocupación; a penalización das faltas é por concello e por correo (`booking_no_show_block`, con RLS). **Reservar é idempotente:** o cliente manda unha `Idempotency-Key` por intención de reservar e, baixo o mesmo bloqueo, a mesma clave coa mesma petición devolve a reserva xa creada en vez de crear outra (a clave gárdase na mesma transacción e dura 24 h; ADR-0025 punto 19). O modelo SQL próbase con concorrencia real (`OccupancyModelIT`, sobre as migracións de produción). [ADR-0012](adr/0012-ocupacion-exclusiva-e-prazas.md), [ADR-0022](adr/0022-instalacions-horario-e-orde-de-bloqueo.md), [ADR-0025](adr/0025-reservas-fase-1.md).

**Reservas dunha entidade** ([ADR-0039](adr/0039-tempada-de-entidades.md)): unha reserva pode ter de titular unha entidade (`organization_id`), sen conta, correo nin teléfono; as sesións dunha tempada son reservas así (`series_id`, sen prezo), creadas todas nunha transacción baixo o bloqueo da instalación e só nos días libres. Ningún camiño da persoa (ligar ao entrar, límite, faltas, recordatorio) as toca. A persoa responsable dunha entidade non ten rol: a pertenza compróbase no caso de uso polo seu correo verificado. Orde de bloqueo: instalación → entidade → solicitude ou serie.

**Partidas abertas** ([ADR-0040](adr/0040-buscar-xente-para-xogar.md)): quen se une a unha reserva de pista enteira ou por partes non ocupa nada nin ten reserva; as prazas da partida cóntanse baixo o bloqueo da súa fila. Os camiños que tocan a reserva (cancelar, cambiar, o prazo) toman instalación → reserva → partida; ningún toma a partida e despois a reserva.

## 10. Web

Angular standalone con signals; `core/` · `shared/` · `features/<modulo>/`; Tailwind con variables CSS do tema do concello; só CDK (nin Material nin Flowbite); gl e es en JSON; lint de TS e de modelos con regras de accesibilidade; Playwright + axe en gl e es. [ADR-0014](adr/0014-web.md). Sistema de deseño (tokens `--f-*` derivados do tema con AA garantido, compoñentes de `shared/ui/`): [`deseno.md`](deseno.md), [ADR-0021](adr/0021-sistema-de-deseno.md). Gráficos só con `furar-chart`, sobre Apache ECharts cargado ao primeiro gráfico, en canvas e sen estilos en liña ([ADR-0026](adr/0026-indicadores-fase-1.md)).

## 10b. Datos persoais e portabilidade

Papeis por modelo de despregue (concello responsable; Deputación encargada; conta común: a Deputación por recomendación), base 6.1.e e cláusula informativa na páxina «Privacidade» de cada concello cos seus datos legais e, no rexistro, unha frase xenérica que liga a ela (D51), menores sen conta, prazos de conservación e modelos de RAT e encargo: [ADR-0017](adr/0017-datos-persoais.md) e [`datos-persoais.md`](datos-persoais.md). Cada concello pode levar os seus datos nun JSON documentado (`furar-export`), con CSV e iCalendar: [ADR-0018](adr/0018-portabilidade.md). Como o adopta un concello ou unha Deputación: [`reutilizacion.md`](reutilizacion.md). Os datos abertos ([ADR-0034](adr/0034-datos-abertos.md)) son outra cousa: só o público, sen permiso e sen datos de quen reserva, para reutilizar.

**IA:** Furar non usa IA nin servizos externos de IA. A recomendación de ocos («ocos parecidos», en `booking`, [ADR-0033](adr/0033-lista-de-espera-e-ocos-parecidos.md)) é por regras (a mesma instalación noutra hora, unha parecida á mesma hora) detrás dun porto propio (`SlotRecommender`); se algún día leva un modelo, será de pesos abertos e autoaloxable, ou substituíble só cambiando o adaptador, e sen enviar datos persoais a terceiros. Tampouco hai recursos de terceiros na web (fontes, analítica, CDN, mapas): todo se serve desde o propio despregue; o buscador de enderezos pasa pola API e as teselas do mapa por Caddy ([ADR-0023](adr/0023-enderezos-e-mapa-sen-terceiros.md)).

## 11. Despregue e operación

En local: construír as imaxes en serie e `docker compose --profile full up -d --wait` (≤ 3,5 GB nun equipo de 8 GB). Un concello non compila: `compose.prod.yaml` usa as imaxes publicadas pola CI, fixadas por digest, e esixe os segredos. Servizo `migrate` dun só uso (Flyway como `furar_owner`) antes da API, que conecta como `furar_app` sen DDL. Imaxes con JRE, non root, `cap_drop: ALL`, `no-new-privileges`, sistema de ficheiros de só lectura onde se pode, `HEALTHCHECK`, límites de memoria e licenzas de terceiros dentro; Caddy publícase só en 127.0.0.1 por defecto; realm sen usuarios en produción; copia e restauración con script. Perfil `dev`: só PostgreSQL, Keycloak e Mailpit; API e web en local con recarga. [ADR-0015](adr/0015-despregue.md).

## 12. Calidade comprobable

Probas por capa ([ADR-0013](adr/0013-estratexia-de-probas.md)); cada garda ten unha proba que a rompe a propósito; `scripts/rapido.sh` (< 3 min, sen Docker) en cada cambio; `scripts/verificar.sh` executa todo en orde e a CI (GitHub; GitLab para forks) chama ao mesmo script, con Actions e imaxes fixadas por digest ([ADR-0016](adr/0016-ci-e-verificacion.md)); as comprobacións con comando e resultado esperado están en [`definicion-de-feito.md`](definicion-de-feito.md).

## Estado

- **Feito e probado:** `municipality`, `customfields`, `facility`, `booking`, `notification`, `indicators`, `staff` e `opendata` ([ADR-0034](adr/0034-datos-abertos.md), datos abertos) de punta a punta (API, web, e2e con axe; os catro últimos na súa fase 1); multiconcello con `@TenantId`, FK compostas e RLS en todas as táboas do concello (`TenantRlsSpringIT`, `SchemaTenancyIT`); roles de BD separados; acceso con correo e contrasinal e, para o persoal e a plataforma, o código dunha aplicación de autenticación ou do correo (ADR-0029); rexistro de actividade; portabilidade (`furar-export` v5); plantilla de módulo (`novo-modulo.sh`, D45) con permisos, mensaxes e destinos de campos propios do módulo.
- **Sen facer:** o perfil da veciñanza de `people` (as persoas a cargo xa están, [ADR-0031](adr/0031-persoas-a-cargo.md)) e o módulo `platform` (os ocos parecidos e a lista de espera van en `booking`, [ADR-0033](adr/0033-lista-de-espera-e-ocos-parecidos.md)); na web, os scopes de Transloco por feature (`novo-modulo.sh` edita `gl.json`, `es.json` e `app.routes.ts`).
- **Repositorio:** GitHub; a CI corre en cada push a `main`, nos PR, nas etiquetas e cada día; nun fork privado, só nos PR, nas etiquetas e a man ([ADR-0016](adr/0016-ci-e-verificacion.md)). En local, `scripts/verificar.sh` executa o mesmo.

## Índice de ADR

Para ler en dez minutos: **0005, 0006, 0011, 0012, 0022, 0025, 0007, 0008, 0016 e 0018** (o resto son convencións). Os ADR non se renumeran nin se fusionan: son un rexistro, e unha decisión que cambia revísase no seu ficheiro (ADR-0001).

| Nº | Decisión |
|---|---|
| [0001](adr/0001-rexistro-de-decisions.md) | Rexistro de decisións en ADR curtos |
| [0002](adr/0002-monorepo-licenzas-reuse.md) | Monorepo, EUPL-1.2 + CC0-1.0, REUSE, só dependencias OSI/FSF |
| [0003](adr/0003-idioma.md) | Código en inglés, documentación en galego, app en gl e es |
| [0004](adr/0004-versions-fixadas.md) | Versións exactas e verificadas |
| [0005](adr/0005-monolito-modular-hexagonal.md) | Monolito modular hexagonal e regras que rompen o build |
| [0006](adr/0006-multiconcello.md) | Multiconcello con `@TenantId` e concello no URL |
| [0007](adr/0007-seguridade-pechada.md) | Seguridade pechada por defecto; Keycloak autentica, os casos de uso autorizan |
| [0008](adr/0008-erros-rfc9457.md) | Erros RFC 9457 con código traducible |
| [0009](adr/0009-contrato-api-cliente-xerado.md) | OpenAPI versionado e cliente TS xerado |
| [0010](adr/0010-persistencia.md) | Flyway, UUIDv7, UTC e convencións SQL |
| [0011](adr/0011-campos-personalizados.md) | Campos personalizados: definicións por concello e valores en táboas tipadas |
| [0012](adr/0012-ocupacion-exclusiva-e-prazas.md) | Ocupación: un uso por instalación e franxa, enteira, por partes ou por prazas |
| [0013](adr/0013-estratexia-de-probas.md) | Estratexia de probas por capa |
| [0014](adr/0014-web.md) | Web: estrutura, idiomas, tema e accesibilidade |
| [0015](adr/0015-despregue.md) | Despregue con Compose, non root, ≤ 3,5 GB |
| [0016](adr/0016-ci-e-verificacion.md) | CI pública, dous carrís e cadea de subministración |
| [0017](adr/0017-datos-persoais.md) | Datos persoais: papeis, base xurídica, conservación e menores |
| [0018](adr/0018-portabilidade.md) | Portabilidade dos datos por concello |
| [0019](adr/0019-autenticacion-publica-clave.md) | Autenticación pública: Cl@ve e Chave365 como IdP SAML, probado con «Cl@ve simulada» |
| [0020](adr/0020-rexistro-de-actividade.md) | Rexistro de actividade: táboa só de engadir e porto `AuditLog` |
| [0021](adr/0021-sistema-de-deseno.md) | Sistema de deseño: tokens por función, cores derivadas con AA garantido e compoñentes xenéricos |
| [0022](adr/0022-instalacions-horario-e-orde-de-bloqueo.md) | Instalacións, horario e orde de bloqueo: un módulo `facility`, SPI única cara a `booking`, borrador calculado |
| [0023](adr/0023-enderezos-e-mapa-sen-terceiros.md) | Enderezos e mapa sen terceiros: CartoCiudad polo servidor e teselas por Caddy |
| [0024](adr/0024-biblioteca-de-imaxes.md) | Imaxes das instalacións: biblioteca do concello, debuxos de Furar e debuxo suxerido |
| [0025](adr/0025-reservas-fase-1.md) | Reservas, fase 1: normas e prezo no uso, persoa sen rol, unha orde de bloqueo e avisos despois do commit |
| [0026](adr/0026-indicadores-fase-1.md) | Indicadores, fase 1: módulo `indicators` de lectura síncrona, gráficos con ECharts no navegador e exportación sen servidor |
| [0027](adr/0027-persoal-e-permisos-fase-1.md) | Persoal e permisos, fase 1: módulo `staff`, roles da táboa en cada petición e invitación propia |
| [0028](adr/0028-administracion-da-plataforma-e-primeiro-acceso.md) | Administración da plataforma na base de Furar e primeiro acceso sen consola |
| [0030](adr/0030-portada-comun.md) | Portada común: as instalacións de todos os concellos na raíz, sen escoller concello nin botón de publicar |
| [0031](adr/0031-persoas-a-cargo.md) | Persoas a cargo: fichas na conta, unha reserva con N prazas e, no mostrador, a reserva é do correo |
| [0032](adr/0032-non-presentados-e-validacion.md) | Validación do QR e non presentados: asistencia á parte do estado, falta automática ou manual e bloqueo por concello |
| [0033](adr/0033-lista-de-espera-e-ocos-parecidos.md) | Lista de espera e ocos parecidos: horas ocupadas á vista, aviso a todas á vez e alternativas por regras |
| [0034](adr/0034-datos-abertos.md) | Datos abertos: instalacións, peches e ocupación agregada, por concello e de toda a instalación |
| [0035](adr/0035-recordatorio-por-correo.md) | Recordatorio por correo: 24 horas antes do prazo, unha vez, e baixa desde o propio correo |
| [0036](adr/0036-incidencias.md) | Incidencias: a veciñanza avisa dun problema e o persoal resólveo |
| [0037](adr/0037-valoracions.md) | Valoracións: unha por reserva á que se foi, públicas e anónimas, e o xestor oculta comentarios |
| [0038](adr/0038-eventos.md) | Eventos do concello: a veciñanza apúntase, con prazas, prezo e aprobación opcionais |
| [0039](adr/0039-tempada-de-entidades.md) | Tempada das entidades: as sesións son reservas da entidade, nunca pisan nada e a entidade ten acceso propio |
| [0040](adr/0040-buscar-xente-para-xogar.md) | Buscar xente para xogar: unha reserva aberta a outras persoas, con mínimo, máximo e prazo do concello |
| [0041](adr/0041-actividades-con-horario.md) | Actividades con horario: sesións que ocupan a instalación coma unha tempada, lista de espera automática e asistencia |
| [0042](adr/0042-es-un-concello.md) | «¿Es un concello?»: a solicitude de alta vai por correo a quen opera o servizo, sen gardala |
| [0043](adr/0043-eliminar-a-propia-conta.md) | Eliminar a propia conta: sen nada pendente, Keycloak borra a conta e a API limpa; as reservas de pago quedan ata o seu prazo |
