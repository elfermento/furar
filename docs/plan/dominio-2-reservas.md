<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Plan do dominio 2: reservas, fase 1

> **Histórico: manda a especificación e as ADR.** Este plan guiou a construción do dominio e non se mantén ao día; se algo non coincide coa [especificación](../produto/especificacion.md) ou coas [ADR](../adr/), valen elas.

**Estado:** executado (histórico). Era a proposta inicial, v1 · 2026-10-26 · do 27 de outubro ao 5 de novembro. Fonte de produto: [especificación](../produto/especificacion.md), bloques 3, 4 e 5, revisados para a fase 1 (cambios aplicados en D2-01) e as decisións do 2026-10-24 ao 2026-10-26. Onde este plan e a especificación non coincidan, manda a especificación. O que falta de produto está no §6 e **non se decide ao construír**: constrúese o valor por defecto marcado e cámbiase cando o mantedor responda.

Lectura obrigatoria para cada tarefa: este ficheiro, as regras do seu perfil (§4) e a súa fila (§3). As regras comúns de traballo (carrís, lock de Docker, ficheiros quentes, regra de rebase das migracións) son as do [plan do dominio 1](dominio-1-instalacions.md) §2.1 e §3.5, e non se repiten.

---

## 1. Alcance técnico

**Fase 1 = o básico, ben feito** (decisión do 2026-10-26): a persoa reserva e o concello pon a pista. Todo o que non está nesta lista é unha capa posterior (§1.3).

**Entra (construído e probado de punta a punta):**
- **Veciñanza:** listado público «Instalacións» (busca qué + día, chips de filtro, Lista · Mapa) e ocos nas tarxetas da portada · ficha pública con prezos, «Antes de reservar» e «Reservar» · páxina de reservar (Que · Data · Hora con duración · Parte; «Continuar» sen sesión, «Confirmar reserva» con ela) · «Reserva confirmada» (QR co código, prazo exacto para cancelar de balde, «Engadir ao calendario») · «As miñas reservas» (Próximas, Historial, Cambiar, Cancelar, Ver QR).
- **Correo fixo gl/es** con QR e `.ics` en catro casos: confirmada, cambiada, cancelada pola persoa, cancelada polo concello. Textos de Furar, co nome, o logo (ou escudo xenérico) e o contacto do concello; todos recordan o prazo para cancelar.
- **Concello:** no modal do uso, prezo (Sen prezo · Gratis · De pago, por reserva ou por persoa) e catro normas (antelación mín./máx., duración máxima, límite por persoa, cancelar sen custo), baleiras por defecto (baleira = non se aplica), e os días da semana no «Cando» · normas da instalación en texto libre · contacto do servizo de deportes e textos de pago e devolución nos axustes do concello.
- **Cobro no mostrador:** pendente → pagada; cancelada estando pagada → «a devolver» → «devolta».
- **Xestión:** pantalla «Reservas» (últimas, cobros, próximas con filtros e detalle) e «Calendario» (día, semana, mes) · modal de reservas afectadas con reservas reais (`BookingImpact`).
- **Regras:** unha persoa = unha praza na mesma franxa dun uso por prazas; os ocos empezan na apertura de cada tramo, en pasos da duración do uso; cambiar nunha soa transacción; sen reter ocos.

**Non entra (capas posteriores, na orde do 2026-10-26, sen data):** require aprobación (solicitada, aprobada, rexeitada) · exento · colectivos (quen reserva, prezo por colectivo) · reservas a nome doutra persoa (persoal no mostrador, «persoas ao meu cargo», menores e idades nos usos) · apertura programada · non presentados, penalizacións, «usada» co QR e xustificar falta · prezo por franxa e tipo de día, luz · identidade verificada por norma · compensar con outra data e lista para Tesourería · plantillas de correo editables e recordatorios · lista de espera e suxestións de oco · participantes e partido aberto · series · herdanza de normas e tarifas concello → instalación → uso · `booking` en `furar-export` e iCalendar do concello (ADR-0018) · baixa e anonimización automática das reservas (ADR-0017, chega con `people`).

### 1.1 Que xa existe e que se rehace

| Peza | Estado a 2026-10-26 | Que pasa | Onde |
|---|---|---|---|
| Ficha pública (`features/facility/public/`, `GET …/public/facilities/{id}`) | Feita: foto, descrición, «Que se pode reservar» (sen prezo), servizos, mapa, horario e contacto | Engade prezo por uso, «Antes de reservar» (normas, pago e normas da instalación) e o botón «Reservar» (maqueta 06). `PublicUseResponse` gaña `id`, partes con `id`, prezo e normas | D2-02, D2-11 |
| Portada (`features/municipality/home/`) | Feita sen ocos nin «Reservar»/«As miñas reservas» | Chip «N ocos hoxe» nas tarxetas; o buscador leva ao listado público; entradas na barra | D2-14 |
| Listado público (maqueta 16) | Non existe: o buscador baixa á sección da portada | Páxina `/:concello/instalacions` | D2-14 |
| Modal do uso (`features/facility/uses/`) | Nome, «Se aluga», partes, duración, «Cando» (sempre / só unhas datas) | Pasa á maqueta 21: «Cando» como campo-resumo con días da semana, «Duración e prezo» e «Normas» en acordeón | D2-02, D2-11 |
| `BookingImpact` / `NoBookingImpact` / `FacilityChange` | SPI feita con `affectedBy`, `apply`, `hasBookings` e as sete variantes | `booking` achega o bean real; `NoBookingImpact` queda para `@ApplicationModuleTest(STANDALONE)` | D2-08 |
| `FacilityQueries` | `exists`, `find`, `summary`, `lockFacility`, `openingIntervals`, `exclusiveUseOn` | Engádense `use`, `uses`, `visibleIds` e `OpeningInterval.alignFromMinute` (§1.2.6) | D2-02 |
| Modal de reservas afectadas (`features/facility/affected-bookings/`) | Feito e probado cun dobre | Sen cambios: agora recibe reservas reais | D2-08, D2-17 |
| `OccupancyModelIT` + `db/occupancy-model/` | Modelo só de test | Corre sobre `db/migration`; o esquema de proba bórrase no mesmo cambio (condición de entrada de D19) | D2-03 |
| Correo | Só Keycloak (Mailpit en `dev` e `full`); a API ten `SPRING_MAIL_*` no compose pero non a dependencia | `spring-boot-starter-mail` e módulo `notification` | D2-09 |
| `Role.RESIDENT` | Existe, pero ninguén o recibe ao rexistrarse | Non se usa para reservar (§1.2.3) | D2-03 |
| `qr-sample.ts` da portada | QR decorativo que non codifica nada | Queda; os QR de verdade saen da API | — |
| `docs/arquitectura.md` §3 (módulos `policy` e `pricing`) | Previstos | **Non se crean na fase 1**: normas e prezo viven no uso (§1.2.1) | D2-01 |

### 1.2 Decisións técnicas (non son produto)

1. **Normas e prezo son do uso, en `facility`.** Edítanse no modal do uso e gárdanse co seu `If-Match`; `facility` valídaas e publícaas en `FacilityQueries.use(useId)` como records da raíz (`UseTerms`: `price`, `rules`). `booking` avalíaas (dominio puro). Non se crean `policy` nin `pricing`: sen herdanza (capa posterior), un módulo máis só engadiría unha SPI e unha consulta entre módulos para catro números. Cando chegue a herdanza, `UseTerms` pasa a ser «as efectivas» e a firma non cambia.
2. **Dous módulos novos, con `scripts/novo-modulo.sh`:** `booking` (`booking Booking bookings reservas`) e `notification` (`notification Notification notifications avisos`; sen pantalla: a parte web xerada quítase seguindo `como-engadir-un-modulo.md`). Dependencias: `booking → shared, facility, municipality`; `notification → shared, booking, facility, municipality`. `notification` só escoita eventos de `booking` con `@Async` e `@TransactionalEventListener` (despois do commit, nun fío propio, como moito dous envíos á vez), co reintento do rexistro de publicacións que xa existe; non usa `@ApplicationModuleListener` porque abre unha transacción por evento e, nunha rafeira, os envíos lentos ao SMTP esgotaban o grupo de conexións ([ADR-0025](../adr/0025-reservas-fase-1.md) punto 2). `ModularityTest` (M3) coa lista nova.
3. **A persoa que reserva non ten rol no concello.** Ninguén recibe `slug:RESIDENT` ao rexistrarse e a conta é común a todos os concellos (ADR-0017). Por iso os casos de uso da persoa levan `@PersonUseCase` (novo en `shared.security`): pide sesión e `email_verified = true`, en calquera concello, e o caso de uso filtra por `sub`. A regra ArchUnit pasa a «cada caso de uso leva `@RequiresPermission`, `@PublicUseCase` ou `@PersonUseCase`». Descartado dar `RESIDENT` implícito no concello do URL: rompería `Audiences.forMunicipality` (SEC-03). Os datos de quen reserva cópianse do token na reserva (`sub`, nome, correo, idioma); non hai teléfono (P7).
4. **Permisos de xestión** (`BookingPermission`, matriz en `RolePermissions`): `booking.view` (MANAGER, STAFF), `booking.payment` (MANAGER, STAFF: cobrar é traballo do mostrador), `booking.cancel` (MANAGER). Os interruptores do conserxe (bloque 7) son outra capa.
5. **Orde de bloqueo (ADR-0022 punto 4).** Reservar, cambiar e cancelar abren unha transacción e chaman primeiro a `FacilityQueries.lockFacility(facilityId)`; despois comproban, baixo o bloqueo: instalación activa e visible (`visibleIds`), reserva por internet, uso vivo e vixente ese día e ese día da semana, `exclusiveUseOn`, que o oco cabe enteiro nun tramo aberto e aliñado, as normas; e só entón escriben `booking`, `booking_occupancy` e `use_slot`. O límite por persoa conta baixo o mesmo bloqueo (o uso é dunha soa instalación), así dúas reservas simultáneas da mesma persoa non pasan o límite. A base segue garantindo «un uso por franxa e o cupo» aínda que o código fallase.
6. **Aliñación dos ocos** (decisión do 2026-10-26: «desde a apertura de cada tramo»): o primeiro oco empeza no inicio do tramo da tempada, festivo ou excepción, e os seguintes en pasos de `slot_minutes`; un oco vale se cabe enteiro no tramo e non o pisa un bloqueo. Como `openingIntervals` devolve os tramos xa cortados polos bloqueos, `OpeningInterval` gaña `alignFromMinute` (o inicio do tramo orixinal): despois dun bloqueo, os ocos seguen a grella do tramo, non o fin do bloqueo. A duración é de 1 a N franxas seguidas dentro do mesmo anaco aberto (N = duración máxima; sen norma, ata o fin do anaco).
7. **Horas.** A reserva é un intervalo real: `period tstzrange` na base e `start`/`end` `Instant` na API. A dispoñibilidade devolve `today`, `now` e, por oco, `start` (`Instant`) e `startMinute` (reloxo de parede en Europe/Madrid) para pintar sen calcular zonas; a web formatea os `Instant` con `Intl.DateTimeFormat` e `timeZone: 'Europe/Madrid'`. Os días dos períodos (`día`, `semana` de luns a domingo, `mes`) calcúlanse en Europe/Madrid.
8. **Cambiar = unha transacción sobre a mesma reserva** (decisión do 2026-10-24, revisada o 2026-10-26): baixo o bloqueo, a ocupación vella desactívase e `use_slot` descóntase, avalíanse as normas **sen ela** (o límite por persoa non a conta), escríbese a ocupación nova e actualízase a fila `booking` (mesmo `id` e código, `version + 1`, fila en `booking_event` co período de antes e o de despois). Calquera erro desfai todo e a reserva queda como estaba. Só na mesma instalación (P2), así hai un só bloqueo.
9. **Sen reter ocos** (2026-10-24): consultar non garda nada. «Continuar» sen sesión leva a Keycloak co estado na URL (`?uso=&data=&hora=&franxas=&parte=`) e o `returnUrl` que xa existe volve ao mesmo paso. Se ao confirmar o oco xa non está, 409 `booking.slot-taken` con `alternatives[]` (as tres horas libres máis próximas do mesmo uso e día, maqueta 13).
10. **«Tanto me ten» nas partes:** Furar asigna a primeira parte libre pola orde das partes. **Prazas:** cada reserva é unha praza (`seats = 1`) e a base impide que a mesma persoa teña dúas no mesmo uso e franxa (exclusión en `booking` por `use_id`, `holder_subject` e `period`, só `SEATS` e confirmadas).
11. **Prezo conxelado:** a reserva garda `price_kind` (`NONE` sen prezo, `FREE` gratis, `PAID` de pago), `amount_cents` (importe × franxas; nas prazas, por persoa e franxa) e as normas aplicadas en columnas (`rule_*`) e `cancel_until timestamptz` calculado ao reservar ou cambiar. Cambiar as tarifas ou as normas do uso non toca as reservas xa feitas. Sen `jsonb` (ADR-0010: só textos traducidos e tema).
12. **Estados.** `status`: `CONFIRMED`, `CANCELLED_BY_PERSON`, `CANCELLED_BY_MUNICIPALITY` («Realizada» é calculado: confirmada e xa rematada). `payment_status`: `NONE` (gratis ou sen prezo), `PENDING`, `PAID`, `TO_REFUND`, `REFUNDED`. Transicións no dominio: `PENDING → PAID` (e desfacer, P13), cancelar con `PAID` → `TO_REFUND`, `TO_REFUND → REFUNDED` (e desfacer). Cada transición, fila en `booking_event` e, se a fai o persoal, `AuditLog` (ADR-0020).
13. **Código e QR.** Código `FUR-XXXX` (alfabeto base32 sen 0/O/1/I, único por concello, reintento ao chocar). O QR só leva o código (na fase 1 ninguén o le: «usada» co QR é capa posterior). Xérao a API con **ZXing `core` 3.5.3** (Apache-2.0, sen dependencias, versión de 2024): o QR ten corrección de erros Reed-Solomon e un fallo deixaría a alguén no mostrador sen código; escribilo á man custa máis e próbase peor. A API devolve o QR como `path` SVG e tamaño (`qr: {size, path}`) e a web píntao con `furar-qr` (sen dependencia na web nin `innerHTML`); o correo leva o mesmo QR en PNG incrustado (`cid:`), porque moitos clientes de correo non mostran SVG.
14. **`.ics` escrito á man** (RFC 5545, ~80 liñas en `booking.adapter.out.calendar`): `VCALENDAR`/`VEVENT` con `UID` = id da reserva, `SEQUENCE` = versión (o cambio substitúe o evento), `DTSTART`/`DTEND` en UTC, `SUMMARY`, `LOCATION`, `DESCRIPTION` co código, `METHOD:PUBLISH` (`CANCEL` ao cancelar), escapado e pregado a 75 octetos, `CRLF`. Unha biblioteca de calendarios trae moito máis do que fai falta para un evento. Test con ficheiros esperados, cambio de hora incluído.
15. **Correo.** `spring-boot-starter-mail` (versión do BOM de Spring Boot; Jakarta Mail e Angus Mail, EPL-2.0 ou GPL-2.0 con Classpath exception, xa permitidas; D37 xa trae o aviso EPL). Plantilla HTML fixa da maqueta 29 (CSS en liña, táboas, ancho 600, versión de texto) en `notification/src/main/resources/mail/`, enchida cun renderizador propio de ~40 liñas que escapa todo valor (`HtmlUtils.htmlEscape`); textos en `mail/notification_{gl,es}.properties` (non en `messages/`, que o catálogo de erros percorre enteiro), no idioma gardado na reserva. Sen Thymeleaf nin MJML: as plantillas editables son outra capa e traerán o seu motor. Envío despois do commit; un SMTP caído non desfai a reserva e o rexistro de publicacións reinténtao. `notification_delivery` garda que se enviou, a quen, cando e o resultado (bloque 2: «rexístrase a quen se avisou, como e cando»). Remitente: `FURAR_SMTP_FROM` co nome do concello como nome visible.
16. **«Engadir ao calendario».** Na web, `GET …/me/bookings/{id}/calendar.ics` descargado co token (blob). No correo, o `.ics` vai adxunto e o botón leva a «Ver a miña reserva» na web (unha ligazón sen sesión ao `.ics` precisaría un token asinado: non compensa na fase 1).
17. **`BookingImpact` real** (D2-08): `affectedBy` devolve as reservas confirmadas e futuras que pisa cada variante de `FacilityChange` (`Closure` e `Withdrawal`: todas as da instalación; `Block` e `ScheduleChanged`: as que pisan o período ou os tramos perdidos; `ExclusiveUse`: as doutros usos neses días e días da semana; `UseChanged` e `UseArchived`: as do uso). `apply` cancela (`CANCELLED_BY_MUNICIPALITY`, `PAID → TO_REFUND`, ocupación inactiva) ou mantén, garda a decisión en `booking_event` (quen, cando, canles) e publica `BookingCancelled` con `channels`; `notification` só envía se hai `EMAIL`. `WEB` = a reserva aparece «cancelada polo concello» nas súas reservas (sen campá na fase 1); `PHONE` só queda anotado. `hasBookings` mira `booking` enteira (tamén canceladas).
18. **Ocos para listas** (portada e listado): `GET …/public/availability?date=` calcula por lote, para as instalacións visibles da páxina, o número de ocos libres e as tres primeiras horas, nunha consulta de ocupación por páxina; sen caché na fase 1 (o volume dunha demo e dun concello non o pide). A ficha e a páxina de reservar usan a dispoñibilidade dun uso e un día.
19. **Web.** Unha feature `features/booking/` con subcartafoles `reserve/`, `mine/`, `manage/`, `calendar/`, cada páxina con `loadComponent` propio (o orzamento inicial non se move); o listado público vive en `features/facility/public/list/` e a portada en `features/municipality/home/`, que só usan o cliente xerado. Rutas: `/:concello/instalacions` (listado), `/:concello/instalacions/:id/reservar`, `/:concello/reservas` (as miñas), `/:concello/xestion/reservas` e `/:concello/xestion/calendario`. Sen dependencias novas na web.
20. **Paleta de estados e chips de filtro** (2026-10-26). Os tons `success` `#2a7d3f`, `warn` `#a05d0e`, `danger` `#c9372c`, `info` `#2c64cc` e `muted` `#5f646b`, con letra branca nos dous temas (≥ 4,5:1; en escuro, recheos escuros que o manteñan). Mapa: Confirmada e Pagada `success`, Pendente `warn`, A devolver e Cancelada `danger`, Gratis `info`, Devolta `muted` (P11). Excepción a P1-9 **só** para o chip de filtro (botón con `aria-pressed`): sen marcar, fondo do 9 % do ton, bordo do 28 % e letra no ton; marcado, recheo con degradado lixeiro, letra branca e sombra suave; 16 px e 40 px de alto mínimo. As etiquetas de estado seguen `solid`/`outline` (14 px). Nota en ADR-0021 e en `deseno.md` P1-9, e `tag-color.spec.ts` admíteo só no selector do chip de filtro e comproba 4,5:1 nos dous estados.

---

## 2. Modelo de datos e API

### 2.1 Táboas

Mesmas regras ca o dominio 1 (§2.1 daquel plan): `municipality_id`, `unique (municipality_id, id)`, FK compostas con `municipality_id` nos dous lados, RLS e política `<táboa>_tenant` na mesma migración, `version` onde hai agregado, `V{AAAAMMDDHHMM}__{modulo}_{que}.sql` coa hora real e regra de rebase.

**`facility`** (D2-02)

| Táboa | Columnas novas | Garantías |
|---|---|---|
| `facility_use` | `weekdays smallint` (máscara L=1 … D=64; por defecto 127), `price_kind` (`NONE`, `FREE`, `PAID`; por defecto `NONE`), `price_cents int`, `rule_min_advance_hours int`, `rule_max_advance_days int`, `rule_max_slots int`, `rule_cap_count int`, `rule_cap_period` (`DAY`, `WEEK`, `MONTH`), `rule_cancel_hours int` | `check (weekdays between 1 and 127)`; `check ((price_kind = 'PAID') = (price_cents is not null))`, `price_cents between 1 and 100000`; normas nulas ou en rango (antelación mín. 0–720 h, máx. 1–365 días, franxas 1–16, límite 1–50, cancelar 0–720 h); `check ((rule_cap_count is null) = (rule_cap_period is null))` |
| `facility` | `house_rules jsonb` (normas da instalación, `{gl, es}`) | como `description` (`LocalizedText` + `LanguagePolicy`), ≤ 2.000 caracteres por idioma |

**`municipality`** (D2-02)

| Táboa | Columnas novas | Garantías |
|---|---|---|
| `municipality` | `sports_service_name`, `sports_address`, `sports_phone`, `sports_email`, `payment_note jsonb`, `refund_note jsonb` | lonxitudes; correo con formato; as notas nulas usan o texto por defecto de Furar («No mostrador, ao chegar», «O concello dirache como cha devolve»; P8) |

**`booking`** (D2-03)

| Táboa | Columnas clave | Garantías |
|---|---|---|
| `booking` | `code`, `facility_id`, `use_id`, `part_id`, `rental_mode`, `period tstzrange`, `slots`, `status`, `payment_status`, `price_kind`, `amount_cents`, `cancel_until`, `rule_*` (as aplicadas), `holder_subject`, `holder_name`, `holder_email`, `holder_language`, `cancelled_at`, `cancelled_by`, `created_at`, `updated_at`, `version` | `unique (municipality_id, code)`; FK `(municipality_id, facility_id, use_id)`, `(municipality_id, use_id, part_id)`, `(municipality_id, use_id, rental_mode)` (lista pechada de `CrossModuleForeignKeys`); checks de estado, pago e coherencia (`NONE` ⇔ `price_kind <> 'PAID'`; `TO_REFUND`/`REFUNDED` só canceladas); `exclude using gist (use_id with =, holder_subject with =, period with &&) where (status = 'CONFIRMED' and rental_mode = 'SEATS')`; índices `(municipality_id, holder_subject, lower(period))`, `(municipality_id, facility_id, lower(period))`, `(municipality_id, payment_status) where payment_status in ('PENDING', 'TO_REFUND')` |
| `booking_occupancy` | o modelo de `OccupancyModelIT` (ADR-0012), con `booking_id` FK `(municipality_id, booking_id)` | as tres exclusións; `seats = 1` sempre na fase 1; `active = false` ao cancelar ou cambiar |
| `use_slot` | o do modelo | `check (taken between 0 and capacity)`; `capacity` copiada do uso ao crear a fila; o uso por prazas non baixa de capacidade con reservas futuras (P1 do dominio 1) |
| `booking_event` | `booking_id`, `kind` (`CREATED`, `CHANGED`, `CANCELLED_BY_PERSON`, `CANCELLED_BY_MUNICIPALITY`, `KEPT`, `PAID`, `REFUNDED`, `PAYMENT_UNDONE`), `at`, `actor_subject`, `from_period`, `to_period`, `channels text[]` | só `insert` e `select` para `furar_app` (como `audit_event`) |

**`notification`** (D2-09)

| Táboa | Columnas clave | Garantías |
|---|---|---|
| `notification_delivery` | `booking_id`, `kind` (`CONFIRMED`, `CHANGED`, `CANCELLED_BY_PERSON`, `CANCELLED_BY_MUNICIPALITY`), `recipient`, `language`, `status` (`SENT`, `FAILED`), `error`, `sent_at` | sen FK a `booking` (outro módulo, fóra da lista pechada); `unique (municipality_id, booking_id, kind, event_id)` para que un reintento non envíe dúas veces |

Datos persoais (`holder_*`, `recipient`): en `docs/datos-persoais.md` coa conservación de ADR-0017 (2 anos; 5 se houbo pago). O borrado e a anonimización chegan con `people`.

### 2.2 API (alto nivel)

Prefixo `/api/v1/municipalities/{slug}`. Erros RFC 9457 `booking.*` en gl e es.

| Método e ruta | Quen | Que |
|---|---|---|
| `GET /public/facilities` · `/{id}` | `@PublicEndpoint` | Como agora; os usos levan `id`, partes con `id`, `price {kind, amountCents, per: BOOKING\|PERSON}`, `rules` e `weekdays`; a ficha leva `houseRules`, `paymentNote` e `bookable` (activa, visible, reserva por internet) |
| `GET /public/facilities/{id}/availability?useId=&date=` | `@PublicEndpoint` | `today`, `now`, `slots[] {start, startMinute, maxSlots, freeParts[] \| freeSeats}`; só datas dentro da antelación; `show_free_slots` sen reserva por internet: ocos sen botón |
| `GET /public/availability?date=&page=` | `@PublicEndpoint` | Por instalación visible: `freeCount` e `firstStarts[3]` (portada e listado) |
| `POST /me/bookings` | `@PersonUseCase` | `{facilityId, useId, start, slots, partId?}` → 201 + `Location` + `ETag`: reserva con código, `qr`, `cancelUntil`, prezo. 409 `booking.slot-taken` con `alternatives[]` |
| `GET /me/bookings?scope=upcoming\|history&page=` · `GET /me/bookings/{id}` | `@PersonUseCase` | As propias (polo `sub`); outra persoa → 404 |
| `PUT /me/bookings/{id}/slot` | `@PersonUseCase`, `If-Match` | Cambiar: `{useId, start, slots, partId?}` (mesma instalación) |
| `POST /me/bookings/{id}/cancel` | `@PersonUseCase`, `If-Match` | Cancelar dentro do prazo (P1) |
| `GET /me/bookings/{id}/calendar.ics` | `@PersonUseCase` | `text/calendar` |
| `GET /bookings?from=&to=&facilityId=&payment=&status=&q=&sort=recent\|start&page=` | `booking.view` | Lista de xestión (nome, instalación, uso, parte, hora, prezo, pago, estado, `reservedAt`) |
| `GET /bookings/summary` | `booking.view` | Contas de «Cobros»: pendentes de pago, a devolver, canceladas (próximos 30 días) |
| `GET /bookings/{id}` | `booking.view` | Detalle (con correo; código) |
| `GET /bookings/calendar?view=day\|week\|month&date=&facilityId=` | `booking.view` | Bloques por instalación e día, e no mes contas por día |
| `PUT /bookings/{id}/payment` | `booking.payment`, `If-Match` | `{status: PAID\|PENDING\|REFUNDED\|TO_REFUND}` segundo as transicións do §1.2.12 |
| `POST /bookings/{id}/cancel` | `booking.cancel`, `If-Match` | Cancelación polo concello dunha reserva, con `channels[]` (P12) |
| `GET/PUT /sports-contact` · `/payment-notes` | lectura pública, escritura MANAGER | Contacto do servizo e textos de pago e devolución (D2-02) |

Erros novos (mínimo): `booking.not-found`, `booking.facility-not-bookable`, `booking.use-not-available`, `booking.slot-invalid` (fóra de horario, sen aliñar ou cortado por un bloqueo), `booking.slot-taken`, `booking.part-taken`, `booking.seat-taken-by-you`, `booking.too-soon`, `booking.too-far`, `booking.too-long`, `booking.cap-reached`, `booking.cancel-deadline-passed`, `booking.not-cancellable`, `booking.change-other-facility`, `booking.change-price-paid` (P3), `booking.payment-transition-invalid`, `booking.email-not-verified`; en `facility`: `facility.use-price-invalid`, `facility.use-rule-invalid`, `facility.use-weekdays-invalid`.

### 2.3 Normas: como se avalían

| Norma (baleira = non se aplica) | Ao reservar e ao cambiar | Ao cancelar |
|---|---|---|
| Antelación mínima (N horas) | `start ≥ now + N h` | — |
| Antelación máxima (N días) | data do oco ≤ hoxe + N (Europe/Madrid) | — |
| Duración máxima (N franxas seguidas) | `slots ≤ N` | — |
| Límite por persoa (N por día, semana ou mes) | reservas confirmadas da persoa nese uso con inicio no mesmo período natural do oco < N (sen contar a que se cambia) (P4) | — |
| Cancelar sen custo (ata N horas antes) | `cancel_until = start − N h`; sen norma, `cancel_until = start` | `now < cancel_until`; se non, 422 `booking.cancel-deadline-passed` (P1) |

Sen sesión non se mostran ocos que a antelación non permite. Unha franxa xa empezada nunca se reserva.

---

## 3. Tarefas

Tamaño: **S** ≤ ½ día · **M** ≈ 1 día. Ningunha L. Rama: `feat/d2-<id>-<nome>` desde `main` actualizado. Carrís e paralelismo: os do dominio 1 (§3.5 daquel plan): unha soa tarefa con Docker á vez, nunca dous `ng build` nin Playwright á vez.

### 3.1 Docs (carril D)

| ID | Rama | Que fai | Ficheiros | Criterios de aceptación | Tam. | Depende | Rol |
|---|---|---|---|---|---|---|---|
| **D2-01** | `feat/d2-01-docs` | ADR-0025 «Reservas, fase 1» (§1.2: normas e prezo no uso sen `policy`/`pricing`, `@PersonUseCase`, orde de bloqueo ao reservar, aliñación, cambio nunha transacción, estados e pago, QR con ZXing, `.ics` á man, correo despois do commit, cada un coa alternativa descartada) · ADR-0012 (ocupación de produción, unha persoa = unha praza) · ADR-0007 (`@PersonUseCase`) · ADR-0021 e `deseno.md` P1-9 (paleta de estados, excepción do chip de filtro; P2: `furar-filter-chip`, `furar-qr`, `furar-field-popover`, `furar-weekday-picker`) · `arquitectura.md` §3 (`booking` e `notification`; `policy`/`pricing` fóra da fase 1), §9 · `glosario.md` (franxa, oco, código de reserva, a devolver, devolta, cancelar sen custo) · `datos-persoais.md` · especificación, bloques 3, 4 e 5, segundo `especificacion-cambios.md` | `docs/` | Ningún documento contradí o plan; `verificar.sh --parte compliance` (D44 coa ADR nova) | S | — | Arquitecto |

### 3.2 API (carril A)

| ID | Rama | Que fai | Ficheiros | Criterios de aceptación | Tam. | Depende | Rol |
|---|---|---|---|---|---|---|---|
| **D2-02** | `feat/d2-02-condicions-uso` | `facility`: días da semana, prezo e normas no uso (§2.1) e no `PUT …/uses/{id}` (mesmo `If-Match`); `house_rules` na sección `description` ou en `PUT …/house-rules`; `FacilityQueries.use`, `uses(facilityId)`, `visibleIds(page)`, `OpeningInterval.alignFromMinute`; `exclusiveUseOn` e a vixencia do uso miran os días da semana; DTO públicos (§2.2). `municipality`: contacto do servizo e notas de pago e devolución, `MunicipalityQueries.mailContext(id)` (nome, idiomas, cor principal, contacto, notas) | `gal.furar.facility`, `gal.furar.municipality`, migracións, `messages/*`, `openapi.json`, `db/demo/` (prezos e normas da demo, maqueta 06) | Dominio: rangos de cada norma e do prezo; `PAID` sen importe → 422; días baleiros → 422. IT: gardar e ler cada campo, `If-Match`; un uso só os fins de semana non sae en `exclusiveUseOn` un martes; `alignFromMinute` cun bloqueo no medio dun tramo; `visibleIds` exclúe borrador, de baixa e sen reserva por internet; `furar-export` segue en verde (D50: o ficheiro leva os campos novos do uso; v2 sen subir versión se o importador os acepta opcionais); `FacilityTenantIT` e `MunicipalityTenantIT` en verde | M | D2-01 en revisión | Backend |
| **D2-03** | `feat/d2-03-booking-nucleo` | `novo-modulo.sh booking Booking bookings reservas` reducido ao necesario; migracións `booking`, `booking_occupancy`, `use_slot`, `booking_event` (§2.1) con RLS; `OccupancyModelIT` sobre `db/migration` e esquema de proba borrado (D19, mesma fila); entradas en `CrossModuleForeignKeys`; dominio `Booking` (estados, pago, transicións, código), `BookingRules` e `Price` puros; `@PersonUseCase` + aspecto + regra ArchUnit; `BookingPermission` na matriz; `BookingQueries` mínima | `gal.furar.booking`, `shared.security`, `architecture/`, migracións, `definicion-de-feito.md` (D19, mesma fila) | Tests de dominio de cada transición e norma (§2.3); `OccupancyModelIT` con `-Dfurar.occupancy.reps=20` sen intermitentes, máis `samePersonNeverHoldsTwoSeats`; `BookingTenantIT`; `SchemaTenancyIT` e `NativeSqlRowLevelSecurityIT` en verde; `@PersonUseCase` sen sesión → 401, co correo sen verificar → 403 `booking.email-not-verified`; `ModularityTest`; `rapido.sh --tenancy api` | M | D2-01 en revisión | Backend PostgreSQL |
| **D2-04** | `feat/d2-04-ocos` | Dispoñibilidade: `SlotGrid` no dominio (tramos con `alignFromMinute`, `slot_minutes`, vixencia e días do uso, uso que bloquea, ocupación, prazas, antelación) e os dous endpoints públicos (§2.2, §1.2.18) | `gal.furar.booking` | Dominio: abre 9:30 e uso de 60 → 9:30, 10:30…; o último oco que non cabe non sae; bloqueo 12:00–13:00 nun tramo 9:30–14:00 → 13:30; uso que bloquea; prazas con 29/30; partes libres; antelación. IT: 29 mar e 25 out; `availability` dunha páxina de 12 instalacións fai unha consulta de ocupación (contador de sentenzas); instalación en borrador → 404; sen sesión vale | M | D2-02, D2-03 | Backend |
| **D2-05** | `feat/d2-05-reservar` | `POST /me/bookings` coa orde do §1.2.5, «tanto me ten» nas partes, unha praza por persoa, prezo e `cancel_until` conxelados, `alternatives[]` no 409; QR (ZXing `core` 3.5.3) en `qr {size, path}`; `GET /me/bookings` (próximas e historial) e detalle; `.ics` (§1.2.14); evento `BookingConfirmed` | `gal.furar.booking`, `api/pom.xml`, `openapi.json` | IT: 20 fíos ao mesmo oco enteiro → 1 reserva e 19 × 409 con alternativas; partes 2 → 2; prazas 30 e 50 fíos → 30; a mesma persoa dúas prazas → 409; límite por persoa con dúas peticións simultáneas → só unha; cada norma rexeita co seu código; bloqueo creado noutra transacción mentres se reserva → agarda e rexeita (bloqueo da instalación); QR decodificado co lector de ZXing dá o código; `.ics` igual ao esperado; outra persoa → 404; licenza de ZXing en `THIRD-PARTY` | M | D2-04 | Backend |
| **D2-06** | `feat/d2-06-cambiar-cancelar` | `PUT /me/bookings/{id}/slot` (§1.2.8) e `POST /me/bookings/{id}/cancel` (pago → «a devolver»); eventos `BookingChanged` e `BookingCancelled` | `gal.furar.booking`, `openapi.json` | IT: cambiar á franxa seguinte cando a persoa xa está no límite → vale (non se conta a vella); cambiar a un oco ocupado → 409 e a vella intacta (ocupación, `use_slot`, versión); cambiar a outra instalación → 422; cambiar fóra de prazo → 422 (P1); cancelar pagada → `TO_REFUND`; cancelar dúas veces → 409; `If-Match` (412, 428) | M | D2-05 | Backend |
| **D2-07** | `feat/d2-07-xestion-api` | Lista de xestión con filtros, busca por nome ou código, orde e páxinas; `summary`; detalle; `calendar` (día, semana, mes); pago (§1.2.12) e cancelación polo concello con canles; `AuditLog` | `gal.furar.booking`, `openapi.json` | IT de cada filtro e orde; `q` atopa por nome sen acentos e por código; `calendar` dun mes sen N+1; transicións válidas e inválidas (422); STAFF marca pagada e non cancela (403); outro concello → 404; `EndpointSecurityIT` cobre as rutas; auditoría | M | D2-05 | Backend |
| **D2-08** | `feat/d2-08-impacto-demo` | `BookingImpact` real (§1.2.17) e `hasBookings`; datos de demo `db/demo/R__demo_bookings.sql` (Narón: reservas pasadas, de hoxe e futuras, con datas relativas a `current_date`, para `vecino-naron` e persoas ficticias; os estados de pago da maqueta 26) | `gal.furar.booking`, `db/demo/` | IT con `facility` real: bloqueo sobre unha reserva → 409 con ela en `affected[]`, `CANCEL` con `EMAIL` → cancelada polo concello, `TO_REFUND` se estaba pagada, evento con canles; `KEEP` → segue na ocupación e `booking_event` `KEPT`; pechar, dar de baixa, arquivar uso, cambiar tempada e uso que bloquea; eliminar instalación con reservas → 409 `facility.has-bookings`; o modal xa non usa `NoBookingImpact` (só `@ApplicationModuleTest(STANDALONE)`); a demo arrinca con `dev,demo` nunha base baleira | M | D2-06, D2-07 | Backend |
| **D2-09** | `feat/d2-09-correos` | `novo-modulo.sh notification …` sen pantalla; `spring-boot-starter-mail`; escoitas de `BookingConfirmed`, `BookingChanged`, `BookingCancelled` (pola persoa e polo concello con `EMAIL`); plantilla da maqueta 29 en gl e es (asunto «Reserva confirmada: instalación, día, hora»; card con título e día e hora; instalación e enderezo, uso, parte, prezo coa nota de pago do concello; QR co código; prazo exacto para cancelar; «Ver a miña reserva» e «Engadir ao calendario»; pé co contacto e «Privacidade»; sen «darse de baixa»), PNG do QR incrustado e `.ics` adxunto; `notification_delivery`; `SPRING_MAIL_*` (usuario, contrasinal, STARTTLS) en `compose.prod.yaml`, `.env.example` e `instalacion.md` | `gal.furar.notification`, `api/pom.xml`, `compose*.yaml`, `docs/instalacion.md`, `ModularityTest` | Tests do renderizador (escapa `<script>` no nome; gl e es; sen texto sen traducir); IT cun `JavaMailSender` de proba: catro correos co asunto, o adxunto `reserva-FUR-XXXX.ics`, o `cid:` do QR e a parte de texto; un SMTP que falla deixa a reserva feita e `FAILED`, e o reintento envía unha vez; o correo vai no idioma da reserva; `AsyncListenerTenantIT` cobre a escoita | M | D2-06, D2-02 | Backend |

### 3.3 Web (carril W)

| ID | Rama | Que fai | Ficheiros | Criterios de aceptación | Tam. | Depende | Rol |
|---|---|---|---|---|---|---|---|
| **D2-10** | `feat/d2-10-componentes` | `[furarFilterChip]` (botón con `aria-pressed`, ton opcional, excepción do §1.2.20 e axuste de `tag-color.spec.ts`); tons da paleta en `tag.css` e tokens; `furar-qr` (`size`, `path`, nome accesible co código); `furar-field-popover` (campo-resumo que abre un panel en CDK Overlay e, por baixo de 640 px, folla desde abaixo con botón de pechar; pecha con clic fóra e Esc; `next()` para pasar ao seguinte campo); `furar-weekday-picker` (L–D por `Intl`, atallos «Todos», «Luns a venres», «Fins de semana») | `shared/ui/{filter-chip,qr,field-popover,weekday-picker}/`, `styles/components/`, `tag.css`, catálogo, `ui.*` | Specs de teclado e foco (o foco volve ao campo ao pechar; Esc; clic fóra); `tag-color.spec.ts` falla se o fondo pálido aparece nunha etiqueta e pasa no chip de filtro; 4,5:1 nos dous estados e temas; axe no catálogo en gl e es; 320 px | M | D2-01 | Frontend compoñentes |
| **D2-11** | `feat/d2-11-uso-ficha` | Modal do uso como a maqueta 21: «Cando» como fila-resumo (o principal grande, o resto en gris, «Cambiar ⌄») que abre `furar-field-popover` con datas, días e «Bloquea os demais usos»; «Duración e prezo» (segmentado Sen prezo · Gratis · De pago, importe grande «por reserva de 60 min» ou «por persoa»); «Normas» en acordeón con chevron, baleiras («Sen establecer»), duración máxima co total calculado. Sección «Normas da instalación» (`furar-bilingual-field`) no modo edición. Axustes do concello: contacto do servizo de deportes e notas de pago e devolución. Ficha pública (maqueta 06): prezo en cada fila de «Que se pode reservar», «Antes de reservar» en reixa con icona (só as normas con valor e o pago) e o recadro das normas da instalación, botón «Reservar» na tarxeta fixa (sen el se non se reserva por internet) | `features/facility/{uses,description,public}/`, `features/municipality/`, i18n | Specs: cada norma garda e baléirase; o resumo do «Cando» co texto da maqueta; validación igual ca o servidor; sen normas, «Antes de reservar» só co pago; axe e 320 px; un dato non se repite na pantalla | M | D2-02, D2-10 | Frontend features |
| **D2-12** | `feat/d2-12-reservar` | Páxina `/:concello/instalacions/:id/reservar` (maquetas 11 e 13): «←» redondo e «Reservar»; tarxeta coa miniatura, nome e enderezo; campos Que · Data · Hora (con duración) · Parte en `furar-field-popover` que pasan sós ao seguinte; estado na URL; barra inferior con total e «Continuar» («Pedirémosche entrar · Págase no mostrador», nota do concello) ou «Confirmar reserva»; 409 → aviso suave arriba con tres horas e a Hora baleira; «Reserva confirmada» (check, correo ao que se enviou, `furar-value-list`, QR, prazo exacto, «Engadir ao calendario», «Ver as miñas reservas»); modo cambio (`?cambiar=<id>`) co mesmo fluxo e «Confirmar o cambio» | `features/booking/reserve/`, `app.routes.ts`, i18n | Specs do store (URL ↔ estado; 409; volver de Keycloak co mesmo paso); só se ofrecen ocos da API; teclado en todos os campos; foco ao título «Reserva confirmada»; anuncio do 409 en `role="status"`; datas en Europe/Madrid; axe; 320 px; orzamento inicial sen cambios | M | D2-05, D2-06, D2-10 | Frontend features |
| **D2-13** | `feat/d2-13-as-mias-reservas` | `/:concello/reservas` (maquetas 14 e 15): Próximas · Historial con `furar-tabs`; tarxeta con nome en negra, data con icona, uso e parte; prezo e chip de pago na súa liña (á dereita en escritorio); botóns noutra liña (Cambiar `quiet`, Cancelar `danger`, Ver QR `secondary`, con icona; repartidos no móbil); «Reservar de novo» no historial; Cancelar en `furar-confirm` estilo A (texto con icona: «Sen custo…» ou «Xa está pagada…» coa nota de devolución do concello); fóra de prazo, sen Cancelar nin Cambiar e co texto de P1; Ver QR en `furar-dialog`; entradas «As miñas reservas» na barra e no `furar-user-menu` | `features/booking/mine/`, `core/layout/`, i18n | Specs; cancelar actualiza a tarxeta sen recargar; 412 → aviso con recargar; os textos de pago e devolución saen da API (ningún fixo no código); axe; 320 px | M | D2-06, D2-10, D2-12 | Frontend features |
| **D2-14** | `feat/d2-14-listado-portada` | `/:concello/instalacions` (maqueta 16, opción A): buscador da portada (que + día), chips de filtro (deportes da busca, «Cuberta»), recuento, Lista · Mapa (`[furarSegmented]`); reixa con `furar-media-card` co chip «N ocos hoxe» e as tres primeiras horas (ligazón á páxina de reservar con hora); Mapa con `furar-map` e unha marca por instalación (ligazón á ficha). Portada: chip de ocos nas tarxetas e o buscador leva ao listado | `features/facility/public/list/`, `features/municipality/home/`, `shared/ui/map/` (varias marcas, se fai falta), i18n | Specs; filtros na URL; sen ocos, a tarxeta sen chip; o mapa ten alternativa (a lista); teselas con `page.route` nos specs; axe; 320 px | M | D2-04, D2-10 | Frontend features |
| **D2-15** | `feat/d2-15-xestion-reservas` | `/:concello/xestion/reservas` (maqueta 26): busca, «Filtros» (orde, datas con atallos, instalación, pago, estado; chips de filtro e «Quitar filtros») en folla no móbil; «Últimas reservas»; «Cobros» con pestanas (pendentes de pago, a devolver, canceladas e contas); «Próximas»; detalle nun panel lateral (diálogo no móbil): persoa, uso, día, hora, código, correo, total, «Marcar como pagada» / «Marcar como devolta» / desfacer, «Cancelar reserva» (canles, P12); entrada no menú de xestión | `features/booking/manage/`, `app.routes.ts`, i18n | Specs; un dato non se repite; chips de pago coa paleta; transición inválida → aviso; STAFF non ve «Cancelar reserva»; axe; 320 px | M | D2-07, D2-10 | Frontend features |
| **D2-16** | `feat/d2-16-calendario` | `/:concello/xestion/calendario` (maqueta 27): Día (columnas por instalación e horas), Semana (unha instalación) e Mes (contas por día), selector de instalación, anterior/seguinte, «Volver a reservas» e o mesmo panel de detalle de D2-15 (compoñente da feature, non importado doutra) | `features/booking/calendar/`, i18n | Specs; as reservas canceladas non ocupan; navegación por teclado entre bloques (orde de lectura = orde visual); cada bloque cun nome completo («Ana Rey, Pista de pádel 1, 19:00–20:00, Pendente»); axe; 320 px (no móbil, día en lista) | M | D2-07, D2-15 | Frontend features |

### 3.4 Final (carril E)

| ID | Rama | Que fai | Ficheiros | Criterios de aceptación | Tam. | Depende | Rol |
|---|---|---|---|---|---|---|---|
| **D2-17** | `feat/d2-17-e2e` | `web/e2e/reservas.e2e.ts` (veciño: listado → ficha → reservar sen sesión → entrar → volve ao paso → confirmar → correo en Mailpit (`localhost:8025`) con `.ics` e QR → as miñas reservas → cambiar → cancelar; oco collido noutra lapela → tres alternativas) e `xestion-reservas.e2e.ts` (xestor: marcar pagada, cancelar pagada → a devolver → devolta, calendario, bloqueo sobre unha reserva → modal coa reserva real → cancelar avisando por correo → correo de cancelación). VoiceOver e só teclado no fluxo de reservar; entrada en `docs/accesibilidade.md`; D34 coa cifra nova | `web/e2e/`, `docs/accesibilidade.md`, `definicion-de-feito.md` (D34, mesma fila, cifra) | `gl`, `es`, `gl-mobile` en verde con axe en cada paso e dous temas; ningunha petición fóra de localhost; sen `securitypolicyviolation`; `scripts/e2e-web.sh <novo mínimo>`; `verificar.sh --estrito` en verde en `main` | M | todas | QA accesibilidade |

**Total: 17 tarefas** (1 D, 8 A, 7 W, 1 E). Ningunha L.

### 3.5 Orde e paralelismo

- **Desde o primeiro minuto:** D2-01 (D) · D2-02 e D2-03 (A; arrincan cando D2-01 estea en revisión; só un con Docker á vez) · D2-10 (W).
- **Camiño crítico da API:** D2-03 → D2-04 → D2-05 → D2-06 → D2-08. D2-07 e D2-09 van en paralelo despois de D2-05/D2-06 (un con Docker).
- **Web:** D2-11 cando estean D2-02 e D2-10; D2-12 cando estean D2-05 e D2-06; despois D2-13. D2-14 despois de D2-04; D2-15 → D2-16 despois de D2-07.
- **Ficheiros quentes:** os do dominio 1 máis `CrossModuleForeignKeys`, `ModularityTest` e `tag.css`. Claves i18n só baixo o propio prefixo (`booking.reserve.*`, `booking.mine.*`, `booking.manage.*`, `booking.calendar.*`, `facility.uses.terms.*`, `ui.<compoñente>.*`).

### 3.6 Calendario

| Día | Carril A | Carril W | D/E |
|---|---|---|---|
| Mar 27 | D2-03 · D2-02 | D2-10 | D2-01 |
| Mér 28 | D2-04 | D2-11 | |
| Xov 29 | D2-05 | D2-11 (remate) | |
| Ven 30 | D2-06 · D2-07 | D2-14 | |
| Sáb 31 | D2-09 · D2-08 | D2-12 | |
| Lun 2 | D2-08 (remate) | D2-13 · D2-15 | |
| Mar 3 | — | D2-16 | |
| Mér 4 – Xov 5 | correccións | correccións | D2-17 · `verificar.sh --estrito` |

**Poden escorregar sen romper a demo:** a vista Mapa de D2-14, Semana e Mes de D2-16, «Reservar de novo» de D2-13. Non escorregan: D2-03 a D2-06, D2-08, D2-09 e D2-12 (son o que se valora: consulta, reserva, modificación, cancelación, historial e avisos).

---

## 4. Perfís e regras comúns

Os mesmos perfís e regras comúns do [plan do dominio 1](dominio-1-instalacions.md) §4 («Le antes…», sen commit nin push, SPDX, dependencias permitidas, para e pregunta se falta produto), cambiando «D1» por «D2» e este ficheiro polo daquel. Engádese:

- **Arquitecto (D2-01):** le ADR-0005, 0006, 0007, 0010, 0012, 0017, 0018, 0020, 0021, 0022, `deseno.md` §8 e P1-9, e `especificacion-cambios.md`. Aplica os cambios da especificación tal como están (non redactes produto novo). Cada decisión do §1.2 vai na ADR-0025 coa alternativa descartada.
- **Backend PostgreSQL (D2-03):** le ADR-0012, ADR-0022, `OccupancyModelIT` e o seu SQL. As migracións son de produción: a fila D19 cambia o comando e o «Límite», non o número de filas. Para se a exclusión «unha praza por persoa» non se pode expresar con `btree_gist` sobre `text`.
- **Backend (D2-02, D2-04 a D2-09):** le ADR-0005, 0006, 0007, 0008, 0010, 0013, 0020, 0022, a ADR-0025 e `facility` como referencia. Todo caso de uso que escribe reservas toma `FacilityQueries.lockFacility` antes de comprobar nada (§1.2.5) e nunca le táboas de `facility` (só `FacilityQueries` e as FK da lista pechada). Horas: `Instant` e `tstzrange`; Europe/Madrid só para pintar e para os períodos das normas. Nada de SMTP dentro dunha transacción de reserva. **D2-05** proba a concorrencia con `CyclicBarrier` como `furar_app` antes que o resto. **D2-09** non engade motor de plantillas nin dependencias de proba (o `JavaMailSender` de proba é unha clase propia). Para se: un estado, norma ou texto non está no §2 nin na especificación; necesitas un dato da persoa que non trae o token.
- **Frontend compoñentes (D2-10):** como D1 §4.6. O chip de filtro é a única excepción a P1-9; non a estendas a outra peza.
- **Frontend features (D2-11 a D2-16):** como D1 §4.7, coas maquetas `06-ficha-publica`, `11-reservar-una`, `13-reservar-confirmada`, `14-mis-reservas`, `15-cancelar-opciones` (estilo A), `16-listado` (opción A), `21-uso-definitivo`, `26-reservas-gestor` e `27-calendario`. Regras de produto que manda revisar en cada pantalla: un dato unha vez por pantalla salvo motivo; sen textos obvios; chips de estado `solid` coa paleta; toda capa pecha con clic fóra e Esc; toda peza reutilizable en `shared/ui` (se falta, para e pídea). Os textos de pago e devolución nunca van no código: saen da API. Datas e horas en Europe/Madrid con `Intl`.
- **QA accesibilidade (D2-17):** como D1 §4.8. O correo compróbase pola API de Mailpit en `localhost:8025`; bórrase o que se lea.

---

## 5. Riscos e orde de merge

| Risco | Mitigación |
|---|---|
| Dobres reservas ou límite superado con peticións simultáneas | Bloqueo da instalación primeiro (§1.2.5) e exclusións na base; IT con fíos en D2-03 e D2-05 |
| Cambio que deixa a reserva a medias | Unha transacción e IT de fallo con estado intacto (D2-06) |
| Ocos mal aliñados ou cambios de hora | `alignFromMinute`, `Instant` en todo o servidor, IT do 29 mar e 25 out |
| SMTP lento ou caído | Envío despois do commit, reintento do rexistro de publicacións, `notification_delivery` |
| Cl@ve ou Chave365 sen correo no token | Reservar pide `email_verified`; Keycloak pide e verifica o correo no primeiro acceso; comprobalo con «Cl@ve simulada» en D2-17 (se non, para e avisa) |
| Orzamento da web | Cada páxina con `loadComponent`; sen dependencias novas na web; QR pintado desde a API |
| Conflitos en `openapi.json`, i18n e `migrations.lock` | Merges en serie, rexenerar sempre, regra de rebase das migracións |
| Textos de pago inventados | Saen das notas do concello (P8); Furar só trae o valor por defecto |

**Orde de merge:** 1. D2-01 → 2. D2-03, D2-10 → 3. D2-02 → 4. D2-04 → 5. D2-05, D2-11 → 6. D2-06, D2-14 → 7. D2-07, D2-12 → 8. D2-09, D2-13 → 9. D2-08, D2-15 → 10. D2-16 → 11. D2-17. Un merge cada vez, despois de `rapido.sh` e as probas da fila; `verificar.sh --estrito` cada noite en `main`.

---

## 6. Preguntas de produto

Só o que non quedou decidido entre o 2026-10-24 e o 2026-10-26. **Todas están pendentes de validar** (2026-10-26): cada unha leva a recomendación, que é o valor por defecto que se constrúe mentres o mantedor non responda. A especificación recólleas en «Pendente de decidir».

**Cancelar e cambiar**
1. **Fóra do prazo para cancelar,** ¿pódese cancelar ou cambiar por internet? Sen penalizacións na fase 1, «fóra de prazo» non tería efecto nunha reserva sen pagar. **Validado (2026-10-30).** *Constrúese: non; desaparecen Cancelar e Cambiar e sae «Xa pasou o prazo para cancelar. Chama ao [teléfono do servizo]». Sen norma de cancelación, pódese ata o inicio.*
2. **Cambiar a outra instalación** («outra pista»): ¿vale ou só dentro da mesma? **Validado (2026-10-30).** *Constrúese: só na mesma instalación (outro uso, parte, día ou hora); para outra, cancelar e reservar. Un só bloqueo e unha soa páxina de reservar.*
3. **Cambiar unha reserva xa pagada** a un uso ou duración con outro prezo. **Validado (2026-10-30).** *Constrúese: déixase só se o prezo novo é igual; se non, «Para cambiar a un prezo distinto, cancela e reserva de novo» (e a cancelación queda «a devolver»).*

**Normas e prezo**
4. **Límite por persoa:** ¿conta por uso ou por instalación, e por períodos naturais (día, semana de luns a domingo, mes) ou móbiles (últimos 7 días)? **Validado (2026-10-30).** *Constrúese: por uso (a norma está no uso) e naturais en hora de Galicia, contando as confirmadas (pasadas e futuras) con inicio nese período.*
5. **«Sen prezo» fronte a «Gratis»:** ¿que ve a persoa? **Validado (2026-10-30).** *Constrúese: «Sen prezo» non mostra prezo nin leva cobro (o concello aínda non o puxo); «Gratis» mostra o chip azul «Gratis». Nos dous, sen «Pendente».*
6. **Varias partes á vez da mesma persoa** (Metade norte e Metade sur á mesma hora en dúas reservas): ¿vale? A regra «unha persoa = unha praza» só fala de prazas. **Validado (2026-10-30).** *Constrúese: si; para o campo enteiro xa está o uso «enteira».*

**Datos da persoa e do concello**
7. **Teléfono de quen reserva:** o rexistro non o pide e a maqueta do xestor amosa un. **Validado (2026-10-30).** *Constrúese: non se pide na fase 1; o xestor ve o correo e «Chamar» só sae se hai teléfono (nunca, polo de agora). Pedilo, opcional, chega con `people`.* *Revisión do 2026-11-04: sen teléfono, «Chamar» sae do modal e das cancelacións.*
8. **Textos de pago e devolución e contacto do servizo de deportes:** ¿onde os pon o concello? **Validado (2026-10-30).** *Constrúese: nos Axustes do concello, dous textos gl/es («Como se paga», «Como se devolve») con valores por defecto de Furar («No mostrador, ao chegar», «O concello dirache como cha devolve») e o contacto (nome do servizo, enderezo, teléfono, correo) para o pé do correo e a ficha. Sen logo propio aínda: escudo xenérico.* **Revisión do 2026-11-03 (maqueta 49):** en Concello › Servizo, «Igual para todas as instalacións» (os dous textos aquí) ou «Distinto por instalación» (`municipality.payment_notes_scope = 'FACILITY'`): cada ficha leva a súa sección «Pago e devolución» (`facility.payment_note`, `refund_note`; `PUT …/facilities/{id}/payment-notes`, `GET …/public/facilities/{id}/payment-notes`) e **non herda** os do concello: sen texto, o de Furar. Os textos do concello quedan gardados ao cambiar de modo. A ficha pública, reservar, «As miñas reservas» e os correos len a fonte que toca (`effectivePaymentTexts` na web; `MunicipalityMailContext.paymentNotesByFacility` e `FacilityQueries.paymentTexts` no correo).
9. **Normas da instalación en texto libre** (maqueta 06, «Normas da instalación»): ¿entran na fase 1? **Validado de momento (2026-10-30); revísase máis adiante.** *Constrúese: si, un texto gl/es na ficha da instalación; é barato e xa está na maqueta aprobada.*

**Estados e pantallas**
10. **«Realizada» e «Devolta»** levan as dúas o gris na maqueta e a paleta di «un ton por estado dentro de cada grupo». **Validado de momento (2026-10-30); revísase máis adiante.** *Constrúese: «Devolta» en gris (`muted` sólido) e «Realizada» como etiqueta neutra (contorno), así non comparten ton no Historial.*
11. **Reserva cancelada sen pagar no Historial:** ¿leva chip? **Validado de momento (2026-10-30); revísase máis adiante.** *Constrúese: «Cancelada» en vermello, e a liña do uso di quen a cancelou («cancelada polo concello»), como na maqueta 14.*
12. **«Cancelar reserva» desde o detalle do xestor:** ¿pide canles como o modal de reservas afectadas? **Validado de momento (2026-10-30); revísase máis adiante.** *Constrúese: si, o mesmo bloque dunha soa reserva (Correo marcado; Aviso na web; Chamar se hai teléfono), sen motivo escrito na fase 1.* *Revisión do 2026-11-04: só Correo e Aviso na web.*
13. **Desfacer un cobro mal marcado** («Pagada» → «Pendente», «Devolta» → «A devolver»): ¿déixase? **Validado de momento (2026-10-30); revísase máis adiante.** *Constrúese: si, desde o detalle, co rexistro de actividade.*

---

## 7. Filas da DoD que se tocan

A DoD está conxelada (ADR-0016): ningunha fila nova. Cambian:

| Fila | Que cambia | Tarefa |
|---|---|---|
| D19 | O comando e o «Límite»: `OccupancyModelIT` corre sobre `db/migration` e engade `samePersonNeverHoldsTwoSeats`; bórrase `db/occupancy-model/` | D2-03 |
| D34 | O mínimo de tests e2e sobe ao número de `playwright test --list` cos dous ficheiros novos | D2-17 |
| D50 | Mesma fila e mesmos 6 tests: o ficheiro de exportación leva os campos novos do uso e da instalación (`booking` non entra en `furar-export` na fase 1) | D2-02 |
| D14, D17, D37, D44, M3, regras A | Sen cambiar o texto: deben seguir en verde coas táboas, o contrato, a dependencia (`THIRD-PARTY` con ZXing e Jakarta Mail), a ADR-0025, `ModularityTest` e a regra de `@PersonUseCase` | D2-01, D2-03, D2-05, D2-09 |
