<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0039 · Tempada das entidades: as sesións son reservas da entidade, nunca pisan nada e a entidade ten acceso propio

- **Estado:** aceptada · 2026-11-12 (produto decidido polo mantedor; alcance da primeira versión recortado o 2026-11-12, punto 1; API e web construídas o 2026-11-12)
- **Fonte de produto:** [especificación](../produto/especificacion.md), «Entidades» (bloque 5) e bloque 6 («Series»), revisados polo mantedor o 2026-11-11.
- **Constrúese enriba de:** [ADR-0012](0012-ocupacion-exclusiva-e-prazas.md) e [ADR-0022](0022-instalacions-horario-e-orde-de-bloqueo.md) (ocupación, `FacilityLock`, `BookingImpact`), [ADR-0025](0025-reservas-fase-1.md) (normas e prezo no uso, persoa sen rol, clave de reintento), [ADR-0031](0031-persoas-a-cargo.md) («a reserva é do correo», `EmailKey`, `close_account`), [ADR-0032](0032-non-presentados-e-validacion.md), [ADR-0033](0033-lista-de-espera-e-ocos-parecidos.md), [ADR-0035](0035-recordatorio-por-correo.md), [ADR-0026](0026-indicadores-fase-1.md), [ADR-0034](0034-datos-abertos.md) e [ADR-0040](0040-buscar-xente-para-xogar.md).
- **Migracións:** `V202611121100__booking_organization.sql`, `V202611121110__booking_series.sql`, `V202611121120__notification_series.sql` (parte de `V202611121020__notification_open_match.sql`) e `V202611121130__booking_organization_close_account.sql` (parte de `V202611121030__booking_open_match_close_account.sql`). A seguinte que redefina as restricións de `notification_delivery` ou `close_account` parte destas.
- **Revisa:** a especificación, bloque 6 (desaparecen os «avisos pendentes», a bandexa «Pendentes», «cada N semanas», o prezo por sesión e os correos asignados), e [`arquitectura.md`](../arquitectura.md) §3 (as entidades non van en `people`, punto 2).

## Contexto
Os clubs, escolas e asociacións usan as mesmas pistas cada semana durante a tempada. Hoxe o concello tería que facer cada sesión á man no mostrador, sen ver de vez os días que xa están collidos, e a entidade non ten onde ver as súas horas nin soltar unha que non vai usar. Hai que repartir horarios fixos sen pisar nunca unha reserva da veciñanza, sen un segundo modelo de ocupación e sen abrir unha porta nova aos datos da veciñanza.

## Decidido polo mantedor (2026-11-11)
- **Entidades** como pestana de «Reservas» («Reservas · Incidencias · Validar QR · Entidades», co número de solicitudes pendentes): nome, persoa de contacto, correo, teléfono e responsables (correos). Táboa con Entidade, Contacto, Teléfono, Responsables e Estado («En tempada», «Solicitude», «Sen tempada»).
- **Responsables:** persoas que, coa súa conta de sempre, ven as sesións da entidade, cancélanas e reservan no seu nome.
- **Nova reserva › Tempada** (segmentado «Unha vez · Tempada»): entidade, instalación, que (uso), parte, días da semana, horas (de 15 en 15 minutos, dentro das franxas do uso) e datas. Os choques saen na mesma pantalla: «N días xa están ocupados. Eses días non se crean.», con que hai cada día e, se é unha reserva ou unha sesión, «Cancelar a reserva» ou «Cancelar a sesión» (o diálogo de sempre, cun interruptor por canle). Os festivos non se crean e dino nunha liña. Botón «Crear N sesións». **Nunca pisa unha reserva dunha veciña ou dun veciño.**
- **Sección «Tempadas»** na páxina «Reservas»; no calendario, a sesión mostra a entidade e o detalle da serie: días, número de sesións e datas, «Pago da tempada» (chip-menú: Gratis, Pendente de pago, Pagada), «Cancelar esta sesión» e «Rematar a tempada…» (desde unha data; as horas quedan libres).
- **Sen cobro sesión a sesión;** o pago é da tempada e opcional.
- **A entidade:** en «As miñas reservas», pestanas de liña «Eu · ‹Entidade›»; cancela sesións, reserva máis horas «como club» e **solicita tempada para calquera instalación que se poida reservar** (sen interruptor «admite tempadas»: decide o xestor ao aprobar).
- **Reservas extra da entidade:** as mesmas condicións e o mesmo prezo ca unha veciña ou un veciño; o límite do uso conta para a entidade coma se fose unha persoa; o pago lévao o persoal coma sempre.
- **Solicitudes:** tarxetas pulsables (entidade; instalación con «· parte · uso» en texto; «venres · 18:00–19:30 · do 20 nov. ao 18 xuñ.»; chip «Pendente» e «solicitada hai 2 h» abaixo á dereita). O xestor revisa coa mesma vista de choques e pode axustar os datos; «Aprobar e crear N sesións» ou «Rexeitar» cunha mensaxe que lle chega á entidade por correo.

## Decisión
1. **Alcance da primeira versión** (decidido o 2026-11-12): as sesións **non se moven** (nin a entidade nin o persoal: só se cancelan); sen `.ics` da tempada; **o correo da entidade é obrigatorio**; sen usos por prazas; as sesións non saen en «Últimas reservas». As reservas extra da entidade cancélaas a entidade e cámbiaas, se fai falta, o persoal. O resto do decidido polo mantedor vai enteiro.
2. **Todo dentro de `booking`.** O dominio vai no subpaquete `domain.season` (`Organization`, `SeriesSchedule`, `SeriesPlanner`, `SeriesPlan`, `SeriesConflict`, `Series`, `SeriesRequest`), coma o horario dentro de `facility` ([ADR-0022](0022-instalacions-horario-e-orde-de-bloqueo.md) punto 1). Os servizos, os adaptadores e os controladores van nos paquetes de sempre do módulo, co prefixo `Organization`/`Series`: comparten pezas de visibilidade de paquete (`BookingViews`, `WaitNotifier`, o mapeador `BookingSql.booking`, `BookingWebMapper`) e un subpaquete obrigaría a facelas públicas. Sen módulo novo e sen dependencias novas entre módulos:
   - **As sesións son reservas** (punto 5): créanse baixo o bloqueo da instalación escribindo `booking_occupancy`, que é de `booking`.
   - **«Reservar como entidade»** está dentro de `ReserveService`, que ten que saber en cada petición se a persoa é responsable: cun módulo aparte, `booking` dependería del e el de `booking` (un ciclo, ou unha SPI e unha FK máis en `CrossModuleForeignKeys`).
   - **Non en `people`:** `people` garda o que é da conta e non dun concello; unha entidade é dun concello.
   - SQL nativo con `JdbcClient` e `@NativeSql(scope = MUNICIPALITY)` coma o resto de `booking` (A8b): `OrganizationSql`, `SeriesSql` e `SeriesRequestSql`, cos seus `OrganizationSqlTenantIT`, `SeriesSqlTenantIT` e `SeriesRequestSqlTenantIT`. Fóra de `furar-export` (punto 16).
3. **Nomes** (glosario): entidade = `organization`; responsable = `organization manager`; tempada dunha entidade = `booking series` (na web e nos textos, «tempada»); sesión = `session` (unha reserva con `series_id`); solicitude = `series request`. «Tempada» do horario (`season` de `facility`) é outra cousa: no código de `booking` nunca se chama `season` a unha serie, só o subpaquete. O permiso chámase `booking.organizations` (non `seasons`, para non confundilo con `facility.season-*`).
4. **Modelo.** Táboas do concello, con RLS e a política `<táboa>_tenant` na mesma migración (o modelo de `V202610231100__row_level_security.sql`):

   ```sql
   create table booking_organization (
     id uuid primary key,
     municipality_id uuid not null references municipality (id),
     name text not null check (length(name) between 1 and 120),
     contact_name text check (length(contact_name) between 1 and 120),
     email text check (length(email) between 3 and 254 and email like '%_@_%'),
     phone text check (length(phone) between 3 and 30 and phone ~ '^[0-9+() .-]+$'),
     archived_at timestamptz,                 -- punto 7: con historial non se borra, arquívase
     created_at timestamptz not null, updated_at timestamptz not null, version bigint not null,
     constraint booking_organization_tenant_uk unique (municipality_id, id),
     -- O correo é obrigatorio mentres está dada de alta; arquivada só queda o nome.
     constraint booking_organization_email_ck check (archived_at is not null or email is not null),
     constraint booking_organization_archived_ck check (archived_at is null
       or (email is null and contact_name is null and phone is null))
   );
   create unique index booking_organization_name_uk
     on booking_organization (municipality_id, lower(name)) where archived_at is null;

   create table booking_organization_manager (
     municipality_id uuid not null references municipality (id),
     organization_id uuid not null,
     email_key text not null check (length(email_key) between 3 and 254),   -- EmailKey.of
     email text not null check (length(email) between 3 and 254),
     added_at timestamptz not null,
     added_by text not null check (length(added_by) between 1 and 255),
     primary key (municipality_id, organization_id, email_key),
     foreign key (municipality_id, organization_id)
       references booking_organization (municipality_id, id) on delete cascade
   );
   create index booking_organization_manager_email_idx
     on booking_organization_manager (municipality_id, email_key);

   create table booking_series (
     id uuid primary key,
     municipality_id uuid not null references municipality (id),
     organization_id uuid not null,
     facility_id uuid not null, use_id uuid not null, part_id uuid,
     rental_mode text not null check (rental_mode in ('WHOLE', 'PARTS')),
     weekdays smallint[] not null
       check (cardinality(weekdays) between 1 and 7 and weekdays <@ '{1,2,3,4,5,6,7}'),  -- ISO, luns = 1
     start_minute int not null check (start_minute between 0 and 1425 and start_minute % 15 = 0),
     end_minute int not null check (end_minute between 15 and 1440 and end_minute % 15 = 0),
     first_date date not null, last_date date not null,
     ended_from date,
     payment_status text not null default 'FREE' check (payment_status in ('FREE', 'PENDING', 'PAID')),
     request_id uuid,
     idempotency_key text not null check (idempotency_key ~ '^[!#-\[\]-~]{1,255}$'),   -- coma booking_idempotency_key
     idempotency_fingerprint text not null check (idempotency_fingerprint ~ '^[0-9a-f]{64}$'),  -- SHA-256 da entrada
     created_by text not null check (length(created_by) between 1 and 255),
     created_at timestamptz not null, updated_at timestamptz not null, version bigint not null,
     constraint booking_series_tenant_uk unique (municipality_id, id),
     constraint booking_series_org_uk unique (municipality_id, id, organization_id),
     constraint booking_series_idempotency_uk unique (municipality_id, idempotency_key),
     foreign key (municipality_id, organization_id) references booking_organization (municipality_id, id),
     foreign key (municipality_id, facility_id, use_id) references facility_use (municipality_id, facility_id, id),
     foreign key (municipality_id, use_id, part_id) references facility_use_part (municipality_id, use_id, id),
     foreign key (municipality_id, use_id, rental_mode) references facility_use (municipality_id, id, rental_mode),
     check ((rental_mode = 'PARTS') = (part_id is not null)),
     check (end_minute > start_minute),
     check (last_date >= first_date and last_date <= first_date + 365),
     check (ended_from is null or ended_from between first_date and last_date + 1)
   );
   create index booking_series_org_idx on booking_series (municipality_id, organization_id);
   create index booking_series_facility_idx on booking_series (municipality_id, facility_id);

   create table booking_series_request (
     id uuid primary key,
     municipality_id uuid not null references municipality (id),
     organization_id uuid not null,
     facility_id uuid not null, use_id uuid not null, part_id uuid,
     rental_mode text not null check (rental_mode in ('WHOLE', 'PARTS')),
     weekdays smallint[] not null
       check (cardinality(weekdays) between 1 and 7 and weekdays <@ '{1,2,3,4,5,6,7}'),
     start_minute int not null check (start_minute between 0 and 1425 and start_minute % 15 = 0),
     end_minute int not null check (end_minute between 15 and 1440 and end_minute % 15 = 0),
     first_date date not null, last_date date not null,
     note text check (length(note) between 1 and 500),
     requested_by_subject text, requested_by_email text,   -- nulos tras close_account
     status text not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED')),
     rejection_message text check (length(rejection_message) between 1 and 1000),
     series_id uuid,
     decided_at timestamptz, decided_by text,
     created_at timestamptz not null, updated_at timestamptz not null, version bigint not null,
     constraint booking_series_request_tenant_uk unique (municipality_id, id),
     foreign key (municipality_id, organization_id)
       references booking_organization (municipality_id, id) on delete cascade,
     foreign key (municipality_id, facility_id, use_id) references facility_use (municipality_id, facility_id, id),
     foreign key (municipality_id, use_id, part_id) references facility_use_part (municipality_id, use_id, id),
     -- Só a columna da serie a nulo: municipality_id é not null.
     foreign key (municipality_id, series_id)
       references booking_series (municipality_id, id) on delete set null (series_id),
     check ((rental_mode = 'PARTS') = (part_id is not null)),
     check (end_minute > start_minute),
     check (last_date >= first_date and last_date <= first_date + 365),
     check ((status = 'PENDING') = (decided_at is null)),
     check ((status = 'REJECTED') = (rejection_message is not null)),
     check (series_id is null or status = 'APPROVED')
   );
   create index booking_series_request_pending_idx
     on booking_series_request (municipality_id, created_at) where status = 'PENDING';
   create index booking_series_request_facility_idx on booking_series_request (municipality_id, facility_id);

   alter table booking
     add column organization_id uuid,
     add column series_id uuid,
     add foreign key (municipality_id, organization_id) references booking_organization (municipality_id, id),
     add foreign key (municipality_id, series_id, organization_id)
       references booking_series (municipality_id, id, organization_id),
     add constraint booking_series_org_ck check (series_id is null or organization_id is not null),
     add constraint booking_organization_holder_ck check (organization_id is null
       or (holder_subject is null and holder_email is null and holder_phone is null and booked_by is not null)),
     add constraint booking_series_price_ck check (series_id is null or price_kind = 'NONE');
   create index booking_series_session_idx on booking (municipality_id, series_id, lower(period))
     where series_id is not null;
   create index booking_organization_idx on booking (municipality_id, organization_id, lower(period))
     where organization_id is not null;
   ```
   - Compatible co que hai: `booking_anonymized_ck` (`V202611042230`) admite unha reserva sen conta nin correo se ten `booked_by`; `holder_email_key` é nulo xunto co correo (`V202611101710`); `holder_subject`, `holder_name` e `holder_email` xa admiten nulo (`V202611041300`).
   - `CrossModuleForeignKeys` gaña as FK de `booking_series` e `booking_series_request` cara a `facility_use` e `facility_use_part`. `SchemaTenancyIT` e `NativeSqlRowLevelSecurityIT` cobren as catro táboas.
   - **Estado da entidade** (na táboa), calculado ao ler: con algunha solicitude pendente, `REQUEST`; se non, con algunha sesión confirmada futura, `SEASON`; se non, `NONE`.
5. **As sesións son reservas normais da entidade, cun vínculo á serie** (`booking.series_id`), non unha táboa propia que ocupa.
   - **Por que:** a ocupación (`booking_occupancy`) leva `booking_id not null` e as exclusións da base; a dispoñibilidade, as esperas e «Quedou libre» ([ADR-0033](0033-lista-de-espera-e-ocos-parecidos.md)), o modal de reservas afectadas, o calendario e a busca da xestión, a validación do QR, os indicadores e a ocupación dos datos abertos xa len `booking`. Cunha táboa propia habería que ensinarlles a todos un segundo ocupante, e a base deixaría de garantir que unha sesión e unha reserva non se pisan.
   - **O dominio sábeo.** `Booking` gaña dous campos `@Nullable`, `organizationId` e `seriesId`, coa regra «`seriesId` implica `organizationId`» no construtor e o titular baleiro se hai entidade. Lense e escríbense en todos os sitios que copian as columnas da reserva: o mapeador `BookingSql.booking` e os seus `select` (`BookingSql`, `BookingSearchSql`, `BookingAttendanceSql`) e `BookingSql.insertBooking`.
   - **Que leva unha reserva da entidade** (sesión ou reserva extra): `organization_id`; `holder_subject`, `holder_email`, `holder_email_key` e `holder_phone` nulos; `holder_name` = o nome da entidade (copia); `holder_language` = o primeiro idioma do concello; `booked_by` = quen a fixo (o xestor nunha sesión, a persoa responsable nunha extra). Sen correo de titular, ningún camiño da persoa a toca: `PersonArrival` non a liga, non conta no límite nin nas faltas de ninguén, non leva recordatorio e non sae en «As miñas reservas › Eu».
   - **Que leva unha sesión ademais:** `series_id`; `price_kind = 'NONE'`, `payment_status = 'NONE'`; `cancel_until` = o inicio; normas `rule_*` nulas; `slots` = `ceil(minutos / duración da franxa do uso)` (só informativo); un código e un QR propios; `seats = 1`.
   - **A grella:** unha sesión non ten que caer na grella do uso; a ocupación só mira intervalos e a grella da veciñanza calcula os ocos libres por solapamento.
   - **Non se edita o titular:** `Booking.withHolder` lanza `booking.holder-not-editable` se hai `organizationId` (hoxe só o impide se `bookedBy` é nulo, e `BookForService.change` podería ligala a unha conta e violar `booking_organization_holder_ck`).
   - **Unha sesión non se move:** `BookForService.change` cunha sesión (`seriesId != null`) responde 422 `booking.session-not-editable` antes de nada (o camiño de sempre calcula o fin por franxas, esixe a grella, aplica as normas e o prezo do uso). Unha reserva extra da entidade si se cambia polo camiño de sempre do persoal (ten prezo e normas).
   - **Valoracións:** unha reserva da entidade non se valora (`ReviewRule.reviewable` falso).
   - **Partidas abertas** ([ADR-0040](0040-buscar-xente-para-xogar.md)): unha reserva da entidade non se abre (422 `booking.match-not-allowed`).
6. **Só usos enteiros ou por partes.** As tempadas, as solicitudes e as reservas extra dunha entidade non se fan en usos por prazas (422 `booking.organization-seats-use`); a web non os ofrece.
7. **Entidades** (permiso `booking.organizations`, punto 13).
   - `GET …/organizations?status=&archived=false` → `OrganizationRowResponse[] {id, name, contactName?, email?, phone?, managers: int, status: REQUEST|SEASON|NONE, archived}`; `GET …/organizations/{id}` → `OrganizationResponse {id, name, contactName?, email?, phone?, managers: string[], archived, version}` (`ETag`); `POST …/organizations` e `PUT …/organizations/{id}` (`If-Match`) con `OrganizationRequest {name, contactName?, email, phone?, managers: string[]}` → `OrganizationResponse`; `DELETE …/organizations/{id}` (`If-Match`) → 204.
   - Como moito **10 responsables** (422 `booking.organization-managers-limit`); correos validados e normalizados con `EmailKey.of`, repetidos unha vez. O `PUT` substitúe a lista na mesma transacción, baixo o bloqueo da fila da entidade. Cambiar o nome actualiza `holder_name` das súas reservas sen anonimizar.
   - **Eliminar:** `select … for update` da entidade **primeiro** e despois a comprobación. **Sen ningunha reserva nin serie na súa historia**, bórrase (e con ela, en cascada, os responsables e as solicitudes). **Con historial non se borra: arquívase** (`archived_at`; bórranse os responsables, as solicitudes pendentes e o contacto; queda o nome), así as reservas pasadas seguen ligadas e os indicadores pasados non cambian (unha entidade segue contando como unha persoa). Con sesións ou reservas confirmadas futuras, 409 `booking.organization-has-future-bookings`. Unha arquivada non sae nas listas por defecto, non admite tempadas, solicitudes nin reservas e os seus responsables xa non a ven.
8. **Crear unha tempada: vista previa e creación.** `SeriesPlanner` (dominio, puro e sen reloxo) calcula as sesións; `SeriesService` le o que fai falta e escribe.
   - **Entrada** `SeriesRequest {organizationId, facilityId, useId, partId?, weekdays[], startMinute, endMinute, firstDate, lastDate}`. Validación (422): días baleiros `booking.series-weekdays-required`; minutos fóra de 15 ou fin ≤ inicio `booking.series-time-invalid`; `firstDate` antes de hoxe (Europe/Madrid), `lastDate` antes de `firstDate` ou máis de 365 días `booking.series-range-invalid`; instalación de baixa ou sen publicar `booking.facility-not-bookable`; uso arquivado `booking.use-not-available`; uso por prazas `booking.organization-seats-use`; entidade arquivada ou inexistente 404 `booking.organization-not-found`. Non se miran as normas do uso. Cada semana (sen «cada N semanas»).
   - **Cada data:** os días de `firstDate` a `lastDate` cun día da semana escollido; inicio e fin en Europe/Madrid (`ZonedDateTime.ofLocal`); `endMinute = 1440` é a medianoite do día seguinte. Non contan as que xa empezaron.
   - **Lectura nova en `facility`:** `FacilityQueries.calendar(facilityId, from, to)` → `List<FacilityDay(date, holiday, open, blocks, exclusiveUseId)>`: se é festivo (aínda que ese festivo teña horario propio), os tramos abertos **sen cortar polos bloqueos**, os bloqueos que o pisan (`BlockSpan(start, end, reason)`) e o uso que bloquea os demais. Cun número fixo de lecturas para todo o rango (ata `MAX_OPENING_DAYS`).
   - **Cada data cae nunha destas clases, nesta orde:**

     | Clase | Cando | Onde sae |
     |---|---|---|
     | `HOLIDAY` | É festivo, teña ou non horario propio (o conservador: non se crea) | Liña de debaixo: «Os festivos (6 xan. e 17 maio) tampouco se crean.» |
     | `USE_OFF` | O uso non está vixente ese día (datas ou días do uso) | Liña: «O uso non está dispoñible ‹datas›: tampouco se crean.» |
     | `CLOSED` | Sen ningún tramo aberto ese día, ou a sesión cae enteira na hora que non existe o día do cambio de hora (intervalo baleiro) | Liña: «Os días pechados (…) tampouco se crean.» |
     | `OTHER_USE_EXCLUSIVE` | Outro uso bloquea os demais ese día | Lista de choques: «Uso exclusivo: ‹uso›» |
     | `OUT_OF_HOURS` | Abre, pero a hora non cabe enteira nun tramo | Lista: «Ese día abre de ‹tramos›» |
     | `BLOCK` | Un bloqueo píspaa | Lista: «Bloqueo: ‹motivo›» (sen botón) |
     | `BOOKING` / `SESSION` | Unha ocupación activa da instalación píspaa (o mesmo uso e parte ou toda a pista, ou outro uso) | Lista: «Reserva de ‹nome›» ou «Sesión de ‹entidade›», coa súa hora, e «Cancelar a reserva» / «Cancelar a sesión» |
     | `FREE` | Nada do anterior | Créase |
   - **Vista previa** `POST …/booking-series/preview` con `SeriesScheduleRequest` (a entrada) → `SeriesPreviewResponse {sessions, firstSession?, lastSession?, holidays: date[], useOff: date[], closed: date[], conflicts: [{date, start, end, kind, bookingId?, name?, label?}]}`. Non escribe nin bloquea.
   - **Crear** `POST …/booking-series` con `CreateSeriesRequest` (a entrada máis `skip: date[]`, as datas de choque que viu o xestor) e `Idempotency-Key` (obrigatoria; `IdempotencyKey`, 1–255). Nunha transacción, **orde de bloqueo única instalación → entidade → solicitude/serie**: `FacilityQueries.lockFacility` → entidade `for share` (404 se xa non existe ou está arquivada) → volve calcular todo → se as datas de choque non son **exactamente** `skip`, **409 `booking.series-conflicts-changed`** coa vista previa nova en `preview` (extensión RFC 9457) e sen escribir. Se coinciden, escribe a serie e as sesións libres e responde 201 `SeriesResponse`.
   - **Clave de reintento:** gárdase na serie coa pegada SHA-256 da entrada canónica (`idempotency_fingerprint`, o mesmo cálculo ca `booking_idempotency_key`). A mesma clave e a mesma pegada → 200 coa serie xa creada; a mesma clave con outra pegada → 422 `booking.idempotency-key-reused`.
   - **Escritura en lote** (para non pasar do `lock_timeout = 5s` da conexión: cunha tempada diaria dun ano, unha a unha, serían ~1.800 sentenzas e quen reserva nesa instalación recibiría 503 `common.busy`): os códigos `FUR-XXXX` xéranse todos e compróbanse cunha consulta (`select code from booking where code in (:codes) and municipality_id = :m`; os que chocan vólvense xerar); despois **unha sentenza por táboa** para `booking`, `booking_occupancy` e `booking_event` (`CREATED`): `insert … select` sobre `unnest` das listas, que van como texto (A8b só admite `JdbcClient.sql` cun literal, así que nada de `batchUpdate`). Tres viaxes. A reserva leva `on conflict (municipality_id, code) do nothing returning id`: se outra reserva colleu un código entre a consulta e a escritura, esas sesións vólvense escribir con outro. `SeriesIT` crea unha tempada diaria dun ano (366 datas) por debaixo de 1 s. Se a base rexeita unha ocupación (que baixo o bloqueo non debería pasar), desfaise todo e responde 409 `booking.slot-taken`.
   - Despois do commit, `BookingSeriesCreated(municipalityId, seriesId)` (sen persoas). As sesións non publican `BookingConfirmed`.
9. **Cancelar desde a lista de choques reutiliza o que hai:** `POST …/bookings/{id}/cancel` con `channels` (o diálogo de sempre), co permiso `booking.cancel` e o seu rexistro: a veciña recibe o correo «cancelada polo concello» e as esperas o aviso «Quedou libre». Despois a web volve pedir a vista previa. Non hai cancelación en lote.
10. **A entidade** (`@PersonUseCase`). É responsable quen entra cun correo verificado que está en `booking_organization_manager` dunha entidade sen arquivar dese concello. Comprobado dentro de cada caso de uso; unha conta de traballo non actúa como entidade (403 `booking.staff-account`).
    - `GET …/me/organizations` → `[{id, name}]` (a web mostra «Eu · ‹Entidade›» só se non está baleira; cunha conta de traballo, baleira).
    - `GET …/me/organizations/{orgId}/bookings?scope=UPCOMING|HISTORY&page=` → `MyBookingResponse[]` (a mesma forma, con `organization {id, name}` e, nas sesións, `series {weekdays, startMinute, endMinute, firstDate, lastDate}`); `GET …/{id}` (con QR). Sen ser responsable: 404 `booking.organization-not-found`.
    - **Cancelar** `POST …/me/organizations/{orgId}/bookings/{id}/cancel` (`If-Match`): unha sesión, ata que empeza e sen custo; unha reserva extra, coas normas de sempre (`cancel_until`). Estado `CANCELLED_BY_PERSON`, actor = a persoa responsable. Solta a ocupación e chama a `WaitNotifier.released`.
    - **Non hai ruta para mover** unha sesión nin unha reserva extra (punto 1).
    - **Reservar como entidade** `POST …/me/organizations/{orgId}/bookings`, o corpo de `POST …/me/bookings` sen `participants` e coa súa `Idempotency-Key`. **«Reservar como» decídese pola ruta.** Mesmo fluxo ca `ReserveService`, coas diferenzas: despois do bloqueo da instalación, a entidade `for share` (404 se xa non existe ou está arquivada: sen isto, a FK daría `23503` e un 500); o titular é a entidade; **o límite do uso cóntase para a entidade coma unha persoa** (`organization_id = :org and series_id is null`); sen participantes nin usos por prazas; con bloqueo por faltas vixente da persoa responsable, 403 `booking.blocked-no-show`.
    - **Solicitar tempada** `POST …/me/organizations/{orgId}/series-requests` con `SeriesRequestRequest {facilityId, useId, partId?, weekdays, startMinute, endMinute, firstDate, lastDate, note?}` → 201 `MySeriesRequestResponse {id, facilityName, useName, partName?, weekdays, startMinute, endMinute, firstDate, lastDate, note?, status, rejectionMessage?, createdAt}`: a validación de forma do punto 8 (sen choques), sobre unha instalación visible para a veciñanza; como moito **5 pendentes por entidade** (422 `booking.series-request-limit`). `GET …/series-requests` e `DELETE …/series-requests/{id}` (só pendente; se non, 409 `booking.series-request-decided`).
11. **Despois de crear** (permiso `booking.organizations`, salvo onde se di).
    - **Cancelar unha sesión:** `POST …/bookings/{id}/cancel`, o de sempre (`booking.cancel`).
    - **Rematar a tempada** `POST …/booking-series/{id}/end` `{from}` → `SeriesResponse`: baixo o bloqueo da instalación (e despois a serie `for update`), cancela nunha sentenza as sesións confirmadas, sen marca de asistencia e que aínda non empezaron desde ese día (`CANCELLED_BY_MUNICIPALITY`), solta as súas ocupacións e escribe a historia (co aviso por correo) noutras dúas, garda `ended_from`, chama unha vez a `WaitNotifier.released` e publica `BookingSeriesEnded(municipalityId, seriesId, from, cancelled, noticeId)`. Un só correo, sempre. `from` entre hoxe e a última data (422 `booking.series-end-invalid`); repetilo non cancela nada máis.
    - **Pago da tempada** `PUT …/booking-series/{id}/payment` `{status}` (`If-Match`) → `SeriesResponse`. **Ao nacer** (créea o xestor ou saia dunha solicitude aprobada), «Pendente de pago» (`PENDING`) se o uso reservado ten prezo (o mesmo que decide o cobro dunha reserva normal: un prezo de pago maior que cero) e «Gratis» (`FREE`) se non o ten (gratis ou sen prezo). Despois cámbiao o xestor co chip-menú.
    - **Cambios posteriores na instalación:** as sesións futuras saen no modal de reservas afectadas de sempre co nome da entidade; `CANCEL` manda o correo de sempre á entidade.
    - **Non se edita unha serie:** rématase e créase outra.
    - **Borrar unha instalación** con series ou solicitudes: `BookingSql.anyOf` (o `hasBookings` de `BookingImpactService`, que mira `FacilityService` antes de borrar) conta tamén `booking_series` e `booking_series_request` desa instalación; a instalación non se borra (o erro de sempre, `facility.has-bookings`).
    - **Listas:** `GET …/booking-series?status=ACTIVE|ENDED` e `GET …/booking-series/{id}` → `SeriesResponse {id, organizationId, organizationName, facilityId, facilityName, useId, useName, partId?, partName?, weekdays, startMinute, endMinute, firstDate, lastDate, paymentStatus, sessions (confirmadas), cancelled, endedFrom?, requestId?, version}` (unha soa forma para a lista e o detalle). `ManagedBookingResponse`, `ManagedBookingDetailResponse` e `MyBookingResponse` gañan `organization? {id, name, seriesId?, series?}`; `seriesId` vai en toda sesión (para quen vexa a reserva: a web non ofrece «Cambiar» nunha sesión e o detalle dunha tempada lista as súas) e `series {id, weekdays, startMinute, endMinute, firstDate, lastDate, sessions, paymentStatus}` vai nas sesións (no detalle da xestión, só con `booking.organizations`). Nunha reserva da entidade as canles de aviso son só `EMAIL`. «Últimas reservas» deixa fóra as sesións (`BookingFilter.withSessions`); a busca e o CSV inclúenas.
    - **Quen fixo a reserva:** `ManagedBooking.bookedBy` resolve só o persoal; para unha reserva extra da entidade, a ficha e o CSV (`BookingCsv`, columna «Reservada por») mostran a entidade, non a persoa responsable.
    - **Solicitudes:** `GET …/booking-series-requests?status=PENDING` e `GET …/{id}` → `SeriesRequestCardResponse {id, organizationId, organizationName, facilityId, facilityName, useId, useName, partId?, partName?, weekdays, startMinute, endMinute, firstDate, lastDate, status, createdAt, note?, requestedByEmail?, rejectionMessage?, seriesId?}`; `GET …/booking-series-requests/pending-count` → `{count}`. **Aprobar** `POST …/{id}/approve` con `CreateSeriesRequest` (axustable) e `Idempotency-Key`: crea a serie co punto 8 e, na mesma transacción e **despois** dos bloqueos da instalación e da entidade, bloquea a solicitude (`for update`; xa decidida, 409 `booking.series-request-decided`) e márcaa `APPROVED`. **Rexeitar** `POST …/{id}/reject` `{message}` (obrigatoria; 422 `booking.series-request-message-required`) → `REJECTED` e `BookingSeriesRequestRejected`. Ningún camiño bloquea primeiro a solicitude e despois a entidade ou a instalación: sen ciclos con «Eliminar».
12. **Correos á entidade.** Todos ao **correo da entidade** no primeiro idioma do concello; mesma plantilla, mesmo rexistro e mesmo reintento ca os demais; despois do commit.
    - **Os de cada reserva** (cancelada ou cambiada polo concello, cancelada pola entidade, confirmada a extra): os de sempre. Cambia `BookingQueryService.notice`: hoxe descarta a reserva sen correo de titular; cunha da entidade usa o correo da entidade, o seu nome e `holderHasAccount = false`, e `BookingNotice` gaña `organization` (booleano). En `notification`, `BookingMailComposer` co `organization` aceso: unha liña «Sesión da tempada de ‹entidade›» ou «Reserva de ‹entidade›» no lugar do saúdo e **sen** o pé «Se esta reserva non é túa» (hoxe vai por `bookedByStaff`); nunha sesión, sen prezo nin prazo para cancelar.
    - **Tres novos.** `notification_delivery` gaña `series_id` e `series_request_id`. A migración **parte da última** que redefina `notification_delivery_kind_ck` e `notification_delivery_target_ck` (a de [ADR-0040](0040-buscar-xente-para-xogar.md), se xa está; se non, a última do árbore): volve declaralas coa lista anterior máis `SEASON_CREATED`, `SEASON_ENDED` e `SEASON_REQUEST_REJECTED`, e `num_nonnulls(…, series_id, series_request_id) = 1`; regras tipo↔destino `(kind in ('SEASON_CREATED', 'SEASON_ENDED')) = (series_id is not null)` e `(kind = 'SEASON_REQUEST_REJECTED') = (series_request_id is not null)`; índices únicos parciais `(municipality_id, series_id, kind, event_id)` e `(municipality_id, series_request_id, kind, event_id)`. `BookingQueries` gaña `seriesNotice(seriesId)` → `SeriesNotice(organizationName, email, language, facilityName, facilityAddress, useName, partName, weekdays, startMinute, endMinute, firstDate, lastDate, sessions, skipped: date[], fromRequest, endedFrom?, cancelled)` e `seriesRequestNotice(requestId)` → `SeriesRequestNotice(organizationName, email, language, facilityName, useName, partName, weekdays, startMinute, endMinute, firstDate, lastDate, message)`. Os tres eventos entran en `MailRetry.MAIL_EVENTS`.

      | `MailKind` | Asunto (gl / es) | Corpo |
      |---|---|---|
      | `SEASON_CREATED` | «Tempada asignada: ‹instalación›» / «Temporada asignada: ‹instalación›» (de solicitude: «Solicitude aprobada: ‹instalación›» / «Solicitud aprobada: ‹instalación›») | Instalación · parte · uso; días e horas; «do ‹data› ao ‹data› · ‹N› sesións»; se os hai, «Non se crearon: ‹datas›»; botón «Ver as sesións» → `/‹concello›/reservas` (pestana da entidade). Sen `.ics` |
      | `SEASON_ENDED` | «Tempada rematada: ‹instalación›» / «Temporada terminada: ‹instalación›» | «Desde o ‹data› cancelamos as ‹N› sesións que quedaban. As horas quedan libres.» / «Desde el ‹fecha› hemos cancelado las ‹N› sesiones que quedaban. Las horas quedan libres.» (cunha soa, «a sesión que quedaba»; sen ningunha, «xa non quedaban sesións por cancelar») |
      | `SEASON_REQUEST_REJECTED` | «Solicitude de tempada non aprobada: ‹instalación›» / «Solicitud de temporada no aprobada: ‹instalación›» | O que se pediu e a mensaxe do concello, tal cal (texto plano, escapado) |
13. **Permisos** (declarados en `BookingPermission`, cos seus roles; `RolePermissions` xúntaos).

    | Permiso | Que | Xestión | Recepción | Plataforma |
    |---|---|---|---|---|
    | `booking.organizations` (novo) | Entidades, responsables, tempadas (crear, rematar, pago), solicitudes | Si | Non | Non |
    | `booking.view` (o de sempre) | Ver as sesións no calendario, na busca e no detalle, co nome da entidade | Si | Si | Non |
    | `booking.cancel` (o de sempre) | Cancelar unha sesión ou reserva da entidade, tamén desde os choques | Si | Non (coma hoxe) | Non |
    | `booking.attendance` (o de sempre) | Validar o QR dunha sesión | Si | Si | Non |
14. **Asistencia e faltas (o conservador).** As reservas da entidade valídanse co QR e o persoal pode marcar «Non presentado» a man; **nunca contan para a penalización de ninguén**, non mandan correo de falta e `NoShowService` déixaas fóra da falta automática (`and organization_id is null` na consulta de candidatas).
15. **Indicadores e datos abertos.** **Ocupación: si** (minutos, ocupación, mapa de calor e ocupación dos datos abertos, sen cambiar consultas). **«Reservas»: si**, cada sesión conta como unha. **«Persoas distintas»:** unha entidade conta como unha (`count(distinct coalesce(holder_subject, 'o:' || organization_id::text, case when anonymized_at is null then id::text end))` en `BookingStatsSql`); como a entidade con historial se arquiva e non se borra, os períodos pasados non cambian. **Cobros:** as sesións non teñen importe; o pago da tempada non entra nas cifras. Nin as entidades nin as tempadas se publican.
16. **Portabilidade.** Fóra de `furar-export`, coma as reservas ([ADR-0018](0018-portabilidade.md)).
17. **Rexistro de actividade** (só o persoal): `booking.organization-create|update|archive|delete` (`organizationId`, nome, cantos responsables se engadiron e quitaron, nunca os correos; contacto, correo e teléfono só como campo cambiado oculto); `booking.series-create` (`seriesId`, `organizationId`, `facilityId`, `sessions`, `skipped`, `requestId`); `booking.series-end` (`seriesId`, `from`, `cancelled`); `booking.series-payment` (`seriesId`, de e a); `booking.series-request-reject` (`requestId`, `organizationId`; nunca a mensaxe). O que fai a persoa responsable queda en `booking_event` (actor = a súa conta).
18. **Datos persoais** (base 6.1.e, coma as reservas; filas en [`datos-persoais.md`](../datos-persoais.md)).
    - **Contacto da entidade** (nome de contacto, correo, teléfono) e **responsables** (correo, quen o engadiu e cando): mentres a entidade estea dada de alta; arquivala ou eliminala bórraos.
    - **Solicitudes:** conta e correo de quen pide e a nota; as decididas bórranse 1 ano despois (`furar.booking.series-request-retention`, `P1Y`, en `BookingRetentionService`).
    - **Anonimización das reservas da entidade** (2 anos; con prezo, 5): ademais do de sempre, como non teñen `holder_subject`, a tarefa pon `furar:anonymized` en `booking_event.actor_subject`, `booking.cancelled_by` e `booking.booked_by` cando gardan a conta dunha persoa responsable (hoxe `forgetEventActors` e `forgetCanceller` comparan con `holder_subject` e deixarían esa conta para sempre; `booked_by` consérvase ao anonimizar). O disparador de `booking_event` xa o permite.
    - **Baixa da conta:** a migración redefine `close_account` partindo da **última** redefinición que haxa (a de [ADR-0040](0040-buscar-xente-para-xogar.md), se xa está): antes de borrar `account_email`, borra os responsables co correo desa conta e pon a nulo o solicitante das súas solicitudes.
    - **Textos** (`web/src/i18n/{gl,es}.json`): `privacy.residents.dataOrganizations` — «Das entidades (clubs, escolas, asociacións) coas que o concello reparte horarios: o nome, correo e teléfono de contacto e os correos das persoas responsables, que dá a propia entidade; e, nas solicitudes de tempada, a conta e o correo de quen as fai.» / «De las entidades (clubes, escuelas, asociaciones) con las que el ayuntamiento reparte horarios: el nombre, correo y teléfono de contacto y los correos de las personas responsables, que facilita la propia entidad; y, en las solicitudes de temporada, la cuenta y el correo de quien las hace.»; `privacy.retention.organizations` — «Entidades: mentres estean dadas de alta. Solicitudes de tempada: un ano despois de decididas.» / «Entidades: mientras estén dadas de alta. Solicitudes de temporada: un año después de decididas.»
19. **Erros** (`BookingError`; texto en `messages/booking_{gl,es}.properties` e na web):

    | Código | HTTP | Galego | Castelán |
    |---|---|---|---|
    | `booking.organization-not-found` | 404 | Esta entidade xa non existe. | Esta entidad ya no existe. |
    | `booking.organization-name-taken` | 409 | Xa hai unha entidade con ese nome. | Ya hay una entidad con ese nombre. |
    | `booking.organization-managers-limit` | 422 | Unha entidade pode ter ata {max} responsables. | Una entidad puede tener hasta {max} responsables. |
    | `booking.organization-has-future-bookings` | 409 | A entidade ten sesións ou reservas futuras. Cancélaas ou remata a tempada antes. | La entidad tiene sesiones o reservas futuras. Cancélalas o termina la temporada antes. |
    | `booking.organization-seats-use` | 422 | Neste uso resérvase por prazas: non vale para unha entidade. | En este uso se reserva por plazas: no vale para una entidad. |
    | `booking.series-weekdays-required` | 422 | Escolle polo menos un día da semana. | Elige al menos un día de la semana. |
    | `booking.series-time-invalid` | 422 | As horas van de 15 en 15 minutos e a de fin é despois da de inicio. | Las horas van de 15 en 15 minutos y la de fin es después de la de inicio. |
    | `booking.series-range-invalid` | 422 | As datas van desde hoxe e como moito un ano. | Las fechas van desde hoy y como mucho un año. |
    | `booking.series-conflicts-changed` | 409 | Mentres tanto cambiou algún día. Revisa os choques e volve crear. | Mientras tanto ha cambiado algún día. Revisa los choques y vuelve a crear. |
    | `booking.series-not-found` | 404 | Esta tempada xa non existe. | Esta temporada ya no existe. |
    | `booking.series-end-invalid` | 422 | Escolle un día desde hoxe e dentro da tempada. | Elige un día desde hoy y dentro de la temporada. |
    | `booking.session-not-editable` | 422 | Unha sesión da tempada non se cambia: cancélaa e, se fai falta, reserva outra hora. | Una sesión de la temporada no se cambia: cancélala y, si hace falta, reserva otra hora. |
    | `booking.series-request-not-found` | 404 | Esta solicitude xa non existe. | Esta solicitud ya no existe. |
    | `booking.series-request-decided` | 409 | Esta solicitude xa se decidiu. | Esta solicitud ya se ha decidido. |
    | `booking.series-request-limit` | 422 | A entidade xa ten {max} solicitudes pendentes. | La entidad ya tiene {max} solicitudes pendientes. |
    | `booking.series-request-message-required` | 422 | Escribe a mensaxe para a entidade. | Escribe el mensaje para la entidad. |

    Valen os de sempre (`booking.facility-not-bookable`, `booking.use-not-available`, `booking.slot-taken`, `booking.blocked-no-show`, `booking.staff-account`, `booking.holder-not-editable`, `booking.idempotency-key-missing`, `booking.idempotency-key-reused`).
20. **Horas.** As series en reloxo de parede (`date` + `startMinute` + `endMinute`, a excepción de [ADR-0022](0022-instalacions-horario-e-orde-de-bloqueo.md) punto 8); as sesións, en `Instant`. O servidor converte.
21. **Demo de Narón** (`R__demo_naron_seasons.sql`, despois de `R__demo_naron_bookings.sql`): tres entidades ficticias («Club Demo Baloncesto», «Club Demo Patinaxe», «Escola Demo de Fútbol Sala») de ids fixos, contacto `@example.org`; `vecino-naron@example.org` é responsable da primeira. Unha tempada (luns e mércores, 19:00–20:30, nunha parte do pavillón, ata xuño, pago pendente) e unha solicitude pendente da segunda, cunha nota. **O patrón de `R__demo_naron_bookings.sql`:** cada sesión sáltase (`continue when exists …`) se a ocupación xa está collida ou o día está pechado, para que a carga nunca falle; datas relativas ao día de carga.

## Consecuencias
- Unha sesión e unha reserva nunca se pisan, pola mesma exclusión da base; e todo o que xa le reservas ve as sesións.
- O núcleo das reservas cambia: dous campos en `Booking`, unha decena de `select`, `BookForService`, a retención, as estatísticas e as faltas. É a parte de máis risco.
- Na primeira versión as sesións non se moven: cancélanse e resérvase outra hora.
- Unha entidade con historial non se borra: arquívase sen contacto nin responsables.
- Os indicadores contan cada sesión como unha reserva.
- `close_account` e as restricións de `notification_delivery` redefínense outra vez, despois das de ADR-0040.

## Preguntas abertas
Ningunha de produto: as do primeiro borrador decidíronse o 2026-11-12 (punto 1).

## Alternativas descartadas
- **Táboa propia de sesións que ocupa:** a ocupación ten `booking_id not null`; habería que ensinar todas as lecturas a outra táboa e a base deixaría de impedir que se pisen.
- **Módulo `organization` ou `season` aparte:** un ciclo ou unha SPI e unha FK máis entre módulos.
- **Entidades en `people`:** `people` é da conta, sen concello.
- **A sesión a nome do correo da entidade:** «a reserva é do correo» ligaríaa á conta dese correo, ao seu límite e ás súas faltas.
- **«Avisos pendentes» e bandexa:** o mantedor decidiu que un día que choca non se crea.
- **Pisar ou mover a reserva dunha veciña ao crear:** nunca.
- **Validar os choques só na vista previa** ou **crear menos sen avisar:** volver calcular baixo o bloqueo e comparar co que viu o xestor.
- **Mover sesións polo camiño de sempre:** calcula o fin por franxas, esixe a grella e pon o prezo do uso; fóra da primeira versión.
- **Borrar a entidade con historial:** as reservas pasadas quedarían soltas e os indicadores pasados cambiarían.
- **Escribir as sesións unha a unha:** con centos de sesións o bloqueo pasaría do tempo de espera da conexión.
- **Un correo por sesión:** un só con todo.
- **Conta compartida da entidade:** cada persoa coa súa conta e o seu correo.
- **Contar as sesións no límite da entidade:** non podería reservar nunca unha hora extra.

## Construción
- **API (2026-11-12):** pasos 1 a 9 do plan. Probas: `SeriesPlannerTest`, `OrganizationTest`, `OrganizationBookingTest`, `OpenMatchOrganizationTest`; `OrganizationIT`, `OrganizationBookingsIT`, `SeriesIT` (choques, 409 coa vista previa nova, clave de reintento, concorrencia cunha reserva da veciñanza, sesión que non se move, rematar, pago, unha tempada dun ano por debaixo de 1 s), `SeriesRequestIT` (pedir, límite, retirar, aprobar axustando, aprobar dúas veces á vez, eliminar e aprobar á vez sen interbloqueo, instalación con solicitude que non se borra), `SeriesMailIT`, `BookingRetentionIT` e `CloseAccountIT`; os tres `<Clase>TenantIT`. Demo en `R__demo_naron_seasons.sql`.
- **Web:** feita (pasos 10 a 14 do plan: pestana «Entidades», «Nova reserva › Tempada», «Tempadas» e detalle, «Eu · ‹Entidade›» e «Reservar como»).
