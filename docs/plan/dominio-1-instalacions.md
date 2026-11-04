<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Plan do dominio 1: instalacións, horarios e bloqueos

> **Histórico: manda a especificación e as ADR.** Este plan guiou a construción do dominio e non se mantén ao día; se algo non coincide coa [especificación](../produto/especificacion.md) ou coas [ADR](../adr/), valen elas.

**Estado:** executado (histórico). Era a proposta inicial, v2 tras a revisión de backend, infra, arquitectura, frontend e deseño · 2026-10-25 · semana do 25 ao 31 de outubro. Fonte de produto: [especificación](../produto/especificacion.md), bloques 1, 1b e 2 (revisados o 2026-10-25). Onde este plan e a especificación non coincidan, manda a especificación. O que falta de produto está no §6 e **non se decide ao construír**: constrúese o valor por defecto marcado e cámbiase cando produto responda.

Lectura obrigatoria para cada tarefa: este ficheiro, as regras do seu perfil (§4) e a súa fila (§3). O que cambiou respecto á v1 e o que se descartou das revisións está no §7.

---

## 1. Alcance técnico

**Entra (dominio 1, construído e probado de punta a punta para o xestor):**
- Listado de instalacións (tarxetas alfabéticas no idioma de quen mira, chip de estado con menú) e «Nova instalación» (nome + enderezo → ficha en borrador co aviso do que falta).
- Ficha única por instalación: fotos, cabeceira (nome, enderezo, estado), seccións Usos · Descrición · Horario · Bloqueos · Servizos e accesibilidade · Localización, e tarxeta Datos (deportes, tipo, RIDEGA, teléfono, correo, campos propios, reserva por internet / mostrar ocos, cuberta). Edición no sitio e «Gardar» por sección.
- Usos (enteira · partes con nome · prazas; 30/60/90/120 min; sempre · só unhas datas; «bloquea os demais»).
- Horario: tempadas sen solapes, semana con tramos e fila de festivos, excepcións, festivos nacionais e autonómicos sementados.
- Bloqueos, co contrato do modal de reservas afectadas (a parte que toca reservas complétase no dominio 2).
- Enderezos con CartoCiudad a través do servidor e mapa OSM servido desde a mesma orixe.
- Campos personalizados en táboas tipadas, reutilizables por destino (Axustes) e na tarxeta Datos.
- Compoñentes propios en `shared/ui`: selector de hora, calendario e campo de data, menú, diálogo e confirmación, interruptor, segmentado tipo radio, pestanas, combobox con suxestións, sección editable, mapa.

**Non entra (vai ao dominio 2 ou a F2):** reservas e ocupación real, vista da veciñanza, normas, tarifas, permisos do conserxe, avisos. Ver §5.3.

### 1.1 Que se rehace ou se borra da base actual

| Peza actual | Que pasa | Onde |
|---|---|---|
| `custom_field_definition` con `target` único, `options jsonb`, `rules jsonb`, `position`, `group_*`, `applies_when` | Substitúese por `custom_field`, `custom_field_option`, `custom_field_usage`, `custom_field_value`, `custom_field_value_option`. Fóra `position` e grupos (orde alfabética, especificación 1b) e `applies_when` (non está na especificación; P28) | D1-10a |
| `municipality.custom_fields jsonb` + índice GIN | Os valores mígranse a `custom_field_value` na mesma migración (cun bloque `DO` que compara recontos antes do `DROP`) e a columna bórrase. `db/demo/R__demo_municipalities.sql` pasa a escribir en `custom_field_value` | D1-10a |
| `shared.domain.CustomFieldValues` | Queda (o dominio segue gardando valores validados), pero xa non se serializa a JSONB: `fromStorage` pasa a ser o lector das táboas tipadas | D1-10a |
| Formato de `furar-export` (sección `customfields`) | Sobe a `version: 2` (`usages[]` en vez de `target`); o importador segue lendo a v1 (ADR-0018) | D1-10e |
| Plantilla `templates/module` modo `--custom-fields` (columna jsonb + GIN) | Pasa ao almacén tipado ou quítase o modo, o máis pequeno que deixe D45 en verde | D1-10b |
| Espazo, árbore `parent_id`, «espazo folla», `CapacityChangeGuard`, `lockLeaves` (ADR-0012) | Desaparecen. Ningún código de produción os usa; só o modelo de test de `OccupancyModelIT` | D1-02 |
| `api/src/test/resources/db/occupancy-model/V1__occupancy_model.sql` e `OccupancyModelIT` | Reescríbense co modelo novo (instalación + uso + parte/prazas) | D1-02 |
| Módulo `calendar` previsto en `arquitectura.md` §3 | **Non se crea**: tempadas, excepcións, festivos e bloqueos van dentro de `facility` (§1.2.1) | D1-01b |
| ADR-0005, 0006, 0011, 0012, 0015, 0018, 0021 | Revísanse no seu ficheiro (ADR-0001) | D1-01a, D1-01b |
| ADR novas | ADR-0022 «Instalacións, horario e orde de bloqueo» e ADR-0023 «Enderezos e mapa sen terceiros» | D1-01a, D1-01b |
| `docs/arquitectura.md` §3, §8, §9; `docs/glosario.md` (espazo, folla, enderezo, catálogo, ocupación); `docs/deseno.md` §8 (nomes e estado dos compoñentes) | Actualízanse | D1-01b |
| `ModularityTest` (M3) | Lista de dependencias esperadas con `facility` | D1-11a |
| DoD D18 e D19 | **Mesmas filas, outros nomes de test** (non se engaden filas; ADR-0016) | D1-02, D1-10a |
| Maqueta do listado co bloque «Complexo» | Non se constrúe o complexo: listado plano | D1-20 |

### 1.2 Decisións técnicas (non son produto)

1. **Un só módulo `facility`** (instalación, usos, partes, fotos, servizos, deportes, enderezo, estado **e** horario, excepcións, festivos e bloqueos, no subpaquete `schedule` de cada capa: `domain.schedule`, `application.service.schedule`, `adapter.out.persistence.schedule`…). Motivo: «¿pódese reservar aquí e agora?» queda nun só dono; desaparecen unha SPI, unha FK entre módulos e a coordinación do bloqueo entre dous módulos. `booking` dependerá só de `facility` para isto.
2. **Unha SPI cara a `booking`, un só bean.** `facility` declara na raíz `BookingImpact` con `List<AffectedBooking> affectedBy(FacilityChange)` e `void apply(FacilityChange, List<BookingDecision>)`. Inxéctase un único bean; `facility` trae `NoBookingImpact` (lista baleira) con `@ConditionalOnMissingBean`, e `booking` substitúeo no dominio 2. `FacilityChange` é selado: `Closure` (pechar temporalmente), `Withdrawal` (dar de baixa), `ExclusiveUse` (uso que bloquea os demais nas súas datas), `Block` (bloqueo novo ou cambio de período), `UseChanged`, `UseArchived` e, se produto o confirma (P6), `ScheduleChanged`. `apply` **só cambia estado na transacción**; os avisos (correo, web) saen despois do commit por evento de Modulith (`event_publication` xa existe). Nada de SMTP dentro do bloqueo.
3. **Borrador calculado, non gardado:** `missing ∈ {SPORTS, USES, SCHEDULE}` calcúlase no módulo, por lote (`missing(Set<UUID>)`). Para o dominio 2, `FacilityQueries.visibleIds(page)` resolve en SQL «activa e `missing` baleiro» sen filtrar en memoria.
4. **Orde de bloqueo única (ADR-0022).** Todo o que cambia a reservabilidade toma primeiro `select … from facility where id = ? for no key update` (`FacilityLock.lock(id)`; exposto a `booking` como `FacilityQueries.lockFacility(id)`): estado, usos e os seus períodos, tempadas e semana, excepcións e bloqueos. No dominio 2, `booking` toma o mesmo bloqueo **antes** de comprobar estado, horario, bloqueos e «bloquea os demais», e despois escribe a ocupación. Orde sempre instalación → ocupación. Constrúese en D1-11a e úsase xa no dominio 1.
5. **Que garante a base e que garante o bloqueo.** A base garante, declarativamente, **un uso por instalación e franxa e o cupo** (partes e prazas), como di a especificación; D1-02 próbao. Bloqueos, peche, horario e «bloquea os demais» **non** van na exclusión: compróbaos `booking` baixo o bloqueo do punto 4. Así encaixa «manter»: a reserva mantida segue na ocupación e o bloqueo simplemente non a toca (a decisión, quen e cando, gárdaa `booking`). Sen uso centinela nin filas falsas en `facility_use`.
6. **Modal de reservas afectadas = un fluxo en dous pasos sobre o mesmo endpoint.** Gardar un bloqueo, cambiar o estado ou gardar un uso que bloquea: baixo o bloqueo, se `BookingImpact` devolve reservas e a petición non trae decisión para todas, 409 `facility.affected-bookings` con `affected[]` (extensión RFC 9457). A web abre o modal e reenvía a mesma petición con `decisions[] {bookingId, action: CANCEL|KEEP, channels[]}`; `channels ⊆ {EMAIL, WEB, PHONE}`, ≥ 1 se `CANCEL`, baleiro se `KEEP` («se se mantén, non se avisa»). Se as afectadas cambiaron entre os dous pasos, volve 409 coa lista nova.
7. **Gardar por sección = un endpoint por sección** con `If-Match`: o da instalación para as seccións da propia instalación (tamén estado e fotos) e o de cada elemento para usos, tempadas, excepcións e bloqueos. Cada resposta devolve o recurso enteiro e `ETag` novo. Sen `PATCH` con semántica de nulos.
8. **Horas sempre de reloxo de parede na API.** Semana, excepcións e **bloqueos** van como `date` + `startMinute` + `endMinute` (0–1440, múltiplos de 15; 1440 = medianoite, P12). O servidor converte os bloqueos a `tstzrange` en Europe/Madrid. `GET …/schedule` devolve `today` e `now` (Europe/Madrid) para «Hoxe» e «Agora»: o navegador non calcula zonas.
9. **Fotos na base** (`bytea` con `storage external`, táboa aparte do metadato). Só JPEG e PNG ≤ 8 MB; antes de decodificar, `ImageReader` le ancho e alto e rexeita > 40 Mpx; aplícase a orientación EXIF e despois quítase todo o EXIF (GPS incluído); reencodado a 1600 px e miniatura de 480 px, con semáforo de 2 reencodados á vez; máximo 12 por instalación. Límites: Caddy 9 MB só na ruta de subida; Spring `max-file-size=8MB`, `max-request-size=9MB`. Sérvense desde a API con `Cache-Control: immutable` e id novo en cada cambio.
10. **Enderezos:** porto `AddressSearch` (`suggest`, `resolve`, `reverse`), adaptador CartoCiudad con `RestClient` (connect 2 s, read 3 s, sen reintentos, `limit=10`, só con 3–100 caracteres, caché LRU en memoria de 24 h, filtro polo INE do concello) e adaptador `fixed` (Narón e Ferrol). `FURAR_GEOCODER=cartociudad|fixed` é variable propia (por defecto `cartociudad`; `fixed` en tests e e2e), **non** atada ao perfil `demo`. Saída a internet por proxy opcional (`FURAR_HTTP_PROXY`). Só `MANAGER`/`STAFF`; nunca `@PublicEndpoint`. Erro 503 `facility.geocoder-unavailable` → a web deixa encher a man.
11. **Mapa:** Leaflet 1.9.4 (BSD-2-Clause), CSS cargado só ao abrir `furar-map` (entrada `styles` con `inject: false` e `<link>` creado polo compoñente; `style-src 'self'` igual), icona propia, sen estilos en liña. Teselas por Caddy en `/tiles/{z}/{x}/{y}.png` (z 0–19) cara a `FURAR_MAP_TILES_UPSTREAM` (esquema e host) + `FURAR_MAP_TILES_PATH`, sen `X-Forwarded-*`, `Cookie`, `Authorization` nin `Referer` cara a fóra, sen `Set-Cookie` de volta, co `User-Agent` de Furar e caché longa no navegador. Atribución «© OpenStreetMap» visible. Produción: servidor de teselas propio ou contratado (a política de OSM non admite tráfico de produción), en `instalacion.md`.
12. **Catálogos de plataforma:** `sport_catalog` e `public_holiday` sen `municipality_id`, en `TenancyAllowlist`, con `revoke insert, update, delete … from furar_app` na súa migración e un IT que o proba. Deportes creados con «+ crear» en `custom_sport` (por concello, RLS). Servizos e accesibilidade: `enum` + `check`; textos e iconas na web. Festivos: **unha soa lista efectiva para Galicia** (BOE, columna Galicia, e DOG), cada fila coa súa referencia nun comentario; 2026 e 2027 só se xa está publicado (P11).
13. **Nomes:** nome da instalación, do uso e da parte nun só texto; descrición e alt das fotos en gl e es (`LocalizedText` + `LanguagePolicy`). Unicidade sen maiúsculas con `create unique index` (non constraint), comprobada cun test con «Pabellón»/«PABELLÓN» e «Ñ» na configuración real da base.
14. **Orde alfabética no servidor:** `order by name collate "gl-x-icu"` ou `"es-x-icu"` segundo `Accept-Language`. Ningún índice usa esas colacións (`ALTER COLLATION … REFRESH VERSION` documentado en `instalacion.md`).
15. **Restricións e Hibernate:** Hibernate executa INSERT antes que DELETE. Por iso: semana da tempada e portada das fotos fanse con `DELETE`/quitar portada + `flush()` explícito no adaptador; nomes das partes con `exclude … where (archived_at is null) deferrable initially deferred` (o intercambio A↔B non se pode facer doutro xeito). Un IT por caso.
16. **Web:** Reactive Forms tipados (`FormGroup`/`FormArray`/`FormRecord`, sen Signal Forms); os campos propios son `ControlValueAccessor` que funcionan con `formControlName`. Datas e horas formátanse con `LanguageService.lang()` e `Intl.DateTimeFormat` (nunca co `LOCALE_ID` inxectado, que só se fixa ao arrancar); nomes de días e meses por `Intl`, non por Transloco.
17. **Sen cambios perdidos:** `CanDeactivate` co diálogo propio e `beforeunload` só mentres haxa unha sección sucia. 412 nunca tira o escrito: aviso con «Recargar» e os valores do xestor ata que decida.
18. **Rexistro de actividade:** cada caso de uso de escritura de `facility` e `customfields` escribe en `AuditLog`.

---

## 2. Modelo de datos e API

### 2.1 Táboas

Todas levan `municipality_id`, `unique (municipality_id, id)`, FK compostas **con `municipality_id` nos dous lados** (D14), RLS coa política `<táboa>_tenant` na mesma migración (modelo `V202610231100`; RLS sen `FORCE`, como agora), `created_at`, `updated_at` e `version` onde hai agregado. `btree_gist` xa existe (`V202610220850__platform_base.sql`). Nomes de migración `V{AAAAMMDDHHMM}__{modulo}_{que}.sql` coa hora real de creación; nunca se edita unha aplicada. **Regra de rebase:** se ao facer rebase sobre `main` o teu prefixo é menor ou igual ca o último de `migrations.lock`, renomea á hora actual e volve a `scripts/migracions.sh fixar`.

**`customfields`** (D1-10a)

| Táboa | Columnas clave | Garantías na base |
|---|---|---|
| `custom_field` | `code`, `type`, `label jsonb {gl,es}`, `help jsonb`, `personal_data`, regras tipadas (`min_length`, `max_length`, `min_number numeric`, `max_number`, `decimals`, `min_date`, `max_date`, `text_format`, `max_choices`), `status` | `unique (municipality_id, code)`; `unique (municipality_id, id, type)`; `unique (municipality_id, id, personal_data)`; checks de código, tipo e regras por tipo |
| `custom_field_option` | `field_id`, `code`, `label`, `archived` | `unique (municipality_id, field_id, code)`; `unique (municipality_id, field_id, id)` |
| `custom_field_usage` | `field_id`, `target`, `required`, `visibility`, `personal_data` (copia) | `unique (municipality_id, field_id, target)`; FK `(municipality_id, field_id, personal_data)` con `on update cascade`; `check (not personal_data or visibility <> 'PUBLIC')` |
| `custom_field_value` | `field_id`, `field_type` (copia), `target`, `entity_id`, `text_value`, `number_value numeric`, `date_value`, `bool_value`, `option_id` | FK `(municipality_id, field_id, field_type)`; FK `(municipality_id, field_id, target)` → usage; FK `(municipality_id, field_id, option_id)` → opción; `check` «exactamente a columna do tipo» (`MULTI_SELECT`: todas nulas); `unique (municipality_id, target, entity_id, field_id)`; `unique (municipality_id, id, field_id)` |
| `custom_field_value_option` | `value_id`, `field_id`, `option_id` | PK `(value_id, option_id)`; FK `(municipality_id, value_id, field_id)` → valor; FK `(municipality_id, field_id, option_id)` → opción |

`entity_id` é polimórfico e **sen FK** (ADR-0011 revisada xustifícao: RLS + comprobación no almacén de que a entidade é do concello, borrado e anonimizado vía `PersonalDataHolder`, `loadAll` dunha consulta por páxina). API pública (raíz, A19): `CustomFieldValidator.validate(target, raw, actor)`, `CustomFieldValueStore.replace(target, entityId, values)` e `loadAll(target, ids)`, `CustomFieldProjector`. O módulo dono chama a `replace` na mesma transacción e sobe a súa versión. Límites: 50 usos por destino; 32 KB por entidade.

**`facility`** (D1-11a, D1-11b, D1-12, D1-13)

| Táboa | Columnas clave | Garantías |
|---|---|---|
| `facility` | `name`, `status` (`ACTIVE`, `TEMPORARILY_CLOSED`, `WITHDRAWN`), `type` (`FIELD`, `SPORTS_HALL`, `PADEL_COURT`, `POOL`, `OUTDOOR_COURT`, `OTHER`, nulo), `description jsonb`, `ridega_code`, `phone`, `email`, `online_booking` (def. `true`), `show_free_slots` (def. `false`), `covered` (nulo = sen dato), `street`, `street_number`, `postal_code`, `locality`, `ine_code`, `latitude numeric(8,6)`, `longitude numeric(9,6)` | índice único `(municipality_id, lower(name))`; `check (not (online_booking and show_free_slots))`; rangos de lat/lon; enderezo e coordenadas `not null` |
| `sport_catalog` (plataforma) | `code` PK, `label jsonb` | sementada (~30); `revoke` de escritura a `furar_app` |
| `custom_sport` | `label jsonb` | índice único `(municipality_id, lower(label->>'gl'))` |
| `facility_sport` | `id`, `facility_id`, `sport_code`, `custom_sport_id` | `check` exactamente un dos dous; índices únicos parciais `(facility_id, sport_code) where sport_code is not null` e `(facility_id, custom_sport_id) where custom_sport_id is not null` |
| `facility_amenity` | `facility_id`, `code` | `check (code in (…))`; PK `(facility_id, code)` |
| `facility_use` | `facility_id`, `name`, `rental_mode` (`WHOLE`, `PARTS`, `SEATS`), `capacity`, `slot_minutes`, `availability` (`ALWAYS`, `DATES`), `blocks_others`, `archived_at` | `unique (municipality_id, facility_id, id)`; `unique (municipality_id, id, rental_mode)`; `check ((rental_mode = 'SEATS') = (capacity is not null))`; `capacity between 2 and 10000` (P3); `slot_minutes in (30,60,90,120)`; `check (not blocks_others or availability = 'DATES')`; índice único `(facility_id, lower(name)) where archived_at is null` |
| `facility_use_part` | `use_id`, `name`, `position`, `archived_at` | `unique (municipality_id, use_id, id)`; `exclude (use_id =, lower(name) =) where (archived_at is null) deferrable initially deferred`; ≥ 2 partes no dominio |
| `facility_use_period` | `use_id`, `from_date`, `to_date` | `check (to_date >= from_date)`; exclusión `(use_id =, daterange [] &&)`. Que dous usos «que bloquean» non se solapen compróbase no dominio baixo o bloqueo da instalación (§1.2.4; P2) |
| `facility_photo` → desde o 2026-10-26 `library_photo` + `facility_image` + `drawing` ([ADR-0024](../adr/0024-biblioteca-de-imaxes.md)) | biblioteca: `alt jsonb`, `width`, `height`, `content_type`, `pending_until`; lista: `facility_id`, `position`, `photo_id` ou `drawing_code` | a primeira é a portada; índices únicos parciais por foto e por debuxo; borrar e `flush` antes de inserir (§1.2.15); máx. 12 no dominio |
| `facility_photo_content` | `photo_id`, `variant` (`LARGE`, `THUMB`), `bytes bytea` (`storage external`) | PK `(photo_id, variant)`; `on delete cascade` |

Os usos e as partes **arquívanse, non se borran**: as reservas do dominio 2 apuntarán a eles con FK `(municipality_id, facility_id, use_id)`, `(municipality_id, use_id, part_id)` e `(municipality_id, use_id, rental_mode)` (ADR-0005 revisada: lista pechada de táboas publicadas).

**`facility.schedule`** (D1-15a, D1-15b, D1-16)

| Táboa | Columnas clave | Garantías |
|---|---|---|
| `season` | `facility_id`, `name`, `start_date`, `end_date` | exclusión `(facility_id =, daterange(start_date, end_date, '[]') &&)` → 409 `facility.season-overlap` |
| `season_hours` | `season_id`, `day` (1–7, 8 = festivos), `start_minute`, `end_minute` | `0 ≤ start < end ≤ 1440`, múltiplos de 15; exclusión `(season_id =, day =, int4range &&)`; a semana substitúese con `DELETE` + `flush` |
| `schedule_exception` | `facility_id`, `date`, `open`, `start_minute`, `end_minute`, `reason` | `unique (facility_id, date)`; `check (open = (start_minute is not null))`; un tramo (P9) |
| `public_holiday` (plataforma) | `date`, `name jsonb`, `source` | PK `date`; lista efectiva de Galicia; `revoke` de escritura a `furar_app` |
| `block` | `facility_id`, `date`, `start_minute`, `end_minute`, `period tstzrange` (calculado no servidor), `reason`, `created_by` | `check (lower(period) < upper(period))`; exclusión `(facility_id =, period &&)` → 409 `facility.block-overlap` (P7) |

A fila 8 (festivos) énchese coa do domingo ao crear ou copiar a tempada; non hai outra marca (`holidays_like_sunday` fóra).

### 2.2 API (alto nivel)

Prefixo `/api/v1/municipalities/{slug}`. Escritura: rol `MANAGER`; lectura de xestión: `MANAGER` e `STAFF`. Lectura pública: dominio 2, salvo o listado da portada (`GET …/public/facilities`, sen sesión: nin borrador nin de baixa; 2026-10-26). Erros RFC 9457 `modulo.motivo` en gl e es.

| Método e ruta | Que | Versión |
|---|---|---|
| `GET /facilities` | Lista (nome, enderezo curto, estado, `missing[]`, **`version`**), alfabética segundo `Accept-Language` | — |
| `POST /facilities` | Alta con nome e enderezo → 201 + `Location` + `ETag` | — |
| `GET /facilities/{id}` | Ficha completa (datos, deportes, servizos, usos, fotos sen bytes, campos propios proxectados, `missing[]`) | `ETag` |
| `PUT /facilities/{id}/name` · `/details` · `/description` · `/address` · `/amenities` | Unha por sección. `details` = tarxeta Datos | `If-Match` instalación |
| `PUT /facilities/{id}/status` | Cambio de estado; `decisions[]` (§1.2.6) | `If-Match` instalación |
| `POST /facilities/{id}/uses` · `PUT …/uses/{useId}` · `POST …/uses/{useId}/archive` | Usos coas partes (lista enteira, existentes por id) e períodos; `decisions[]` se bloquea | `If-Match` uso |
| `PUT /facilities/{id}/images` · biblioteca `GET`/`POST /photos` (multipart), `DELETE /photos/{id}` (só pendentes), `GET /drawings` | Imaxes (biblioteca do concello e debuxos, 2026-10-26, [ADR-0024](../adr/0024-biblioteca-de-imaxes.md)) | `If-Match` instalación (só `PUT …/images`) |
| `GET /facilities/{id}/photos/{photoId}/{large\|thumb}` | Bytes (`@PublicEndpoint`; só instalacións non retiradas) | `Cache-Control: immutable` |
| `GET /sports` · `POST /sports` | Catálogo + deportes do concello | — |
| `GET /address-search?q=` · `/address-search/{id}` · `/address-search/reverse?lat=&lon=` | Suxestións, resolución, punto → enderezo | — |
| `GET /facilities/{id}/schedule` | Tempadas coa semana, excepcións, festivos do ano visible, `currentSeasonId`, `today`, `now`, `gaps[]` (días sen tempada nos próximos 365) | `ETag` por tempada |
| `POST /facilities/{id}/seasons` (`copyFrom`) · `PUT …/seasons/{sid}` · `DELETE …/seasons/{sid}` | Tempadas | `If-Match` tempada |
| `POST/PUT/DELETE /facilities/{id}/schedule-exceptions[/{eid}]` | Excepcións | `If-Match` |
| `GET/POST/PUT/DELETE /facilities/{id}/blocks[/{bid}]` | Bloqueos (`date`, `startMinute`, `endMinute`, `reason`); `decisions[]` | `If-Match` |
| `GET /api/v1/holidays?year=` | Festivos de Galicia (`@PublicEndpoint`) | — |
| `…/custom-fields` | Como hoxe, pero con `usages[] {target, required, visibility}` en vez de `target` | `If-Match` |

`FacilityQueries` (para o dominio 2): `exists`, `summary(ids)`, `use(useId)` (modo, prazas, partes, duración), `lockFacility(id)`, `openingIntervals(facilityId, from, to) → List<Interval>` (tempada → festivo → excepción → menos bloqueos), `exclusiveUseOn(facilityId, date)`, `visibleIds(page)`. A aliñación das franxas (inicio do tramo ou en punto) é de `booking`; a firma non cambia por iso.

Erros novos (mínimo): `facility.not-found`, `facility.name-taken`, `facility.address-required`, `facility.affected-bookings`, `facility.use-name-taken`, `facility.use-parts-required`, `facility.use-capacity-required`, `facility.use-dates-required`, `facility.use-blocks-others-needs-dates`, `facility.exclusive-use-overlap`, `facility.use-has-bookings`, `facility.photo-too-large`, `facility.photo-too-many-pixels`, `facility.photo-unsupported-type`, `facility.photo-limit`, `facility.geocoder-unavailable`, `facility.season-overlap`, `facility.interval-invalid`, `facility.interval-overlap`, `facility.exception-date-taken`, `facility.block-overlap`, `facility.block-invalid-period`. Os de versión (`412`, `428`) xa existen.

---

## 3. Tarefas

Tamaño: **S** ≤ ½ día · **M** ≈ 1 día. Non queda ningunha L. «Carril» no §3.1. Rama: `feat/d1-<id>-<nome>` desde `main` actualizado.

### 3.1 Docs e infra (carril D)

| ID | Rama | Que fai | Ficheiros | Criterios de aceptación | Tam. | Depende | Rol |
|---|---|---|---|---|---|---|---|
| **D1-01a** | `feat/d1-01a-adr-dominio` | ADR-0022 (un módulo `facility`, SPI única, borrador calculado, orde de bloqueo, que garante a base e que o bloqueo, modal en dous pasos, seccións, horas de reloxo) · revisión de ADR-0012 (sen espazos, modelo de D1-02, manter = a reserva queda e o bloqueo non a toca) · ADR-0011 (táboas tipadas, `entity_id` sen FK xustificado) · ADR-0005 (FK cara á raíz **ou a táboas publicadas en lista pechada**; SPI como bean único con implementación por defecto) · ADR-0006 (catálogos de plataforma en `TenancyAllowlist`, `revoke`) | `docs/adr/` | Cada decisión do §1.2 está nunha ADR co seu porqué e a alternativa descartada; sen «espazo folla» vivo salvo como obsoleto; `verificar.sh --parte compliance` | S | — | Arquitecto |
| **D1-01b** | `feat/d1-01b-docs` | ADR-0023 (enderezos e mapa: CartoCiudad polo servidor, teselas por Caddy sen datos do veciño, caché, atribucións, produción con servidor propio) · ADR-0015 (`/tiles`, límite de fotos, variables) · ADR-0018 (formato v2) · ADR-0021 (Leaflet; iconas pendentes, P27) · `arquitectura.md` §3 (sen `calendar`), §8, §9 · `glosario.md` · `deseno.md` §8: nomes do plan (`furar-time-picker`, `furar-calendar` + `furar-date-field`, `furar-edit-section` en vez de `furar-summary-card`, `furar-tabs`, `furar-combobox`, `[furarSegmented]` radio), a semana vive na feature (non `furar-weekly-slots` con `input type="time"`), estado de cada un | `docs/` | Ningún documento contradí o plan; `verificar.sh --parte compliance` | S | — | Arquitecto |
| **D1-06** | `feat/d1-06-caddy` | Caddy: (a) `/tiles` con `path_regexp ^/tiles/(1?[0-9])/[0-9]+/[0-9]+\.png$`, `rewrite` ao camiño do provedor, `header_up Host {upstream_hostport}`, `header_up -X-Forwarded-For -X-Forwarded-Host -Cookie -Authorization -Referer`, `header_down -Set-Cookie`, `User-Agent` de Furar, `Cache-Control` longo, só `GET`/`HEAD`; (b) `handle /api/v1/municipalities/*/facilities/*/photos` con `max_size 9MB`; variables `FURAR_MAP_TILES_UPSTREAM`, `FURAR_MAP_TILES_PATH`, `FURAR_GEOCODER`, `FURAR_HTTP_PROXY` en `compose*.yaml` e `.env.example`; `instalacion.md`: destinos saíntes (www.cartociudad.es, teselas), proxy corporativo (Caddy: comprobar `HTTPS_PROXY` ou `forward_proxy_url`), servidor de teselas en produción, `REFRESH VERSION` das colacións ICU | `deploy/caddy/`, `compose.yaml`, `compose.prod.yaml`, `.env.example`, `docs/instalacion.md` | `curl -I /tiles/14/7905/5878.png` → 200 `image/png`; `/tiles/20/…` e `POST` → 404/405; un upstream de proba confirma que non chegan `X-Forwarded-For`, `Cookie`, `Authorization` nin `Referer`; 8 MB a `/photos` pasa Caddy e 8 MB a outra ruta de `/api` dá 413; CSP igual (`csp.sh`); `verificar.sh --parte compliance` | M | — | Infra |

### 3.2 API (carril A)

| ID | Rama | Que fai | Ficheiros | Criterios de aceptación | Tam. | Depende | Rol |
|---|---|---|---|---|---|---|---|
| **D1-02** | `feat/d1-02-ocupacion` | Modelo SQL de ocupación só de test: `facility`, `facility_use` (cos `unique` compostos do §2.1), `facility_use_part`, `booking_occupancy (facility_id, use_id, part_id, rental_mode, period, seats, active)` con FK `(municipality_id, facility_id, use_id)`, `(municipality_id, use_id, part_id)`, `(municipality_id, use_id, rental_mode)`; `check ((rental_mode = 'PARTS') = (part_id is not null))`; exclusión `(facility_id =, use_id <>, period &&) where active`; enteira `(use_id =, period &&) where active and rental_mode = 'WHOLE'`; por parte; contador `use_slot` con `check (taken <= capacity)`. **Sen bloqueos** (§1.2.5) | `api/src/test/resources/db/occupancy-model/`, `OccupancyModelIT`, `definicion-de-feito.md` (D19, mesma fila) | `CyclicBarrier` como `furar_app` con RLS, repeticións por `-Dfurar.occupancy.reps` (20 en local, 3 na CI): dous usos distintos na mesma franxa → 1; enteira → 1; partes: 2 partes → 2 e a 3.ª falla; prazas 30 con 50 fíos → 30; ocupación co uso doutra instalación → 23503; modo que non casa co uso → 23503; sen interbloqueo en multifranxa (5 s); RLS entre concellos; confírmase que `uuid WITH <>` funciona con `btree_gist` en PG 18 (se non, discriminante enteiro) | M | — | Backend PostgreSQL |
| **D1-10a** | `feat/d1-10a-campos-tipados` | Táboas tipadas (§2.1); migración que copia definicións e valores de `MUNICIPALITY`, compara recontos nun `DO` e borra o JSONB; `db/demo/R__demo_municipalities.sql` ao novo modelo; dominio con `usages`; `CustomFieldValueStore`; `JsonSchemaGenerator` e validador; `municipality` usa o almacén; `x-furar-personalData` e exportación RGPD sobre as táboas; `datos-persoais.md`; D18 coa mesma fila | `gal.furar.customfields`, `gal.furar.municipality`, `shared.domain`, migración, `db/demo/`, `openapi.json`, `docs/datos-persoais.md`, DoD D18 | `api_ut` do validador e `JsonSchemaEquivalenceTest`; IT: os 7 tipos gardan e volven; a base rexeita tipo errado, opción doutro campo, opción marcada doutro campo, `PUBLIC` con dato persoal, cambio de tipo con valores; `SchemaTenancyIT` en verde coas FK novas; base baleira con perfil `demo` arrinca; migración probada cunha base co esquema anterior e valores dos 7 tipos; `rapido.sh --tenancy api` | M | — | Backend |
| **D1-10e** | `feat/d1-10e-export-v2` | `furar-export` `version: 2` para `customfields` (`usages[]`); o importador le v1 e v2 | `MunicipalityDataSection` de `customfields`, ADR-0018 xa revisada | `MunicipalityPortabilityIT` cun ficheiro v1 gardado e cun v2 de ida e volta | S | D1-10a, D1-01b | Backend |
| **D1-11a** | `feat/d1-11a-instalacion` | `scripts/novo-modulo.sh facility Facility facilities instalacions` e o núcleo: táboa `facility`; alta, lista (con `version` e `missing`), ficha, `name`, `status` co fluxo 409/`decisions[]`; `FacilityLock`/`lockFacility` (`for no key update`); `BookingImpact` + `NoBookingImpact`; `missing` por lote; `FacilityQueries` mínima; permisos, erros, auditoría; `ModularityTest` | `gal.furar.facility`, migración, `messages/facility_*`, `openapi.json`, `ModularityTest` | Dominio puro con tests (invariantes, `missing`); IT de cada endpoint con `If-Match` (412, 428); orde gl/es (`Á`, `Ñ`); nome repetido «Pabellón»/«PABELLÓN» → 409; `FacilityTenantIT`; `EndpointSecurityIT` cobre as rutas novas; `@ApplicationModuleTest(STANDALONE)` arrinca só con `NoBookingImpact`; estado cun dobre que devolve afectadas → 409 con `affected[]`, con `decisions[]` → 200, `channels` baleiro en `CANCEL` → 422; IT de que o cambio de estado toma o bloqueo (segunda transacción agarda) | M | D1-01a | Backend |
| **D1-11b** | `feat/d1-11b-datos` | Seccións `details` (sen campos propios), `description`, `address`, `amenities`; `sport_catalog` (semente + `revoke`), `custom_sport`, `facility_sport`; `GET/POST /sports` | `gal.furar.facility`, migración, `TenancyAllowlist`, `openapi.json` | IT de cada sección con `If-Match`; `furar_app` non pode escribir en `sport_catalog` (IT); deporte repetido → 409 polos índices parciais; `check` reserva por internet/ocos; `missing` sen `SPORTS` ao engadir un | M | D1-11a | Backend |
| **D1-11c** | `feat/d1-11c-campos-instalacion` | Destino `FACILITY` e `customFields` en `details` e na ficha, con proxección por visor | `gal.furar.facility` | `FacilityCustomFieldsIT`: valida, garda, proxecta; gardar só campos sobe a versión | S | D1-10a, D1-11b | Backend |
| **D1-12** | `feat/d1-12-usos` | Usos, partes e períodos (§2.1), alta/edición/arquivo baixo `lockFacility`; `blocks_others` → `BookingImpact.ExclusiveUse` e sen solape con outro uso que bloquea; `UseChanged`/`UseArchived` → 409 `facility.use-has-bookings` se hai reservas futuras (P1); `missing` sen `USES` cun uso activo | `gal.furar.facility`, migración, `openapi.json` | Tests de dominio: cada modo, ≥ 2 partes, prazas, datas, «bloquea» só con datas; IT: solape de períodos, nome repetido, arquivo conserva filas, `If-Match` por uso, **intercambio de nomes de dúas partes nun gardado**, erro diferido no commit traducido ao código correcto, dous usos que bloquean solapados → 409 | M | D1-11a | Backend |
| **D1-13** | `feat/d1-13-fotos` | Subida multipart (só en `adapter.in.web`, A11) con `spring.servlet.multipart.*`; dimensións con `ImageReader` antes de decodificar; orientación EXIF aplicada e EXIF fóra; semáforo de 2; reencodado e miniatura; orde, portada (`flush`), alt gl/es, borrado, servizo dos bytes; `storage external` | `gal.furar.facility`, migración, `application.yaml` | IT: JPEG con GPS sae sen EXIF; JPEG con `Orientation=6` sae dereito; PNG; 9 MB → 413 `facility.photo-too-large` (de Spring, non de Caddy); PNG de 20000×20000 → 422 `facility.photo-too-many-pixels` sen pasar de 100 MB de heap; GIF → 415; foto 13 → 422; **cambio de portada nun gardado**; `Cache-Control`; outro concello → 404. Sen dependencia nova salvo que ImageIO non poida; entón TwelveMonkeys (BSD-3). **2026-10-26:** a galería pasa a biblioteca do concello con debuxos de Furar e debuxo suxerido ([ADR-0024](../adr/0024-biblioteca-de-imaxes.md), `FacilityImageIT`) | M | D1-11a | Backend |
| **D1-14** | `feat/d1-14-enderezos` | `AddressSearch` + CartoCiudad (§1.2.10) + `fixed`; endpoints de busca; `FURAR_GEOCODER` e `FURAR_HTTP_PROXY` en `FurarProperties` | `gal.furar.facility`, `FurarProperties`, `application*.yaml` | `MockRestServiceServer` con respostas gardadas en `src/test/resources`; timeout → 503; caché: segunda consulta igual non sae; < 3 caracteres non sae; proxy aplicado ao `RestClient`; ningún test sae á rede; comprobado a man que a cadea TLS de cartociudad.es valida co `cacerts` da imaxe `api` | M | D1-11a, D1-06 | Backend |
| **D1-15a** | `feat/d1-15a-tempadas` | `facility.schedule`: tempadas (`copyFrom`), semana coa fila 8, `public_holiday` (Galicia, 2026 e 2027 se publicado, con fonte e `revoke`), `SCHEDULE` en `missing` (P8), todo baixo `lockFacility` | `gal.furar.facility` (`…schedule`), migración, `TenancyAllowlist`, `openapi.json` | Dominio: tramos (fin > inicio, ≤ 1440, sen solapes, múltiplos de 15); IT: solape de tempadas → 409 coa tempada que choca; **substituír a semana enteira nun gardado**; `furar_app` non escribe en `public_holiday`; test que compara 2026 coa resolución do BOE (columna Galicia) e o DOG; tenancy | M | D1-11a | Backend |
| **D1-15b** | `feat/d1-15b-excepcions` | Excepcións; `gaps[]`, `today`, `now`; `openingIntervals` e `exclusiveUseOn` en `FacilityQueries` | `gal.furar.facility` (`…schedule`), migración | Dominio: prioridade excepción > festivo > semana; IT: data repetida → 409; `openingIntervals` nos cambios de hora (29 mar e 25 out); `gaps[]` cunha tempada que remata antes de 365 días | S | D1-15a | Backend |
| **D1-16** | `feat/d1-16-bloqueos` | Bloqueos (`date` + minutos → `tstzrange` en Europe/Madrid), solape entre bloqueos, `BookingImpact.Block` (novo e cambio de período) co fluxo 409/`decisions[]` baixo `lockFacility`; `openingIntervals` resta bloqueos | `gal.furar.facility` (`…schedule`), migración | IT: solape → 409; período inválido → 422; bloqueo o 25 out ás 2:00–3:00 gárdase ben; dobre de `BookingImpact` → 409 con `affected[]` e despois 201; auditoría | S | D1-15b | Backend |
| **D1-17** | `feat/d1-17-demo` | Datos de demo de Narón e Ferrol (Campo de fútbol 1 de O Alto, pádel 1 e 2, Pabellón de Piñeiros, Pista de atletismo) con usos, tempadas, excepcións e un bloqueo; poucas fotos CC0 ≤ 100 KB cargadas desde o classpath co perfil `demo` (non hex no SQL) | `db/demo/R__demo_facilities.sql`, recursos `demo/`, `REUSE.toml` | Arrinca con `dev,demo` nunha base baleira; ningunha instalación da demo en borrador salvo unha a propósito; licenzas en `REUSE.toml` | S | D1-12, D1-13, D1-16 | Backend |
| **D1-18** | `feat/d1-18-portabilidade` | Sección `facility` en `furar-export` e importación, **sen bytes de fotos** (P32) | `MunicipalityDataSection` de `facility` | `MunicipalityPortabilityIT` exporta e importa noutro concello sen perda dos datos | S | D1-17, D1-10e | Backend |
| **D1-10b** | `feat/d1-10b-plantilla` | Modo `--custom-fields` da plantilla co almacén tipado (ou quitalo e a súa axuda), o máis pequeno que deixe D45 en verde | `templates/module/`, `scripts/novo-modulo.sh` | `scripts/probar-plantilla.sh` (D45); `verificar.sh --parte compliance` (D54) | S | D1-10a | Backend ferramentas |

### 3.3 Web (carril W)

| ID | Rama | Que fai | Ficheiros | Criterios de aceptación | Tam. | Depende | Rol |
|---|---|---|---|---|---|---|---|
| **D1-03** | `feat/d1-03-selector-hora` | `furar-time-picker`: botón coa hora en texto que abre en CDK Overlay un `cdkListbox` cada 15 min co foco dentro, desprazado ata a actual; valor en **minutos enteiros** (0–1440); `min`, `max`, `allowEndOfDay` (ofrece 24:00); `ControlValueAccessor`; nome accesible con contexto por `input` (`«Luns, tramo 1, desde, 8:00»`) | `shared/ui/time-picker/`, `styles/components/time-picker.css`, `dev/catalog`, `ui.*` | Spec con teclado (frechas, Inicio/Fin, Intro, Esc), o foco volve ao botón, `aria-expanded`; desprazamento probado cun stub de `scrollIntoView` e `OverlayContainer`; funciona con `formControlName`; axe no catálogo; 44 px; `prefers-reduced-motion` | M | — | Frontend compoñentes |
| **D1-04** | `feat/d1-04-calendario` | `furar-calendar` (grella APG: frechas, Re Páx/Av Páx mes, Maiús+Re Páx ano, Inicio/Fin; hoxe con aro, escollido recheo `primary`, luns primeiro) e `furar-date-field` (texto da data + panel en Overlay; `min`/`max`, días desactivados). **Só data simple**: rangos = dous campos (P14) | `shared/ui/{calendar,date-field}/`, CSS, catálogo, `ui.*` | Spec de teclado; mes anunciado, día con data completa; mes e días por `Intl` co idioma activo e cambio de idioma en quente; formato «24/12/2026» en gl e es; valor ISO `LocalDate`; axe; 320 px | M | — | Frontend compoñentes |
| **D1-05a** | `feat/d1-05a-dialogo-menu` | `furar-dialog` e `furar-confirm` (CDK Dialog), `[furarMenu]` (CDK Menu, `menuitemradio`), `furar-switch` (`role="switch"`), chip de estado **sobre `furar-tag`** con tono novo para `WITHDRAWN` en `tag.css` e nome «Estado: Activa. Cambiar» | `shared/ui/{dialog,menu,switch}/`, `tag.css`, catálogo | Specs de teclado e foco; o estado non vai só por cor; `tag-color.spec.ts` pasa co tono novo; axe; temas límite e escuro | M | — | Frontend compoñentes |
| **D1-05b** | `feat/d1-05b-seccion-editable` | `furar-edit-section`: `<section>` con `<form>` tipado, liña de «cambios sen gardar», «Cancelar»/«Gardar», erros do servidor no campo, «Gardado» por `role="status"`; **non decide o control** (pono a feature). Contrato de foco: valor en repouso como `<button>` con etiqueta e valor; ao activalo o foco vai ao campo; Esc devolve o foco ao botón; tras gardar o foco volve ao título da sección. Garda `CanDeactivate` e `beforeunload` | `shared/ui/edit-section/`, `core/` (garda), catálogo | Spec: foco ao primeiro erro; Cancelar restaura; Esc; garda con diálogo; `beforeunload` só con sección sucia; axe | M | D1-05a | Frontend compoñentes |
| **D1-05c** | `feat/d1-05c-segmentado-pestanas` | `[furarSegmented]` modo radio (`role="radiogroup"`, frechas, CVA; o modo `aria-pressed` actual segue) e `furar-tabs` (`tablist`/`tab`/`aria-selected`, frechas; accións fóra do `tablist`) | `shared/ui/{segmented,tabs}/`, CSS, catálogo | Specs de teclado; a 320 px o segmentado baixa de liña; o selector de idioma segue igual; axe | S | — | Frontend compoñentes |
| **D1-05d** | `feat/d1-05d-combobox` | `furar-combobox` con suxestións asíncronas (patrón APG, `aria-activedescendant` no `input`, espera de 300 ms, estado «buscando» e «sen resultados», opción final «+ crear» configurable) e modo `skeleton` de `furar-status-state` | `shared/ui/{combobox,status-state}/`, CSS, catálogo | Spec con teclado e con fonte lenta e con erro; lector anuncia número de suxestións; axe | M | — | Frontend compoñentes |
| **D1-07** | `feat/d1-07-mapa` | `leaflet@1.9.4` (+ `@types`), aviso en `3rdpartylicenses`; `furar-map`: só lectura (`keyboard:false`, non é parada de tabulador; o texto do enderezo é a alternativa) e editable (chincheta arrastrable, picar para mover, frechas coa chincheta enfocada e campos lat/lon, WCAG 2.5.7); zoom con tokens e 44 px; atribución visible con contraste AA; CSS cargado polo compoñente | `web/package.json`, `angular.json`, `shared/ui/map/`, catálogo | Sen `securitypolicyviolation`; axe; teclado move a chincheta; orzamento inicial intacto; o catálogo e os specs non piden teselas reais (`page.route`) | M | D1-06 | Frontend compoñentes |
| **D1-19** | `feat/d1-19-modal-afectadas` | `features/facility/affected-bookings/`: diálogo como `ul[furarRowList]` cun `fieldset` por reserva (lenda = nome + «Lun 3 nov · 8:00–9:30 · Fútbol 11»), cancelar/manter (segmentado radio), canles como casillas (só con cancelar; teléfono á vista), «Seleccionar todas» e «Cancelar/Manter as marcadas»; helper 409 → abrir → reenviar con `decisions[]` | `features/facility/affected-bookings/`, i18n | Specs cun `affected[]` de proba; «Confirmar» só con decisión en todas e canle en cada cancelada (P18); foco e Esc; 320 px sen scroll horizontal; axe | S | D1-05a, D1-05c, D1-11a | Frontend features |
| **D1-10c** | `feat/d1-10c-campos-web` | Editor de Axustes: «onde se usa» con obrigatorio e visibilidade por sitio; fóra orde manual e grupos; lista alfabética | `shared/custom-fields/`, `features/customfields/`, i18n | Specs; `campos-personalizados.e2e.ts` en gl/es/móbil con axe | M | D1-10a | Frontend features |
| **D1-20** | `feat/d1-20-listado` | `features/facility`: rutas, `facility.store.ts`, listado en tarxetas (nome, enderezo, chip con menú de estado que usa a `version` da tarxeta e D1-19 se 409; «De baixa» pide confirmación, P17), estados baleiro (con «Nova instalación»), cargando (esqueleto) e erro; «Nova instalación» (diálogo: nome + `furar-combobox` de enderezo, a man se 503); entrada no menú | `features/facility/list/`, `core/layout/shell`, `app.routes.ts`, i18n | Specs; tarxeta enfocable enteira e chip como botón aparte; orde do servidor; 320 px; axe | M | D1-05a, D1-05d, D1-11a, D1-14, D1-19 | Frontend features |
| **D1-21a** | `feat/d1-21a-ficha` | Esqueleto da ficha e **contrato de estado**: o store garda a instalación e a súa `version` nun signal; cada sección rellena o seu `FormGroup` só se non está sucia; os gardados que comparten versión da instalación van en cola; `If-Match` sempre da última versión; usos, tempadas, excepcións e bloqueos coa súa versión por elemento. «← Volver», cabeceira con estado, aviso de borrador (`furar-callout` warn sen `role="alert"`, ligazóns que levan o foco ao `h2` da sección, `id` estables), grella de dúas columnas con **Datos primeiro no DOM**, Descrición gl/es, estados de carga e erro | `features/facility/detail/`, `features/facility/description/` | Specs do store (cola, 412 sen perder o escrito); un `h1`, seccións con `h2`; foco ao título ao entrar; orde de foco = orde visual a 320 px e a 1280 px; axe | M | D1-05b, D1-11b, D1-19 | Frontend features |
| **D1-21d** | `feat/d1-21d-tarxeta-datos` | Tarxeta Datos editable no sitio: deportes (`furar-combobox` con «+ crear»), tipo, RIDEGA, contacto, campos propios, reserva por internet / mostrar ocos (`furar-switch`), cuberta; sticky en escritorio (P23) | `features/facility/data-card/` | Specs; validación igual ca o servidor; sen campos propios → nada baleiro á vista; axe | M | D1-21a, D1-11c, D1-05c, D1-05d | Frontend features |
| **D1-21b** | `feat/d1-21b-servizos-localizacion` | Servizos e accesibilidade (vista «o que ofrece» + diálogo con casillas por grupo; baleiro con acción) e Localización (mapa de só lectura + diálogo: `furar-combobox` → campos e chincheta; a man se 503 con `furar-callout` warn; «Fonte: CartoCiudad, © IGN») | `features/facility/{amenities,location}/` | 503 → aviso e campos a man; mapa con alternativa por teclado; axe | M | D1-07, D1-21a | Frontend features |
| **D1-21c** | `feat/d1-21c-fotos` | Galería (unha grande e catro pequenas; móbil unha con «1 de 5»; baleira con «Engadir fotos», P24), «Cambiar fotos» (`[furarButton] secondary` opaco): subir, ordenar con botóns, portada, alt gl/es, quitar con `furar-confirm`. **2026-10-26:** substitúeo o modal a pantalla completa da maqueta aprobada (tira `furar-carousel` que se ordena arrastrando ou con Alt + frechas, biblioteca «Engadir fotos», nada ata «Gardar»; especificación, bloque 1, «Fotos») | `features/facility/photos/` | Erros 413/415/422 xunto ao ficheiro; ordenar só con teclado; `alt` nas `img`; axe | M | D1-13, D1-21a | Frontend features |
| **D1-22** | `feat/d1-22-usos-web` | Sección Usos partida en `use-list`, `use-row`, `use-form`, `use-periods`: filas `ul[furarRowList]` con etiquetas neutras e lapis con nome («Editar Fútbol 11»); edición dentro da lista («Se aluga», duración e cando con segmentado radio, partes, prazas, períodos con dous `furar-date-field`, «bloquea» só con datas); quitar = arquivar con `furar-confirm`; baleiro | `features/facility/uses/` | Specs de cada modo; validación igual ca o servidor; D1-19 se 409; 409 `use-has-bookings` no aviso; axe e teclado | M | D1-04, D1-05c, D1-12, D1-21a | Frontend features |
| **D1-23a** | `feat/d1-23a-horario-web` | Sección Horario partida en `schedule.store.ts`, `season-tabs` (`furar-tabs`, «Agora»), `season-dialog` (+ Tempada: nome, desde, ata, «empezar co horario de»), `week-editor` (`FormArray` de 8 filas), `day-row` (vista resolta como botón e edición: interruptor, tramos con `furar-time-picker`, ✕, «+ Tramo»). «Pulsar fóra» = clic fóra da fila, non `focusout`, e conserva o pendente; fila con erros queda aberta (P20) | `features/facility/schedule/` | Validación por fila e «Gardar» desactivado; solape do servidor na tempada co aviso de usar unha excepción; «Hoxe» e «Agora» desde `today`/`now` da API; tabular a «+ Tramo» non pecha a fila; axe; 320 px | M | D1-03, D1-04, D1-05b, D1-05c, D1-15a, D1-21a | Frontend features |
| **D1-23b** | `feat/d1-23b-excepcions-web` | `exception-list` (filas, «+ Excepción», borrar con `furar-confirm`), aviso de días sen tempada (`gaps[]`), baleiros | `features/facility/schedule/exceptions/` | Specs; data repetida → erro no campo; axe | S | D1-23a, D1-15b | Frontend features |
| **D1-24** | `feat/d1-24-bloqueos-web` | Sección Bloqueos: filas resoltas, edición ao pulsar (data, desde, ata, motivo), «+ Bloqueo», borrar con `furar-confirm`, D1-19 se 409, baleiro | `features/facility/blocks/` | Specs; solape → erro; axe | S | D1-16, D1-19, D1-21a, D1-03, D1-04 | Frontend features |

### 3.4 Final (carril E)

| ID | Rama | Que fai | Ficheiros | Criterios de aceptación | Tam. | Depende | Rol |
|---|---|---|---|---|---|---|---|
| **D1-25** | `feat/d1-25-e2e` | `web/e2e/instalacions.e2e.ts`: alta → borrador → usos, deportes e horario → deixa de ser borrador; editar Datos; tempada solapada; excepción; bloqueo; foto; enderezo. `/tiles` con `page.route` (PNG de proba) e `FURAR_GEOCODER=fixed` explícito no entorno do e2e. Revisión con VoiceOver, só teclado (modal → decisións → confirmar) e zoom 200 % a 1280 px; entrada en `docs/accesibilidade.md` | `web/e2e/`, `.github/workflows/ci.yml` (só env), `docs/accesibilidade.md` | `gl`, `es`, `gl-mobile` en verde con axe en cada paso e dous temas; sen `securitypolicyviolation`; ningunha petición fóra de localhost; `verificar.sh --estrito` en verde en `main` | M | todas | QA accesibilidade |

**Total: 37 tarefas** (3 D, 15 A, 18 W, 1 E). Ningunha L.

### 3.5 Carrís e paralelismo (máquina de 8 GB)

- **Carril A (API):** ata **dúas** tarefas á vez, **unha soa** con Docker/Testcontainers: antes de `api_it`, `verificar.sh` ou `rapido.sh --tenancy`, `mkdir /tmp/furar-it.lock && echo $$ > /tmp/furar-it.lock/pid` con `trap 'rm -rf /tmp/furar-it.lock' EXIT`; se existe e o PID xa non vive, bórrase. `rapido.sh api` (sen Docker), libre.
- **Carril W (web):** ata dúas tarefas; nunca dous `ng build` nin Playwright á vez (mesmo lock).
- **Carril D:** sen límite; non compila.
- **Carril E:** ao final, só.
- **Ficheiros quentes:** `api/openapi.json`, `web/src/i18n/{gl,es}.json`, `app.routes.ts`, `migrations.lock`, `RolePermissions`, `deploy/caddy/Caddyfile` (só D1-06). Antes do merge: rebase sobre `main`, **rexenerar** `openapi.json` e o cliente TS (nunca resolver a man), regra de rebase de migracións (§2.1), claves i18n só baixo o propio prefixo (`facility.<seccion>.*`, `ui.<compoñente>.*`).
- **En paralelo desde o primeiro minuto:** D1-01a, D1-01b, D1-06 (D) · D1-02, D1-10a, D1-11a (A; D1-11a arrinca cando D1-01a estea en revisión) · D1-03, D1-04, D1-05a, D1-05c, D1-05d (W, dous a dous). Despois de D1-11a: D1-11b, D1-12, D1-13, D1-14 e D1-15a; D1-15b → D1-16 en serie. Web: D1-20 cando estean D1-05d, D1-14 e D1-19; despois de D1-21a, as seccións (D1-21b, D1-21c, D1-21d, D1-22, D1-23a, D1-24) van en subcartafoles distintos e poden ir dúas a dúas.

### 3.6 Calendario

| Día | Carril A | Carril W | D/E |
|---|---|---|---|
| Dom 25 – Lun 26 | D1-02 · D1-10a · D1-11a | D1-03 · D1-04 · D1-05a · D1-05c | D1-01a · D1-01b · D1-06 |
| Mar 27 | D1-11b · D1-12 · D1-14 | D1-05b · D1-05d · D1-19 | |
| Mér 28 | D1-15a · D1-13 · D1-11c | D1-07 · D1-20 | |
| Xov 29 | D1-15b · D1-16 · D1-10e | D1-21a · D1-22 | |
| Ven 30 | D1-17 · D1-10b | D1-23a · D1-21d · D1-24 | |
| Sáb 31 | D1-18 | D1-21b · D1-23b | D1-25 · `verificar.sh --estrito` |

**Poden escorregar ao luns 2 sen bloquear reservas:** D1-10c, D1-21c, D1-23b, D1-18, D1-10b. O que precisa o dominio 2 vai primeiro: D1-02, D1-11a, D1-12, D1-15a/b, D1-16, D1-19.

---

## 4. Perfís e regras comúns

Cada tarefa (**D1-xx**) faise co perfil da súa fila e estas regras comúns:

> Le antes `CONTRIBUTING.md`, `docs/arquitectura.md`, `docs/definicion-de-feito.md` (filas da túa capa), §1, §2 e a túa fila deste plan. Traballa na rama da túa fila desde `main` actualizado. **Non fagas commit, push nin merge** sen permiso expreso do mantedor. Código e claves en inglés; documentación e comentarios en galego; cabeceira SPDX en cada ficheiro novo. Dependencia nova: só con licenza de `scripts/allowed-licenses.txt`, versión exacta e ≥ 14 días. Non desactives tests nin regras; se unha regra estorba, para e propón. **Para e pregunta** (lista curta, cunha recomendación por pregunta) se: falta unha regra de produto ou é ambigua (nunca a inventes; se está no §6, constrúe o valor por defecto e dío), a tarefa obriga a tocar un ficheiro doutra tarefa en curso, unha garda (A1–A22, M1–M3, D28) falla e non ves como cumprila, ou a estimación pasa do dobre. Ao rematar devolve: ficheiros tocados, comandos de verificación co resultado e o que queda aberto.

### 4.1 Arquitecto (D1-01a, D1-01b)
> Perfil: arquitecto de software con experiencia en sector público, monolitos modulares e ADR. Escribes en galego claro e curto. Le as ADR 0001, 0005, 0006, 0010, 0011, 0012, 0014, 0015, 0016, 0018, 0021, `docs/deseno.md` §8 e a especificación (bloques 1, 1b, 2). Non toques código nin `definicion-de-feito.md`. Unha decisión que cambia revísase no seu ficheiro (estado, data, que cambia e por que), sen renumerar. Cada decisión do §1.2 leva alternativa descartada (as do §7 serven). Verifica: `scripts/verificar.sh --parte compliance`. Para se unha decisión do §1.2 contradí a especificación.

### 4.2 Backend PostgreSQL (D1-02)
> Perfil: backend sénior Java 25 e PostgreSQL 18 (exclusións, `btree_gist`, illamento, bloqueos de fila, RLS). Le ADR-0006, ADR-0012 e ADR-0022 (revisadas en D1-01a se xa están), `OccupancyModelIT` e o seu SQL. Só tocas o modelo de test, o seu IT e a fila D19 (mesma fila): **nada de migracións de produción**. Os bloqueos non van no modelo (§1.2.5). Verifica: `api_it 'OccupancyModelIT' -Dfurar.occupancy.reps=20` sen fallos intermitentes, como `furar_app`. Para se a regra «un uso por instalación e franxa» non se pode expresar cunha restrición declarativa: describe a alternativa e o seu custo antes de escribila.

### 4.3 Backend (D1-10a, D1-10e, D1-11a, D1-11b, D1-11c, D1-12, D1-13, D1-14, D1-15a, D1-15b, D1-16, D1-17, D1-18)
> Perfil: backend sénior Java 25 + Spring Boot 4.1 + Spring Modulith 2.1 + hexagonal + PostgreSQL 18 + Flyway; dominio rico sen Spring nin JPA; tests primeiro no dominio. Le ADR-0005, 0006, 0007, 0008, 0009, 0010, 0013, 0022, `docs/como-engadir-un-modulo.md` e `municipality` como referencia. Convencións: paquetes `domain`/`application/port/{in,out}`/`application/service`/`adapter/{in/web,out/persistence}` (o horario no subpaquete `schedule` de cada capa); API pública só na raíz; entidades con `@TenantId`, `@Version`, `Persistable<UUID>`; `TenantScopedPersistenceAdapter`; RLS e política na migración; FK compostas con `municipality_id` nos dous lados; índices únicos con expresións como `create unique index`; `Instant` nos DTO para marcas de tempo, pero horarios e bloqueos como data + minutos (§1.2.8); permiso no caso de uso; erros `enum … implements ErrorCode` con texto gl/es; `@VersionedResource` e `If-Match`; auditoría; toda escritura que cambia a reservabilidade toma `FacilityLock` primeiro (§1.2.4). Migración coa hora real e regra de rebase do §2.1. Non toques `web/` salvo rexenerar o cliente nin módulos alleos salvo o indicado. Verifica, por esta orde: `scripts/rapido.sh api`; os teus `api_ut`/`api_it` (co lock); `rapido.sh --tenancy api` se creas táboas; `OpenApiContractIT -Dfurar.openapi.write=true` e `(cd web && npm run api:generate && npx ng build --configuration development)`. Para se: un campo, estado ou regra non está no §2 nin na especificación; necesitas ler táboas doutro módulo; a migración tería que editar unha aplicada.
>
> Notas: **D1-10a** proba a migración cunha base co esquema anterior e mantén `customFields: {code: valor}` nos DTO. **D1-11a** deixa `BookingImpact` cun test con dobres e proba o bloqueo con dúas transaccións. **D1-13** proba a memoria coa imaxe xigante antes de calquera outro caso. **D1-14** non fai chamadas reais nos tests. **D1-15a** non inventa festivos: só os publicados no BOE/DOG, coa referencia. **D1-17/D1-18** non cambian o formato de `furar-export` máis alá do que di ADR-0018.

### 4.4 Backend ferramentas (D1-10b)
> Perfil: backend sénior con Bash (shellcheck) e xeración de código. Le `templates/module/README.md`, `scripts/novo-modulo.sh`, `scripts/probar-plantilla.sh` e ADR-0011 revisada. Só tocas `templates/` e `scripts/novo-modulo.sh`. Escolle o cambio máis pequeno. Verifica: `scripts/probar-plantilla.sh` e `scripts/verificar.sh --parte compliance`. Para se a plantilla precisa API de `customfields` que non existe.

### 4.5 Infra (D1-06)
> Perfil: infra sénior Caddy 2 + Docker Compose + seguridade (CSP, cabeceiras, non root, privacidade). Le ADR-0007, ADR-0015, ADR-0023, `deploy/caddy/`, `compose*.yaml`, `docs/instalacion.md`. Non toques a CSP nin as imaxes base. Proba as cabeceiras que saen cara ao provedor cun upstream local que as devolva. Verifica: perfil `full` levantado en serie, `curl` dunha tesela e dunha subida de 8 MB, `verificar.sh --parte compliance`. Para se o provedor por defecto obriga a algo que choque con «nada a terceiros».

### 4.6 Frontend compoñentes (D1-03, D1-04, D1-05a, D1-05b, D1-05c, D1-05d, D1-07)
> Perfil: frontend sénior Angular 22 zoneless + signals + Angular CDK (Overlay, A11y, Dialog, Menu, Listbox) + Tailwind 4, especialista en WCAG 2.1 AA e APG. Le ADR-0014, ADR-0021, `docs/deseno.md` (enteiro; se choca co §1.2 ou co §3.3, manda o plan), `docs/accesibilidade.md` e `shared/ui`. Compoñentes **sen dominio**, OnPush, `input()`/`output()`, `ControlValueAccessor` compatible con Reactive Forms tipados, CSS global en `src/styles/components/<nome>.css` dentro de `@layer components`, só tokens `--f-*`, textos `ui.*` en gl e es (nomes de días e meses por `Intl` co idioma de `LanguageService`), entrada no catálogo `dev/`. Sen Angular Material nin outra librería de compoñentes. Non toques `features/`. Verifica: `scripts/rapido.sh web`, spec con teclado e axe, catálogo en gl e es, 320 px. Para se un patrón ARIA non encaixa co deseño acordado.

### 4.7 Frontend features (D1-10c, D1-19, D1-20, D1-21a, D1-21b, D1-21c, D1-21d, D1-22, D1-23a, D1-23b, D1-24)
> Perfil: frontend sénior Angular 22 zoneless + signals + Tailwind 4, con formularios accesibles e stores. Le ADR-0014, ADR-0021, `docs/deseno.md`, a especificación (bloques 1, 1b, 2), as maquetas `xestion-instalacions/01-listado.html` e `02-ficha-instalacion.html` (sen complexo) e `shared/ui`. Reactive Forms tipados; o cliente xerado só desde `*.store.ts`; ningún HTTP á man; unha feature non importa outra. Respecta o contrato de estado de D1-21a. Compón `shared/ui`; se falta un xenérico, para e pídeo. Cada sección no seu subcartafol e por baixo dos límites (250 `.ts`, 200 `.html`), coa partición que di a túa fila. Textos en `web/src/i18n/{gl,es}.json` baixo `facility.<seccion>.*`. Cada sección ten estado baleiro, cargando e erro. Datas sempre co formato do idioma («24/12/2026»). Erros da API no campo (`errors[]`) ou no aviso da sección. Verifica: `scripts/rapido.sh web`, specs, `npm run i18n:check`, e a pantalla en gl, es e 320 px con axe. Para se a maqueta e a especificación din cousas distintas ou se precisas un dato que a API non dá.

### 4.8 QA accesibilidade (D1-25)
> Perfil: QA sénior Playwright + axe-core + VoiceOver e só teclado; WCAG 2.1 AA e EN 301 549. Le ADR-0013, ADR-0014, `docs/accesibilidade.md`, `web/e2e/fixtures.ts` e os e2e existentes. Non cambias código de produción: se atopas un fallo, describe paso, criterio WCAG e ficheiro, e para. Ningunha petición a terceiros (teselas con `page.route`, `FURAR_GEOCODER=fixed`). Verifica: `npx playwright test --project=gl --project=es --project=gl-mobile` co perfil `full` en serie e `scripts/verificar.sh --estrito`.

---

## 5. Riscos, integración e dominio 2

### 5.1 Riscos

| Risco | Mitigación |
|---|---|
| A semana non chega (37 tarefas pequenas) | Orde do §3.6; lista do que pode escorregar; ningunha tarefa L |
| 8 GB con varias tarefas á vez | Carrís e lock con PID do §3.5; imaxes en serie |
| Conflitos en `openapi.json`, i18n e `migrations.lock` | Merges en serie (§5.2), rexenerar, regra de rebase das migracións |
| Restricións por sentenza e orde de Hibernate | `flush` explícito e `deferrable` (§1.2.15) cun IT por caso |
| Carreiras entre bloqueo/estado/horario e reservas | Orde de bloqueo única desde o dominio 1 (§1.2.4) e IT de dúas transaccións |
| Fotos que tumban a API | Megapíxeles antes de decodificar, semáforo, límites en Caddy e Spring |
| CartoCiudad caído, lento ou sen saída a internet | Caché, timeouts, 503 → a man; proxy configurable; `fixed` como respaldo |
| Teselas: política de OSM e privacidade | Cabeceiras limpas, caché no navegador, atribución; produción con servidor propio |
| CI dependente da rede | `page.route` para `/tiles` e `FURAR_GEOCODER=fixed` |
| Migración do JSONB con datos | `DO` con recontos, IT desde o esquema anterior, demo nova |
| Edición no sitio pouco accesible | Contrato de foco único en `furar-edit-section`; QA manual en D1-25 |
| Decisións de produto abertas (§6) | Valor por defecto reversible en cada tarefa |

### 5.2 Orde de merge a `main`

Un merge cada vez, despois de `rapido.sh` e as probas da fila; `verificar.sh --estrito` cada noite en `main` e obrigatorio antes do último merge da semana.

1. D1-01a, D1-01b, D1-06 → 2. D1-02 → 3. D1-03, D1-04, D1-05a, D1-05c → 4. D1-10a → 5. D1-11a → 6. D1-05b, D1-05d → 7. D1-11b, D1-12 → 8. D1-14, D1-19 → 9. D1-13, D1-15a, D1-11c → 10. D1-07, D1-20 → 11. D1-15b, D1-16, D1-10e → 12. D1-21a, D1-22 → 13. D1-23a, D1-21d, D1-24 → 14. D1-17 → 15. D1-21b, D1-23b, D1-21c, D1-10c → 16. D1-18, D1-10b → 17. D1-25. Dentro de cada grupo, por orde de chegada.

### 5.3 Para o dominio 2 (1–7 nov)

- **Ocupación de produción** en `booking` co modelo de D1-02 e as FK de §2.1; entradas en `CrossModuleForeignKeys`: `booking → facility_use`, `booking → facility_use_part` (ADR-0005 revisada).
- Ao reservar: `lockFacility` → estado, `openingIntervals`, bloqueos, `exclusiveUseOn` → ocupación.
- `booking` implementa `BookingImpact` (o modal deixa de ver lista baleira), garda decisión, quen e cando, e publica o evento de aviso despois do commit.
- Vista da veciñanza con `visibleIds(page)`; maquetas vecino 01 A, 02 A, 03 B, 04 A, 05 A, 06 B. Fotos públicas: revisar entón se se limitan ás instalacións visibles.
- Normas e tarifas por uso; permisos do conserxe.

---

## 6. Preguntas de produto

Unha lista, sen repetidas. Cada unha leva a miña recomendación, que é o que se constrúe mentres non respondas.

**Usos**
1. **Cambiar un uso que xa ten reservas futuras** (de enteira a partes, a duración, baixar prazas por debaixo do reservado, quitar unha parte): ¿déixase ou non? *Recomendo: non se deixa; sae «Este uso ten reservas futuras» e o xestor pode crear un uso novo. Nome e datas si se poden cambiar.*
2. **Dous usos que «bloquean os demais»** na mesma instalación con datas que se pisan: ¿pode ser? *Recomendo: non; aviso «Xa hai outro uso que bloquea nesas datas».*
3. **Prazas e partes:** ¿mínimo e máximo de prazas? Nun uso por partes, ¿pódese reservar máis dunha parte á vez? *Recomendo: de 2 a 10 000 prazas; unha parte por reserva (para o campo enteiro, un uso «enteira» aparte, como no exemplo do Fútbol 11).*
4. **«Cada reserva dura»** ten catro opcións: ¿botóns (como a maqueta) ou despregable (a regra di botóns ata tres)? *Recomendo: botóns, son curtas e caben no móbil.*
5. **«Só unhas datas»:** ¿rangos de datas (1–15 xullo; un día = rango dun día) ou días soltos? *Recomendo: rangos.*

**Horario e bloqueos**
6. **Cambiar ou borrar unha tempada, un tramo ou unha excepción** que deixa fóra reservas futuras: ¿abre o modal de reservas afectadas como un bloqueo? *Recomendo: si, o mesmo modal (un só comportamento para todo).*
7. **Dous bloqueos que se pisan** na mesma instalación: ¿pode ser? *Recomendo: non; aviso «Xa hai un bloqueo nesas horas».*
8. **«Ter horario» para saír de borrador:** ¿calquera tempada con algún tramo, ou unha que cubra hoxe ou o futuro? *Recomendo: unha tempada con algún tramo que remate hoxe ou despois.*
9. **Excepcións:** ¿un tramo por día ou varios, como na semana? *Recomendo: un tramo (o que di a especificación); varios en F2.*
10. **Festivos locais:** ¿meterse como excepción en cada instalación ou unha vez para todo o concello? *Recomendo: en cada instalación agora; a lista do concello, F2.*
11. **Calendario de 2027** se aínda non está publicado no BOE/DOG: ¿sementamos só 2026? *Recomendo: si; 2027 nunha versión posterior.*
12. **Pechar ás 24:00:** ¿o selector ofrece «24:00» como hora de fin, ou o último fin é 23:45? *Recomendo: ofrecer 24:00.*
13. **Bloqueos:** ¿só nun día con horas, ou tamén varios días seguidos e «todo o día»? *Recomendo: un día, desde–ata (todo o día = 0:00–24:00).*
14. **Datas de tempadas e usos:** ¿calendario de rango nun só panel ou dous campos desde/ata? *Recomendo: dous campos; o panel de rango, F2.*

**Estado e borrador**
15. **Instalación publicada que se queda sen usos ou sen horario:** ¿volve a borrador e deixa de verse? E no listado, ¿como se ve un borrador? *Recomendo: si, volve a borrador para reservas novas (as que xa ten mantéñense) e avísase ao xestor antes; no listado, o chip de estado e ao lado unha etiqueta «Borrador».*
16. **Pechada temporalmente:** ¿a veciñanza vea (sen poder reservar, cun aviso) ou desaparece como a de baixa? *Recomendo: vese co aviso.*
17. **Cambiar o estado desde o chip** sen reservas afectadas: ¿aplícase directamente? *Recomendo: directo para Activa e Pechada; «De baixa» pide confirmación.*

**Modal de reservas afectadas**
18. **Que vén marcado** en cada reserva e que pide «Confirmar». *Recomendo: nada marcado; ao escoller «Cancelar» márcase Correo (ou Chamar se non ten correo); «Confirmar» pide decisión en todas e polo menos unha canle en cada cancelada.*
19. **¿Debe verse o modal na demo do dominio 1?** Ata as reservas (dominio 2) non hai afectadas reais. *Recomendo: non na demo; só no catálogo de compoñentes; na demo final saen con reservas reais.*

**Edición na ficha**
20. **Fila de horario con erros e pulsar fóra:** ¿pecha ou queda aberta? ¿Varias filas ou seccións abertas á vez? *Recomendo: queda aberta co erro; si a varias á vez, cada sección co seu Gardar.*
21. **Despois de «Gardar»:** ¿chega con volver á vista ou queres un aviso? *Recomendo: un «Gardado» pequeno ao lado do botón uns segundos; sen avisos flotantes.*
22. **Borrar unha tempada, excepción ou bloqueo:** ¿confirmación ou «Desfacer»? *Recomendo: confirmación curta.*
23. **Tarxeta Datos:** ¿fixa ao baixar no escritorio? *Recomendo: si.*
24. **Ficha sen fotos:** ¿oco con «Engadir fotos» ou nada? *Recomendo: para o xestor, oco con «Engadir fotos»; para a veciñanza, nada.*
25. **Fotos:** ¿é obrigatoria unha para saír de borrador? ¿Texto alternativo nos dous idiomas? *Recomendo: foto non obrigatoria; texto alternativo obrigatorio en gl e es.*

**Datos e campos**
26. **Deportes creados con «+ crear»:** ¿nome en gl e es ou nun só texto? *Recomendo: gl e es (a veciñanza busca por eles).*
27. **Iconas** de servizos e deportes: ¿Material Symbols (propostas o 25-10) ou propias? *Recomendo: Material Symbols como SVG empaquetados; responder antes do martes para non refacer as iconas.*
28. **Campos personalizados:** fóra orde manual, grupos e a condición «só para pistas de pádel». ¿Confirmas? *Recomendo: si, F2 se os piden.*
29. **Nome da instalación único** no concello, sen distinguir maiúsculas? *Recomendo: si.*

**Demo e datos**
30. **Demo do xurado:** ¿busca enderezos reais en CartoCiudad (precisa internet no servidor) ou chega cos de Narón e Ferrol gardados? *Recomendo: CartoCiudad real; se cae, alta a man.*
31. **Mapa na demo:** ¿valen as teselas públicas de OSM a través de Furar (pouco tráfico, con atribución) ou fai falta un provedor xa? *Recomendo: OSM na demo; provedor propio documentado para produción.*
32. **`furar-export` coas fotos:** ¿leva as fotos (o ficheiro pesa moito máis) ou só os datos? *Recomendo: só datos agora (as fotos van na copia de seguridade da base); con fotos en F2.*

---

## 7. Revisións

Revisión do 2026-10-25 (backend, infra, arquitectura, frontend, deseño). **Incorporado:** bloqueos fóra da exclusión e sen uso centinela; orde de bloqueo única desde o dominio 1; FK compostas instalación–uso–parte e modo; PK de `facility_sport`; FK de `customfields` con `municipality_id`; Hibernate e restricións por sentenza; `version` no listado; fóra a liña de `btree_gist`; límites de subida en Caddy e Spring; imaxes seguras (megapíxeles, orientación, semáforo); D1-11a, D1-15, D1-21a e D1-23 partidas; SPI como bean único e avisos despois do commit; lista única de festivos de Galicia con fonte e `revoke`; datos persoais en D1-10a; índices únicos como `create unique index`; `visibleIds` por lote; caché e límites de CartoCiudad; teselas sen datos do veciño e con variables separadas; e2e sen rede; `FURAR_GEOCODER` propio; proxy saínte; demo R__ e `DO` de recontos; regra de rebase de migracións; formato v2 de exportación; xustificación de `entity_id`; lista completa de ADR e `ModularityTest`; modal en tarefa propia e cedo; `Intl` no canto de `LOCALE_ID`; `deseno.md` ao día; ARIA do selector de hora; minutos e 24:00; bloqueos con data + minutos e `today` da API; contrato de estado da ficha; xenéricos que faltaban (pestanas, combobox, segmentado radio, esqueleto, confirmación); Reactive Forms tipados; partición de D1-22 e D1-23; `channels[]`; modal como lista de `fieldset`; chip sobre `furar-tag`; Datos primeiro no DOM; mapa accesible e atribucións; contrato de foco; estados baleiro, cargando e erro; aviso de borrador; fila 8 sen `holidays_like_sunday`; `bytea` externo; fotos de demo pequenas; repeticións de `OccupancyModelIT` por propiedade; lock con PID; colacións ICU; test de maiúsculas con acentos; Leaflet CSS diferido; `beforeunload`; specs con stubs; formato de datas; nomes accesibles con contexto.

**Fundir `calendar` en `facility`** (suxestión de arquitectura): aceptado. Resolve de paso a propiedade da SPI de bloqueos e a coordinación do bloqueo entre módulos.

**Descartado ou cambiado, e por que:**
- **Ocupación de tipo `BLOCK` recortada arredor das mantidas** (arquitectura): descartado. Obriga a reescribir filas de bloqueo cada vez que se mantén unha reserva e mete `booking` na escritura dos bloqueos. Co bloqueo de fila da instalación (que toman todas as escrituras e `booking`) non hai carreira, e «manter» é simplemente non tocar a reserva. A especificación só pide á base «un uso por franxa e cupo».
- **SPI de bloqueos con variante de borrado** (arquitectura): non fai falta. Sen ocupación de bloqueo, borrar un bloqueo non afecta a ningunha reserva.
- **Exclusión en base para dous usos que bloquean** (arquitectura): compróbase no dominio baixo o bloqueo da instalación, que serializa todas as escrituras de usos. A exclusión obrigaría a copiar `facility_id` e `blocks_others` en cada período con FK de coherencia, para unha regra que aínda é pregunta (P2).
- **`deferrable` en todas as restricións** (backend): só nos nomes das partes, onde o intercambio non ten outra saída. Na semana e na portada, `flush` explícito: o erro sae na sentenza e tradúcese ao código co mecanismo de sempre.
- **Límite por IP en `address-search`** (backend): non. A ruta pide `MANAGER`/`STAFF`, non é un proxy aberto; chega coa caché e o mínimo de caracteres.
- **Subir o límite da importación para as fotos** (infra): non agora; a exportación vai sen bytes de fotos (P32).
- **ES só cos comúns + ES-GA** (infra) fronte a **unha lista efectiva** (backend): quedo coa lista única de Galicia; é máis simple e evita festivos de máis.
- **D1-10b a F2** (arquitectura): non se pode; ao desaparecer o JSONB o modo `--custom-fields` rompe D45. Queda como S ao final, co cambio máis pequeno.
- **Fotos públicas só de instalacións visibles** (arquitectura): non no dominio 1. O xestor ve as fotos dun borrador cunha `<img>` que non leva token; os ids non se adiviñan. Revísase no dominio 2.
- **Orixe de aliñación das franxas en `CalendarQueries`** (arquitectura): é cousa de `booking`; `openingIntervals` devolve intervalos e a súa firma non cambia por iso. Pregúntase a produto no dominio 2.
- **Nomes `furar-date-picker`** (plan v1) fronte a `deseno.md`: quedan os de `deseno.md` (`furar-calendar`, `furar-date-field`) para non renomear o catálogo.
