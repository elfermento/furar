<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0040 · Buscar xente para xogar: unha reserva aberta a outras persoas, con mínimo, máximo e prazo do concello

- **Estado:** aceptada · 2026-11-12 (decidida polo mantedor; API e web construídas o 2026-11-12)
- **Revisada 2026-11-16 (decisión do mantedor):** a conservación das partidas (punto 13) xa non ten prazo propio: van coa súa reserva.
- **Fonte de produto:** [especificación](../produto/especificacion.md), «participantes e partido aberto» (capa posterior), decidido o 2026-11-11.
- **Constrúese enriba de:** [ADR-0025](0025-reservas-fase-1.md) (a persoa sen rol, avisos despois do commit), [ADR-0031](0031-persoas-a-cargo.md) (`booking_participant`, `close_account`), [ADR-0033](0033-lista-de-espera-e-ocos-parecidos.md) (liberar unha hora avisa a quen espera), [ADR-0032](0032-non-presentados-e-validacion.md), [ADR-0030](0030-portada-comun.md) (rutas do catálogo sen concello), [ADR-0037](0037-valoracions.md) (texto público que o xestor oculta), [ADR-0038](0038-eventos.md) (inscricións con aprobación) e [ADR-0017](0017-datos-persoais.md).
- **Revisa:** [ADR-0038](0038-eventos.md), «Futuro: partidas abertas» (punto 1: non se reutiliza a táboa das inscricións) e [ADR-0017](0017-datos-persoais.md) (punto 13: primeira publicación do nome dunha persoa da veciñanza).
- **Migracións:** `V202611121000__booking_open_match.sql`, `V202611121010__municipality_open_match_policy.sql`, `V202611121020__notification_open_match.sql` (parte de `V202611112210__notification_event.sql`) e `V202611121030__booking_open_match_close_account.sql` (parte de `V202611112205__event_close_account.sql`). A seguinte que redefina as restricións de `notification_delivery` ou `close_account` parte destas.

## Contexto
Quen reserva unha pista de fútbol sala ou de pádel e non completa o equipo acaba cancelando ou xogando a medias, e quen quere xogar e non ten con quen non ten onde atopar unha partida. Ao mesmo tempo, unha pista reservada «por se acaso» ata o último momento é unha hora que ninguén máis usa. Hai que deixar abrir unha reserva a outras persoas sen crear reservas novas, sen publicar máis datos dos precisos e sen que a pista quede collida se ao final non hai xente.

## Decidido polo mantedor (2026-11-11)
- Só en instalacións (nada de quedadas fóra). Quen ten unha reserva confirmada futura **busca xente para xogar**: «Xa somos» (por defecto, as persoas da reserva), **mínimo** para que se xogue e **máximo** de persoas (totais), nivel opcional (Sen indicar, Iniciación, Medio, Avanzado), título opcional, descrición libre e **«Con aprobación»** (apagado por defecto: quen queira unirse pídeo e quen organiza acepta ou rexeita). Pódese buscar aínda que falte unha soa persoa (xa somos 3, mínimo 4, máximo 4 → falta 1).
- **Prazo do concello:** en Concello › Xeral, interruptor «Buscar xente para xogar» e, con el aceso, «Cancelar se non hai mínimo» (horas antes). Se ao chegar o prazo non se chegou ao mínimo, **cancélanse a partida e a reserva** e avísase por correo a todas as persoas confirmadas e a quen organiza. Apagado, a función non existe.
- **O nome completo de quen organiza é público, tamén sen sesión** (é unha quedada: hai que saber con quen; valorouse a inicial e descartouse). Os nomes das demais persoas só os ven quen está dentro.
- Saen na **portada do concello**, na **ficha da instalación** (columna principal, despois de «Que se pode reservar») e nunha **sección aparte da portada común, despois das instalacións**. Nas dúas portadas, un **carrusel coa tarxeta dos eventos** (imaxe, día e hora, instalación, e na portada común o concello, título, deporte e nivel); na ficha, filas coma as de «Que se pode reservar» (nome, día e hora co nivel). As prazas libres van sempre no mesmo chip: «6 prazas libres» (verde), «Últimas 2 prazas» e «Última praza» (ámbar), «Completa» (gris); no carrusel, no pé da tarxeta. O nome de quen organiza non vai nelas: vese na páxina da partida. A páxina da partida ten a estrutura da ficha da instalación.
- Quen organiza: «Deixar de buscar» (a reserva e as persoas seguen) e «Cancelar a reserva» (cancela tamén a partida e avisa); as solicitudes con «Aceptar» e «Rexeitar» en botóns cheos.
- O xestor pode **ocultala** (chip-menú «Na portada / Oculta» no detalle da reserva); deixa de verse en público e avísase a quen organiza.
- Na tarxeta da reserva, chip «Buscando xente» (azul, info), distinto do «En espera» (gris).
- **Decidido sobre as preguntas do primeiro borrador (2026-11-12):** correo a quen organiza con cada solicitude nova; unha persoa bloqueada por faltas non se une; sen usos por prazas; ao apagar a función, as partidas deixan de verse e de admitir xente sen cancelar nada; a conservación é un parámetro (punto 13).

## Decisión
1. **Dentro de `booking`, con táboas propias; non se reutiliza `event`.** A partida é dunha reserva: nace dela, cancélase con ela, cambia de hora con ela e, no prazo, cancélaa. Todo iso pasa baixo o bloqueo da instalación e na transacción da propia reserva ([ADR-0022](0022-instalacions-horario-e-orde-de-bloqueo.md) punto 4), que só ten `booking`.
   - **Non nun módulo propio:** tería que cancelar reservas pola API pública de `booking` (que hoxe non escribe para fóra) e `booking` tería que avisalo dentro de cada cancelación ou cambio (un ciclo, ou unha SPI máis).
   - **Non xeneralizando `event_registration`** cun destino: `event` non depende de `booking` nin debe (un evento pode ser «Noutro sitio»), un destino polimórfico quedaría sen FK, e os ciclos de vida non se parecen.
   - **Si se reutiliza a mecánica** de [ADR-0038](0038-eventos.md): estados `PENDING`/`CONFIRMED`/`REJECTED`, «unha persoa, unha vez» por índice único, as prazas contadas baixo o bloqueo da fila da partida e o texto de «Con aprobación»; e, na web, as pezas de `shared/ui`.
   - SQL nativo con `JdbcClient` e `@NativeSql(scope = MUNICIPALITY)` (`OpenMatchSql`, `OpenMatchPlayerSql`), cun `<Clase>TenantIT` cada unha. Fóra de `furar-export`, coma as reservas.
2. **Só reservas de pista enteira ou por partes; quen se une é acompañante, non ocupa praza.**
   - Unha partida ábrese sobre unha reserva dun uso `WHOLE` ou `PARTS`: a reserva xa ocupa a pista ou a parte, e quen se une xoga nela sen ocupar nada máis. **Non vai a `booking_participant`** ([ADR-0031](0031-persoas-a-cargo.md)), que é a lista de prazas dun uso por prazas; nun uso enteiro ou por partes a reserva non leva participantes.
   - **Usos por prazas, non** (422 `booking.match-seats-use`).
   - O mínimo e o máximo son da partida, non do uso.
   - Quen se une non ten reserva propia: non conta no seu límite nin nas súas faltas e non ten QR.
3. **Modelo.** Táboas do concello, con RLS e a súa política na mesma migración:

   ```sql
   create table booking_open_match (
     id uuid primary key,
     municipality_id uuid not null references municipality (id),
     booking_id uuid not null,
     organizer_subject text not null check (length(organizer_subject) between 1 and 255),
     organizer_name text not null check (length(organizer_name) between 1 and 200),  -- o nome do token; nunca un correo
     known_count smallint not null check (known_count between 1 and 39),            -- «Xa somos», con quen organiza
     minimum smallint not null check (minimum between 2 and 40),
     maximum smallint not null check (maximum between 2 and 40),
     level text check (level in ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),          -- nulo = sen indicar
     title text check (length(title) between 1 and 80),
     description text check (length(description) between 1 and 1000),
     approval boolean not null default false,
     status text not null default 'OPEN' check (status in ('OPEN', 'CLOSED', 'CANCELLED')),
     cancel_reason text check (cancel_reason in ('UNFILLED', 'BOOKING_CANCELLED')),
     deadline_checked_at timestamptz,          -- o prazo pasou co mínimo cuberto
     hidden_at timestamptz, hidden_by text,
     created_at timestamptz not null, updated_at timestamptz not null, version bigint not null,
     constraint booking_open_match_tenant_uk unique (municipality_id, id),
     constraint booking_open_match_booking_uk unique (municipality_id, booking_id),   -- unha por reserva
     foreign key (municipality_id, booking_id) references booking (municipality_id, id),
     check (maximum >= minimum and known_count < maximum),
     check ((status = 'CANCELLED') = (cancel_reason is not null)),
     check ((hidden_at is null) = (hidden_by is null)),
     check (organizer_name not like '%@%')
   );
   create index booking_open_match_open_idx on booking_open_match (municipality_id, status)
     where status = 'OPEN';

   create table booking_open_match_player (
     id uuid primary key,
     municipality_id uuid not null references municipality (id),
     match_id uuid not null,
     account_subject text not null check (length(account_subject) between 1 and 255),
     email text not null check (length(email) between 3 and 254),     -- verificado, do token
     name text not null check (length(name) between 1 and 200 and name not like '%@%'),
     language text not null check (language in ('gl', 'es')),
     status text not null check (status in ('PENDING', 'CONFIRMED', 'REJECTED')),
     created_at timestamptz not null, decided_at timestamptz,
     constraint booking_open_match_player_tenant_uk unique (municipality_id, id),
     constraint booking_open_match_player_once_uk unique (municipality_id, match_id, account_subject),
     foreign key (municipality_id, match_id) references booking_open_match (municipality_id, id) on delete cascade,
     check (status <> 'PENDING' or decided_at is null),
     check (status <> 'REJECTED' or decided_at is not null)
   );
   create index booking_open_match_player_account_idx
     on booking_open_match_player (municipality_id, account_subject);

   alter table municipality                         -- o interruptor do concello, coma os non presentados
     add column open_match_since timestamptz,       -- nulo = apagado
     add column open_match_cancel_hours int not null default 12
       check (open_match_cancel_hours between 1 and 72),
     add column open_match_policy_changed_at timestamptz;   -- último cambio do interruptor ou das horas
   ```
   - **Contas:** `total = known_count + confirmadas`; «faltan» = `minimum − total` se é positivo; «quedan» = `maximum − total`; completa se `total = maximum`. As pendentes non ocupan sitio.
   - **O prazo** de cada partida é `inicio da reserva − open_match_cancel_hours` (instante real; a web amósao en Europe/Madrid: «Xógase se hai 10 persoas antes do venres ás 08:00. Máximo 12.»). Non se garda.
   - **Nunca se publica un correo como nome.** O nome sae de `Actor.Profile.name()`; hoxe `ReserveService.holder` usa o correo cando o token non trae nome. Aquí non: sen nome, 422 `booking.match-name-required` («Para buscar xente ou unirte fai falta o teu nome na conta.»), tanto para quen organiza coma para quen se une; e a base rexéitao (`not like '%@%'`).
4. **Abrir e editar** (quen organiza; `@PersonUseCase`). `PUT /api/v1/municipalities/{slug}/me/bookings/{bookingId}/open-match` crea ou edita. Baixo o bloqueo da fila da reserva (`for update`) e, se existe, o da partida, nesta orde:

   | Comproba | Se falla |
   |---|---|
   | O concello ten a función acesa | 404 `booking.matches-off` |
   | A reserva é da persoa (`holder_subject` = o `sub`), confirmada, sen marca de asistencia, sen entidade | 404 `booking.not-found` (allea) · 422 `booking.match-not-allowed` |
   | O uso é enteiro ou por partes | 422 `booking.match-seats-use` |
   | O token trae nome | 422 `booking.match-name-required` |
   | **Aínda se pode cancelar de balde** (`now < cancel_until`) **e** aínda non pasou o prazo do concello (`now < inicio − horas`) | 422 `booking.match-too-late` (`params.deadline`, o primeiro dos dous) |
   | `2 ≤ minimum ≤ maximum ≤ 40` e `1 ≤ knownCount < maximum` | 422 `booking.match-counts-invalid` |
   | Ao editar: `maximum ≥ known_count + confirmadas` | 422 `booking.match-maximum-below-count` (`params.count`) |

   - A condición `now < cancel_until` pecha a trampa da cancelación de balde: sen ela, despois do prazo do uso abriríase unha partida cun mínimo imposible e a tarefa cancelaría sen custo.
   - `knownCount` por defecto (na web) = `seats` da reserva (1 nunha pista enteira).
   - Unha `CLOSED` volve a `OPEN` co mesmo `PUT` (coas mesmas comprobacións); unha `CANCELLED` non se reabre.
   - **Deixar de buscar** `PUT …/open-match/status` `{status: CLOSED}`: xa non se une ninguén, deixa de verse en público e as solicitudes pendentes bórranse; a reserva e as confirmadas seguen. **Unha partida pechada non se cancela no prazo.**
   - **Aceptar / Rexeitar** `POST …/me/open-matches/{id}/players/{playerId}/accept` · `…/reject`: baixo o bloqueo da fila da partida; aceptar comproba que segue aberta, sen empezar e con sitio (409 `booking.match-full`). Xa decidida: 409 `booking.match-player-decided`. Rexeitar deixa a fila `REJECTED`.
5. **Unirse, pedir, saír** (persoa con sesión e correo verificado; `@PersonUseCase`).
   - `POST …/me/open-matches/{id}/players`: baixo **o bloqueo da fila da partida** (`select … for update`), nesta orde: función acesa, partida visible (non oculta), `OPEN` e sen empezar (404 `booking.match-not-found` / 409 `booking.match-closed`); non é quen organiza (409 `booking.match-own`); conta de traballo (403 `booking.staff-account`); bloqueo por faltas vixente (403 `booking.blocked-no-show`); nome no token (422 `booking.match-name-required`); xa está (200 co seu estado); foi rexeitada (409 `booking.match-rejected`); **queda sitio** contando as confirmadas baixo o bloqueo (409 `booking.match-full`). Sen aprobación entra `CONFIRMED`; con ela, `PENDING` e publícase `OpenMatchRequested` (correo a quen organiza, punto 12). **Dúas persoas á vez pola última praza:** a segunda agarda polo bloqueo, conta e recibe 409.
   - `DELETE …/me/open-matches/{id}/players/me`: saír ou retirar a solicitude; borra a fila; ata que empeza (despois, 409 `booking.match-started`). Saír despois do prazo non cancela nada: o prazo mírase unha vez.
   - **Orde de bloqueo:** unirse, aceptar, rexeitar, saír e ocultar só toman a fila da partida. Os camiños que tocan a reserva (cancelar, cambiar, o prazo) toman instalación → reserva → partida. Ningún camiño toma a partida e despois a reserva: sen ciclos.
6. **A reserva manda sobre a partida** (na transacción da reserva; `OpenMatchHooks`, chamado desde os servizos de `booking`):
   - **Cancelar a reserva** (`ChangeMyBookingService.cancel`, `ManageBookingService.cancel`, `BookingImpactService.apply`): a partida pasa a `CANCELLED` (`BOOKING_CANCELLED`), bórranse as pendentes e publícase `OpenMatchCancelled(municipalityId, matchId, reason)` → correo ás confirmadas. O diálogo de cancelar de quen organiza dío: «Cancélase tamén a partida e avisamos por correo ás N persoas apuntadas.»
   - **Cambiar a reserva pola propia persoa** (`ChangeMyBookingService.change`): se o novo prazo do concello ou o novo `cancel_until` xa pasaron, 422 `booking.match-too-late` (non se move; quen organiza pode antes «Deixar de buscar»). Se non, a partida segue á reserva e publícase `OpenMatchChanged` → correo ás confirmadas co día e a hora novos. A un uso por prazas, 422 `booking.match-seats-use`.
   - **Cambiar a reserva desde a xestión** (`BookForService.change`, tramo `rescheduled`): sempre se pode. Se o novo prazo xa pasou, a partida pasa a `CLOSED` (nunca se cancela soa por un cambio do persoal); en todo caso publícase `OpenMatchChanged` (o correo di o día e a hora novos e, se se pechou, «Xa non se busca máis xente.»). A un uso por prazas, a partida pasa a `CLOSED`.
   - **Corrixir o correo do titular no mostrador** (`BookForService.change`, tramo do titular, [ADR-0031](0031-persoas-a-cargo.md) punto 7) cambia de dono a reserva: a partida pasa a `CLOSED` sen correo.
   - A hora que se solta avisa a quen espera, coma sempre ([ADR-0033](0033-lista-de-espera-e-ocos-parecidos.md)).
7. **O prazo: tarefa programada idempotente e segura con varias instancias.** `OpenMatchDeadlineService` (`@PlatformTask`, `@Scheduled(cron = "${furar.booking.open-match-deadline-cron:0 */5 * * * *}")`): en cada concello coa función acesa, co seu `TenantContext.runAs` e a súa RLS:
   1. **Candidatas, sen bloqueos:** partidas `OPEN`, **sen ocultar** (`hidden_at is null`), sen comprobar (`deadline_checked_at is null`), coa reserva confirmada e o prazo pasado (`lower(b.period) − horas ≤ agora`) **e posterior ao último cambio da política** (`lower(b.period) − horas ≥ :desde`, con `:desde = coalesce(open_match_policy_changed_at, open_match_since)`). Así, acender a función ou subir as horas nunca cancela en bloque partidas cuxo prazo novo xa quedou atrás: esas xóganse coma se se cumprise o mínimo.
   2. **Cada unha na súa transacción curta:** `FacilityQueries.lockFacility` → fila da reserva `for update` → fila da partida `for update` → **volve comprobar** todo o anterior. Se mentres tanto a cancelou, pechou ou ocultou alguén, non fai nada.
   3. **Co mínimo cuberto:** `deadline_checked_at = agora`. Non se volve mirar.
   4. **Sen o mínimo:** a reserva cancélase **sen custo** (`Booking.cancelUnfilledMatch(now)`: estado `CANCELLED_BY_MUNICIPALITY`, actor `furar:open-match`; se estaba pagada, «a devolver»), con `booking_event` `CANCELLED_BY_MUNICIPALITY` e canle `EMAIL`; sóltase a ocupación e chámase a `WaitNotifier.released`; a partida pasa a `CANCELLED` (`UNFILLED`), bórranse as pendentes e publícase `OpenMatchCancelled(…, UNFILLED)`. **Non** se publica `BookingCancelled`: o correo de quen organiza é o da partida.
   - **Dúas instancias á vez:** a segunda agarda polo bloqueo da instalación e, ao volver comprobar, xa a atopa resolta: unha soa cancelación e un só correo. Repetir non cambia nada. Un fallo nunha partida non para as demais.
   - **Atraso:** ata 5 minutos sobre o prazo. Unha unión que chega nese intre conta: a tarefa conta baixo o bloqueo.
   - **Como abrir e mover xa esixen `now < cancel_until`** (puntos 4 e 6), a cancelación da tarefa nunca dá unha cancelación de balde que a persoa non tivese: se o prazo do concello cae despois do do uso, quen organiza tivo que abrir antes do do uso. Cando o persoal move a reserva, a partida péchase en vez de cancelarse.
   - **Función apagada:** a tarefa non mira ese concello; as partidas quedan como estaban, sen verse nin admitir xente, e ningunha se cancela.
8. **Ocultar (o xestor).** `PUT …/bookings/{bookingId}/open-match/hidden` `{hidden: boolean}` (permiso novo `booking.open-matches`), baixo o bloqueo da fila da partida. Oculta: fóra de todas as listas, a súa páxina pública dá 404, ninguén novo se une e **a tarefa do prazo non a cancela**; quen organiza e quen está dentro seguen véndoa co aviso «O concello ocultou esta partida». Publícase `OpenMatchHidden` → correo a quen organiza. Mostrala outra vez non manda correo.
9. **Que ve cada quen.**

   | Quen | Que ve |
   |---|---|
   | Calquera, sen sesión | Só partidas `OPEN`, non ocultas, sen empezar e co concello coa función acesa. Instalación (imaxe, enderezo e mapa), uso e parte, día e hora, nivel, título, descrición, «4 de 10 · faltan 6», o prazo e o máximo, «Con aprobación» e o **nome completo de quen organiza**. Ningún outro nome |
   | Con sesión e fóra | O mesmo e o seu estado: «Unirme» / «Pedir unirme», «Solicitude enviada» («Retirar a solicitude») ou que non entrou |
   | Confirmada | Ademais, os nomes das confirmadas (quen organiza primeiro, co chip «Organiza» e «+ N» se `known_count > 1`); «Estás dentro» e «Saír». Tamén pechada, cancelada ou oculta |
   | Quen organiza | O mesmo, as solicitudes pendentes cos seus nomes («Aceptar», «Rexeitar»), «Deixar de buscar» e «Cancelar a reserva» |
   | Xestión e Recepción (`booking.view`) | No detalle da reserva, o estado, os números e se está oculta; sen nomes de quen se une |
   | Plataforma | Nada |

   As listas mostran só as que teñen sitio; a páxina dunha completa segue vendo «12 de 12 · completa».
   - **Buscadores:** a páxina `/:concello/partidas/:id` leva `<meta name="robots" content="noindex">` (servizo `Meta` de Angular) e as respostas de `…/public/open-matches*` e `/catalog/open-matches` levan `X-Robots-Tag: noindex`. `robots.txt` xa exclúe `/api/`. As partidas non van aos datos abertos nin á ficha DCAT.
   - **Texto libre:** título e descrición en texto plano; a web non os converte en ligazóns nin interpreta HTML.
10. **Rutas, corpos e respostas** (os DTO son records en `adapter/in/web/dto/`; `api/openapi.json` e o cliente TS rexéneranse). Todas as respostas das partidas levan `X-Robots-Tag: noindex`.

    | Ruta | Corpo → resposta |
    |---|---|
    | `GET /api/v1/municipalities/{slug}/public/open-matches?facilityId=&page=&size=` (`@PublicEndpoint`, `@PublicUseCase`) | → `PageResponse<OpenMatchCardResponse>` (`{items, page, size, totalItems}`, coma o resto das listas); `OpenMatchCardResponse {id, facilityId, facilityName, facilityAddress?, cover?, useName, partName?, start, end, level?, title?, organizerName, total, minimum, maximum, approval}`; só as que teñen sitio, da que empeza antes. Coa función apagada, baleira |
    | `GET …/public/open-matches/{id}` | → `OpenMatchResponse` = a tarxeta máis `description?`, `deadline` e `status` (só `OPEN`, tamén completa; o resto, 404 `booking.match-not-found`). As coordenadas para o mapa saen da ficha pública da instalación (`facilityId`) |
    | `GET /api/v1/catalog/open-matches?page=&size=` (sen concello) | → `CatalogOpenMatchesResponse {page, size, hasNext, municipalities: [{slug, name, items: OpenMatchCardResponse[]}]}`: por páxinas de concellos coma `/catalog/availability`; cada concello publicado na portada común e coa función acesa, no seu `TenantContext.runAs` e a súa transacción; só saen os que teñen algunha partida |
    | `GET …/me/open-matches/{id}` (`ETag`) | → `MyOpenMatchResponse` = `OpenMatchResponse` (calquera estado para quen está dentro) máis `me: {role: ORGANIZER\|CONFIRMED\|PENDING\|REJECTED\|NONE, playerId?}`, `hidden`, `knownCount`, `bookingId?` (só quen organiza), `players: [{id, name}]` (só dentro; baleira fóra), `requests: [{id, name, requestedAt}]` (só quen organiza) e `version` |
    | `GET …/me/open-matches` | → `MyOpenMatchResponse[]`: as partidas que organiza ou nas que está ou pediu entrar, aínda sen rematar |
    | `PUT …/me/bookings/{bookingId}/open-match` (`If-Match` da partida ao editar; sen el, 428) | `OpenMatchRequest {knownCount, minimum, maximum, level?, title? (80), description? (1000), approval}` → 201 (`Location`) ou 200 `MyOpenMatchResponse`, co `ETag` |
    | `PUT …/me/bookings/{bookingId}/open-match/status` | `{status: OPEN\|CLOSED}` → 200 `MyOpenMatchResponse` |
    | `POST …/me/open-matches/{id}/players` | sen corpo → 201 (`Location`) ou 200 se xa estaba `MyOpenMatchResponse` |
    | `DELETE …/me/open-matches/{id}/players/me` | → 204 |
    | `POST …/me/open-matches/{id}/players/{playerId}/accept` · `…/reject` | → 200 `MyOpenMatchResponse` |
    | `PUT …/bookings/{bookingId}/open-match/hidden` | `{hidden}` → 200 `BookingOpenMatchResponse {id, status, hidden, total, minimum, maximum, confirmed, pending}` (`confirmed`: as persoas que se avisan se se cancela) |
    | `PUT /api/v1/municipalities/{slug}/open-match-policy` (`municipality.update`, `If-Match` do concello) | `OpenMatchPolicyRequest {enabled, cancelHours}` → 200 `OpenMatchPolicyResponse {enabled, cancelHours}`; tamén en `MunicipalityResponse` e `MunicipalityDataResponse`/`MunicipalityDataRequest` (`openMatchPolicy`), coma `noShowPolicy` |

    `MyBookingResponse` e `ManagedBookingDetailResponse` gañan `openMatch?: BookingOpenMatchResponse`.
11. **Permisos.** Os da persoa son `@PersonUseCase`. Novo `booking.open-matches` (ocultar e mostrar), declarado en `BookingPermission` cos roles `MANAGER`, coma `booking.reviews`. O persoal ve a partida no detalle da reserva con `booking.view`. A plataforma non ve nada ([ADR-0028](0028-administracion-da-plataforma-e-primeiro-acceso.md) punto 9); configura o interruptor con `municipality.update`.
12. **Correos** (`notification`; mesma plantilla `mail/booking.html`, mesmo renderizador que escapa, despois do commit, reintento por `MailRetry`).
    - `notification_delivery` gaña `open_match_player_id uuid`. A migración (`V202611121020__notification_open_match.sql`) **parte da última** que redefinía `notification_delivery_kind_ck` e `notification_delivery_target_ck` (`V202611112210__notification_event.sql`): volve declaralas coa lista de antes máis os tipos novos e `num_nonnulls(booking_id, invitation_id, wait_id, incident_id, municipal_event_id, open_match_player_id) = 1`, e engade as súas regras tipo↔destino: `check (kind not in ('MATCH_ACCEPTED', 'MATCH_REJECTED', 'MATCH_CHANGED') or open_match_player_id is not null)`, `check (kind not in ('MATCH_REQUESTED', 'MATCH_HIDDEN') or booking_id is not null)` e `check (open_match_player_id is null or kind in ('MATCH_ACCEPTED', 'MATCH_REJECTED', 'MATCH_CHANGED', 'MATCH_CANCELLED'))` (`MATCH_CANCELLED` vai á persoa ou, en `UNFILLED`, tamén a quen organiza pola súa reserva); índice único parcial `(municipality_id, open_match_player_id, kind, event_id) where open_match_player_id is not null`. Os de quen organiza (`MATCH_REQUESTED`, `MATCH_HIDDEN`, `MATCH_CANCELLED` por `UNFILLED`) van co `booking_id` da súa reserva; `event_id` = o id da solicitude en `MATCH_REQUESTED` e o da publicación nos demais.
    - Os eventos `OpenMatchCancelled`, `OpenMatchChanged`, `OpenMatchHidden`, `OpenMatchRequested` e `OpenMatchDecided` (sen persoas: só ids e a clave da publicación) entran na lista `MAIL_EVENTS` de `MailRetry`. `BookingQueries.openMatchNotice(matchId, kind, playerId?)` dá os destinatarios co estado de agora (unha partida xa reaberta ou unha solicitude xa retirada non manda nada). O detalle do persoal («último correo») só mira os tipos das reservas.

    | `MailKind` | A quen | Asunto (gl / es) | Corpo |
    |---|---|---|---|
    | `MATCH_CANCELLED` (`UNFILLED`) | Confirmadas e quen organiza | «Partida cancelada: ‹título›» / «Partida cancelada: ‹título›» | «Non chegou a ‹mínimo› persoas antes do ‹día› ás ‹hora›, así que a partida cancelouse, e con ela a reserva da pista.» / «No llegó a ‹mínimo› personas antes del ‹día› a las ‹hora›, así que la partida se ha cancelado, y con ella la reserva de la pista.»; tarxeta; «Se queres xogar outro día, mira as partidas abertas de ‹concello›.» / «Si quieres jugar otro día, mira las partidas abiertas de ‹concello›.»; botón «Ver partidas abertas» |
    | `MATCH_CANCELLED` (`BOOKING_CANCELLED`) | Confirmadas | O mesmo asunto | «Cancelouse a reserva da pista e, con ela, a partida.» / «Se ha cancelado la reserva de la pista y, con ella, la partida.»; tarxeta; o mesmo botón |
    | `MATCH_CHANGED` | Confirmadas | «Cambiou a partida: ‹título›» / «Ha cambiado la partida: ‹título›» | «A partida pasa a ‹día›, ‹hora›.» / «La partida pasa al ‹día›, ‹hora›.» (pechada: máis «Xa non se busca máis xente.» / «Ya no se busca más gente.»); tarxeta; «Se xa non podes ir, sae para deixar a praza.» / «Si ya no puedes ir, sal para dejar la plaza.»; botón «Ver a partida» |
    | `MATCH_REQUESTED` | Quen organiza | «Alguén quere unirse: ‹título›» / «Alguien quiere unirse: ‹título›» | «‹Nome› pediu unirse á túa partida.» / «‹Nombre› ha pedido unirse a tu partida.»; botón «Ver as solicitudes» |
    | `MATCH_ACCEPTED` | Quen pediu | «Xa estás na partida: ‹título›» / «Ya estás en la partida: ‹título›» | «‹Organiza› aceptou a túa solicitude.» / «‹Organiza› ha aceptado tu solicitud.»; tarxeta; «Xógase se hai ‹mínimo› persoas antes do ‹día› ás ‹hora›. Se non, avisámoste.» / «Se juega si hay ‹mínimo› personas antes del ‹día› a las ‹hora›. Si no, te avisamos.»; botón «Ver a partida» |
    | `MATCH_REJECTED` | Quen pediu | «Non entraches na partida: ‹título›» / «No has entrado en la partida: ‹título›» | «Quen organiza non aceptou a túa solicitude. Mira outras partidas abertas de ‹concello›.» / «Quien organiza no ha aceptado tu solicitud. Mira otras partidas abiertas de ‹concello›.»; botón «Ver partidas abertas» |
    | `MATCH_HIDDEN` | Quen organiza | «Ocultamos a túa partida: ‹título›» / «Hemos ocultado tu partida: ‹título›» | «O concello ocultou a túa partida: xa non se ve na portada e ninguén máis se pode unir. A reserva segue. Se tes dúbidas, chama ao ‹teléfono›.» / «El ayuntamiento ha ocultado tu partida: ya no se ve en la portada y nadie más se puede unir. La reserva sigue. Si tienes dudas, llama al ‹teléfono›.» (sen teléfono, remata en «A reserva segue.») |

    Sen título, «‹uso› · ‹día› ‹hora›». Unirse directamente non manda correo, coma nos eventos.
13. **Datos persoais** (filas en [`datos-persoais.md`](../datos-persoais.md)).
    - **Base 6.1.e, coma o resto das reservas, sen casiña.** A publicación do nome é consecuencia dunha acción voluntaria da persoa (buscar xente), necesaria para a finalidade (quedar para xogar con persoas que non se coñecen); a ponderación: valorouse a inicial e descartouse porque non deixa saber con quen se queda. O diálogo leva a información do art. 13 antes de «Publicar», sen nada que marcar: **que** se publica (o nome e apelidos, a instalación, o día e a hora e o texto que escriba), **onde** (a portada do concello, a ficha da instalación e a portada común) e **ata cando** (mentres busca xente e antes de empezar). *Revisada 2026-11-16:* a información vai no «?» do campo «Título» do diálogo (`openMatch.dialog.privacyTip`, que describe o campo), antes de «Publicar» e sen nada que marcar: a partida vese na web do concello ata que se xogue ou se deixe de buscar, e na súa páxina aparece o nome de quen organiza (nas portadas xa non). Retírase ao instante con «Deixar de buscar» ou cancelando, o que atende tamén a oposición (art. 21). É a primeira publicación en internet do nome dunha persoa da veciñanza: vai na revisión de [ADR-0017](0017-datos-persoais.md). Pendente da revisión legal xa anotada.
    - **Menores de 14 a 17 anos con conta propia:** o seu nome completo sería público e Furar non sabe a idade (non pide data de nacemento). Risco anotado; o diálogo dío e o xestor pode ocultar.
    - **Texto libre:** o diálogo avisa de que non se poñan datos persoais doutras persoas; o xestor oculta.
    - **Quen se une:** conta, nome, correo e idioma do token e o estado; os nomes só os ven as persoas da partida.
    - **Conservación (revisada o 2026-11-16):** a partida (quen organiza, quen se uniu e as solicitudes) consérvase o mesmo ca a súa reserva (`furar.booking.retention`, `P2Y`, ou `furar.booking.paid-retention`, `P5Y`, se é de pago) e bórrase no mesmo lote da tarefa diaria `BookingRetentionService` en que se anonimiza a reserva, nunca antes. Sen opción propia (ata entón, `furar.booking.open-match-retention`, `P30D`). As solicitudes pendentes seguen borrándose ao pechar, cancelar ou empezar a partida: unha solicitude que xa non se pode aceptar non ten finalidade.
    - **Baixa da conta:** `V202611121030__booking_open_match_close_account.sql` redefine `close_account` partindo da **última** redefinición (`V202611112205__event_close_account.sql`): borra as filas de `booking_open_match_player` da conta e as partidas que organiza (coas súas persoas, **sen avisalas**: é un paso á man de quen opera o despregue; documentado no procedemento de baixa).
    - Nin o rexistro de actividade nin o técnico levan nomes, títulos nin descricións.
    - **Textos que cambian** (`web/src/i18n/{gl,es}.json`, rexistro formal; pendentes da revisión legal):

      | Clave | Galego | Castelán |
      |---|---|---|
      | `privacy.residents.dataOpenMatches` (nova) | Das partidas abertas: cando unha persoa busca xente para xogar, publícanse o seu nome e apelidos xunto coa partida (instalación, día, hora, nivel e o texto que escriba) na portada do concello, na ficha da instalación e na portada común, mentres busca xente e antes de que empece; deixa de publicarse en canto deixa de buscar ou cancela. De quen se une gárdanse a conta, o nome, o correo e o idioma; o nome só o ven as persoas da partida. | De las partidas abiertas: cuando una persona busca gente para jugar, se publican su nombre y apellidos junto con la partida (instalación, día, hora, nivel y el texto que escriba) en la portada del ayuntamiento, en la ficha de la instalación y en la portada común, mientras busca gente y antes de que empiece; deja de publicarse en cuanto deja de buscar o cancela. De quien se une se guardan la cuenta, el nombre, el correo y el idioma; el nombre solo lo ven las personas de la partida. |
      | `privacy.retention.openMatches` (nova; revisada o 2026-11-16) | Partidas abertas (quen organiza, quen se une e as solicitudes): o mesmo ca a súa reserva, e bórranse cando se anonimiza. | Partidas abiertas (quien organiza, quien se une y las solicitudes): lo mismo que su reserva, y se borran cuando se anonimiza. |
      | `openMatch.dialog.privacyTip` (información no «?» do título; revisada 2026-11-16) | A partida vese na web do concello ata que se xogue ou deixes de buscar. Na súa páxina aparece o teu nome. | La partida se ve en la web del ayuntamiento hasta que se juegue o dejes de buscar. En su página aparece tu nombre. |

      `GET /api/v1/privacy-notice` xa non leva prazo propio das partidas (ata o 2026-11-16, `openMatchRetention`): valen os das reservas.
14. **Rexistro de actividade** (só o persoal): `booking.open-match-hide` e `booking.open-match-show`, con `matchId`, `bookingId` e `facilityId`; nunca o título, a descrición nin nomes. Configurar a política vai no rexistro do concello (`MunicipalityAudit`, acción `municipality.open-match-policy` cos campos cambiados `openMatch.enabled` e `openMatch.cancelHours`). O que fan as persoas non vai ao rexistro. A cancelación da tarefa queda na reserva (`booking_event`, actor `furar:open-match`) e o rexistro técnico só leva cantas cancelou por concello.
15. **Erros** (`BookingError`; texto en `messages/booking_{gl,es}.properties` e na web):

    | Código | HTTP | Galego | Castelán |
    |---|---|---|---|
    | `booking.matches-off` | 404 | Este concello non ten partidas abertas. | Este ayuntamiento no tiene partidas abiertas. |
    | `booking.match-not-allowed` | 422 | Esta reserva non se pode abrir a outras persoas. | Esta reserva no se puede abrir a otras personas. |
    | `booking.match-seats-use` | 422 | Neste uso cada persoa reserva a súa praza: non se pode buscar xente. | En este uso cada persona reserva su plaza: no se puede buscar gente. |
    | `booking.match-name-required` | 422 | Para buscar xente ou unirte fai falta o teu nome na conta. | Para buscar gente o unirte hace falta tu nombre en la cuenta. |
    | `booking.match-too-late` | 422 | Xa pasou o prazo para buscar xente ({deadline}). | Ya ha pasado el plazo para buscar gente ({deadline}). |
    | `booking.match-counts-invalid` | 422 | O mínimo vai de 2 a {max}; o máximo non pode ser menor ca o mínimo, e «Xa somos» ten que ser menor ca o máximo. | El mínimo va de 2 a {max}; el máximo no puede ser menor que el mínimo, y «Ya somos» tiene que ser menor que el máximo. |
    | `booking.match-maximum-below-count` | 422 | Xa sodes {count}: o máximo non pode ser menor. | Ya sois {count}: el máximo no puede ser menor. |
    | `booking.match-not-found` | 404 | Esta partida xa non está dispoñible. | Esta partida ya no está disponible. |
    | `booking.match-closed` | 409 | Esta partida xa non admite máis xente. | Esta partida ya no admite más gente. |
    | `booking.match-full` | 409 | A partida xa está completa. | La partida ya está completa. |
    | `booking.match-own` | 409 | Es quen organiza esta partida. | Eres quien organiza esta partida. |
    | `booking.match-rejected` | 409 | Quen organiza non aceptou a túa solicitude. | Quien organiza no ha aceptado tu solicitud. |
    | `booking.match-player-decided` | 409 | Esta solicitude xa se decidiu. | Esta solicitud ya se ha decidido. |
    | `booking.match-started` | 409 | A partida xa empezou. | La partida ya ha empezado. |
16. **Horas.** Todo en `Instant`/`timestamptz`. O prazo é tempo real (inicio menos N horas); a web móstrao en Europe/Madrid co día e a hora que dá a API.
17. **Demo de Narón** (`R__demo_naron_open_matches.sql`, que se aplica despois de `R__demo_naron_bookings.sql`): política acesa con 12 horas; dúas partidas sobre reservas de demo futuras de ids fixos, **só se a reserva existe e está confirmada** (o mesmo patrón de `R__demo_naron_bookings.sql`: `continue when` se falta; nunca falla o arranque): fútbol sala sen aprobación, 4 de 10; pádel con aprobación, 3 de 4 e unha solicitude pendente. Nomes ficticios; a conta `vecino-naron@example.org` organiza a primeira. Datas relativas ao día de carga.

## Consecuencias
- Unha reserva pode levar xente sen ocupar nada máis e sen reservas novas.
- Unha pista collida «por se acaso» sóltase soa ao prazo se non hai xente, e a hora avisa a quen esperaba.
- Primeira publicación en internet dun nome da veciñanza: só o de quen organiza, por decisión propia, mentres busca e sen indexar.
- Abrir unha partida só se pode mentres a reserva se pode cancelar de balde; un cambio do persoal pecha a partida en vez de cancelala.
- `booking` gaña dúas táboas, unha tarefa cada 5 minutos (unha lectura por índice cando non toca nada) e `notification`, sete correos.
- A lista das rutas sen concello gaña `/catalog/open-matches` ([`arquitectura.md`](../arquitectura.md) §4).
- `close_account` e as restricións de `notification_delivery` redefínense outra vez: a seguinte migración que as toque parte destas.
- [ADR-0038](0038-eventos.md), «Futuro: partidas abertas», queda resolto: táboa irmá en `booking`.

## Preguntas abertas (co valor que se constrúe)
- Ningunha (a conservación das partidas decidiuse o 2026-11-16: coa súa reserva).

## Alternativas descartadas
- **Xeneralizar `event_registration` cun destino:** `event` non depende de `booking`, o destino polimórfico quedaría sen FK e a partida vive da reserva baixo un bloqueo que só ten `booking`.
- **Módulo `match` aparte:** precisaría unha API de escritura de `booking` e unha SPI dentro de cada cancelación ou cambio.
- **Quen se une en `booking_participant`:** é a lista de prazas dun uso por prazas e entra na exclusión «unha praza por persoa».
- **Unha reserva por persoa que se une:** códigos, QR, límites e faltas para xente que só acompaña.
- **Prazo fixado por quen organiza:** o mantedor decidiu que o fixa o concello.
- **Comprobar o prazo cando alguén abre a páxina:** sen visitas, a pista quedaría collida.
- **Tarefa sen o bloqueo da instalación:** cancelar escribe a ocupación e rompería a orde instalación → ocupación.
- **Deixar abrir despois de `cancel_until`:** a tarefa daría unha cancelación de balde que a persoa xa non tiña.
- **Cancelar soa a partida cando o persoal move a reserva a un prazo xa pasado:** a persoa perdería a reserva por algo que non fixo; péchase.
- **Base 6.1.a (consentimento) para o nome:** o repositorio non usa o consentimento para o servizo; a publicación forma parte da función que a persoa escolle usar, con información e retirada inmediata.
- **Usar o correo cando o token non trae nome:** publicaría un correo.
- **Nome con inicial ou só con sesión:** o mantedor decidiu o nome completo e público.
