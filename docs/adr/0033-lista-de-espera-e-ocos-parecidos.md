<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0033 · Lista de espera e ocos parecidos: horas ocupadas á vista, aviso a todas á vez e alternativas por regras

- **Estado:** aceptada · 2026-11-10 (decidida polo mantedor; construída o 2026-11-10)
- **Revisada 2026-11-10 (respostas do mantedor):** as preguntas abertas PC1 a PC16 quedan decididas co valor proposto ([táboa](#decidido-polo-mantedor-sobre-as-preguntas-abertas-2026-11-10)). Non queda ningunha pregunta de produto aberta.
- **Revisada 2026-11-10 (ao construír):** o punto 8 pasa dunha sentenza a dúas (ou tres) na mesma transacción, coa mesma garantía, porque a garda do SQL nativo (A8b e `NativeSqlRowLevelSecurityIT`) non le unha CTE nin un `on conflict do update`; `AffectedBookingsGate` non chamaba a `apply` sen reservas afectadas e agora chámao sempre, e `FacilityUseService` chámao tamén ao cambiar ou arquivar un uso (punto 5); as migracións levan a hora do día en que se crearon.
- **Revisada 2026-11-11 (decisión do mantedor):** a primeira regra dos ocos parecidos deixa de ser «±60 minutos» e pasa a ser a franxa anterior ou a seguinte do mesmo uso na mesma instalación (punto 9 e PC3).
- **Revisada 2026-11-11 (revisión do cambio «`apply` sempre»):** as esperas que borra un cambio da instalación deixan un evento no rexistro de actividade só co número e o tipo de cambio; cambiar as datas ou os días dun uso borra as esperas dos días que quedan fóra; os borrados de varias filas bloquéanas en orde; e anótanse os límites coñecidos (punto 5).
- **Revisada 2026-11-11 (decisión do mantedor, «Lista de espera cancelada»):** quitarlle á instalación a reserva por internet, pechala ou dala de baixa borra as súas esperas e **cada persoa recibe un correo**, un só con todas as súas horas nesa instalación (cambia PC14 só para eses tres cambios; un bloqueo, o horario ou un uso seguen borrando sen correo). Quitar a reserva por internet pasa a tomar o bloqueo da instalación e chama a `apply` cun cambio propio (`FacilityChange.OnlineBookingOff`): resolve o primeiro «límite coñecido» do punto 5. Na mesma transacción, as esperas borradas deixan o correo, o idioma e a franxa en `booking_wait_cancelled` (con RLS, 7 días, `furar.booking.cancelled-wait-retention`) e publícase `BookingWaitsCancelled`, sen persoas; `notification` escribe despois do commit (`MailKind.WAIT_CANCELLED`, o mesmo rexistro de envíos, coa primeira espera da persoa como destino e a cancelación como evento). Texto: asunto «Lista de espera cancelada: ‹instalación›»; «‹instalación› xa non está dispoñible para reservar. Estabas en lista de espera para o ‹día›, ‹hora›. Cancelamos a túa espera, porque esa hora xa non se poderá reservar. Podes buscar outra instalación ou chamar ao ‹teléfono›.» (o teléfono do servizo de deportes; sen el, remata en «Podes buscar outra instalación.»), en plural se son varias. Segue o rastro `booking.waits-removed`.
- **Fonte de produto:** [especificación](../produto/especificacion.md), bloque 9 («Lista de espera e ocos parecidos»), decidido o 2026-11-10: selector de hora, «Avísame se se libera», «Ocos parecidos», tarxeta «En espera» e correo «Quedou libre».
- **Constrúese enriba de:** [ADR-0031](0031-persoas-a-cargo.md) («a reserva é do correo», `holder_email_key`, `EmailKey.of`, N prazas, `close_account`) e [ADR-0032](0032-non-presentados-e-validacion.md) (bloqueo por faltas). Vai despois delas.
- **Revisa:** [ADR-0025](0025-reservas-fase-1.md) puntos 15 e 17 (un correo máis; a dispoñibilidade dun uso leva tamén as horas ocupadas) e [`arquitectura.md`](../arquitectura.md) §3 (non se crea o módulo `recommendation`: punto 9).
- **Como ler esta ADR:** o decidido polo mantedor está en «Decidido polo mantedor» e na táboa das preguntas abertas, xa respondidas. A «Decisión» é o deseño técnico; **(PCn)** remite á fila desa táboa que fixa o valor.
- **Revisada 2026-11-12 ([ADR-0040](0040-buscar-xente-para-xogar.md)):** solta ocupación, e chama a `WaitNotifier.released`, tamén a cancelación dunha partida aberta sen mínimo.
- **Revisada 2026-11-12 ([ADR-0039](0039-tempada-de-entidades.md)):** soltan ocupación, e chaman a `WaitNotifier.released`, tamén cancelar unha sesión ou unha reserva extra desde a entidade e rematar unha tempada (unha vez para todas as sesións).

## Contexto
O selector de hora só lista as horas libres: a ocupada non existe para a persoa, que non sabe se a hora que quería está collida nin ten onde apuntarse. Cando alguén cancela, a hora volve saír sen que ninguén se entere. A especificación xa prevía «Avísame se se libera» e unha recomendación por regras detrás dun porto. Hai que facelo sen quendas nin reservas automáticas, sen mostrar datos de quen ocupa a hora, sen gardar a espera máis do preciso e sen que unha lectura custe máis canto máis instalacións teña o concello.

## Decidido polo mantedor (2026-11-10)
- As horas ocupadas saen no selector de hora, en gris e con «Ocupada». Ao elixir unha, no canto de «Continuar», «Avísame se se libera»; despois, a confirmación e «Deixar de esperar» (botón de perigo).
- Debaixo, «Ocos parecidos»: por regras, sen IA, detrás dun porto substituíble, só ocos que a persoa pode reservar. Orde: mesma instalación, a franxa anterior ou a seguinte (decidido o 2026-11-11; antes, «±1 h») → outra instalación co mesmo uso a esa hora → outra máis tarde ese día → mesmo día e hora a semana seguinte. Pulsar unha déixaa elixida. Sen «horas tranquilas» por agora.
- Ao cancelarse unha reserva (pola persoa ou polo concello) ou cambiarse de hora, avísase por correo, á vez, a todas as persoas que esperan esa hora; reserva a primeira que entre. Sen quenda, sen prazo e sen reserva automática.
- As esperas vense en «As miñas reservas → Próximas», nunha tarxeta co chip «En espera» e «Deixar de esperar». Sen pestana nin pantalla nova.
- Límite fixo: 3 esperas activas por persoa e concello.
- Ao pasar a hora, a espera bórrase; só queda un reconto anónimo (demanda non atendida).
- Fai falta conta: a espera é da conta e o aviso vai ao seu correo.

## Decisión
1. **Que é «esa hora».** Unha espera é dunha **conta**, por **un uso e unha franxa** dese uso (`[inicio, inicio + duración do uso)`), sen número de franxas e sen parte **(PC1)**. Unha hora está **ocupada** para un uso cando é un paso da grella dun anaco aberto ([ADR-0025](0025-reservas-fase-1.md) punto 6), a antelación do uso deixaría reservala e non se pode por ocupación:
   - **enteira:** hai unha reserva nela;
   - **por partes:** non queda ningunha parte libre;
   - **por prazas:** non queda ningunha praza;
   - **calquera modo:** outro uso da instalación ocupa esa franxa («un uso por franxa», [ADR-0012](0012-ocupacion-exclusiva-e-prazas.md)).

   Fóra de horario, bloqueada, xa empezada ou fóra da antelación non é «ocupada»: non sae, coma hoxe. Un día en que o uso non está vixente, ou en que outro uso bloquea os demais (`exclusiveUseOn`), non ten horas libres nin ocupadas.
2. **A API di que horas están ocupadas e nada máis.** `GET …/public/facilities/{id}/availability` gaña `busy: [{start, startMinute}]`, en orde; `slots` non cambia (un cliente antigo segue vendo só as libres). Sen quen, sen cantas reservas, sen parte e sen o motivo (o mesmo uso ou outro). Só sae cando `bookable` é verdadeiro. **Que revela de novo:** ata agora unha hora que faltaba podía estar reservada, bloqueada ou pechada; agora sábese que está reservada. É un dato da instalación, non dunha persoa. No dominio, `SlotGrid.busySlots(use)` coas mesmas celas ca `freeSlots`. As listas (`…/public/availability`, a portada) non cambian.
3. **Modelo** (`V202611101900__booking_wait.sql`). Dúas táboas do concello, con RLS e as políticas `booking_wait_tenant` e `booking_wait_tally_tenant` na mesma migración:

   ```sql
   create table booking_wait (
     id uuid primary key,
     municipality_id uuid not null references municipality (id),
     facility_id uuid not null, use_id uuid not null,
     slot_start timestamptz not null, slot_end timestamptz not null,
     account_subject text not null check (length(account_subject) between 1 and 255),
     email_key text not null check (length(email_key) between 3 and 254),  -- EmailKey.of do correo verificado
     language text not null check (language in ('gl', 'es')),
     ordinal smallint not null check (ordinal between 1 and 3),            -- o límite, na base
     created_at timestamptz not null,
     constraint booking_wait_tenant_uk unique (municipality_id, id),
     constraint booking_wait_once_uk unique (municipality_id, account_subject, use_id, slot_start),
     constraint booking_wait_limit_uk unique (municipality_id, account_subject, ordinal),
     foreign key (municipality_id, facility_id, use_id)
       references facility_use (municipality_id, facility_id, id),
     check (slot_end > slot_start)
   );
   create index booking_wait_facility_idx on booking_wait (municipality_id, facility_id, slot_start);
   create index booking_wait_start_idx on booking_wait (municipality_id, slot_start);
   create index booking_wait_email_idx on booking_wait (municipality_id, email_key, use_id, slot_start);

   create table booking_wait_tally (              -- reconto anónimo: sen persoa
     municipality_id uuid not null references municipality (id),
     facility_id uuid not null, use_id uuid not null,
     slot_start timestamptz not null,
     waits int not null check (waits >= 1),
     primary key (municipality_id, use_id, slot_start),
     foreign key (municipality_id, facility_id, use_id)
       references facility_use (municipality_id, facility_id, id)
   );
   ```
   - **Correo:** `email_key` escríbeo a aplicación con `shared.domain.EmailKey.of` ([ADR-0031](0031-persoas-a-cargo.md) punto 4), a mesma clave ca `booking.holder_email_key`; a base non normaliza. É tamén o enderezo ao que vai o aviso. Sen nome: o correo non saúda.
   - **Se a persoa cambia o correo da conta:** cada caso de uso das esperas (punto 5) pon antes o correo verificado do token nas esperas desa conta que teñan outro. Ata que volva entrar, o aviso vai ao correo co que se apuntou.
   - `CrossModuleForeignKeys` gaña `booking_wait → facility_use` e `booking_wait_tally → facility_use` (`facility_use` xa é táboa publicada de `facility`).
   - SQL nativo con `JdbcClient` (`BookingWaitSql`, `@NativeSql` de concello, A8b), coma o resto de `booking`. Fóra de `furar-export`.
4. **Límite fixo: 3 esperas activas por conta e concello,** garantido na base. Activa = a súa franxa aínda non empezou. O 3 é unha constante do dominio (`WaitLimit.MAX`).
   - Antes de inserir, a mesma transacción vence as esperas xa pasadas desa conta (punto 8), para que non ocupen sitio.
   - Insírese probando os ordinais 1, 2 e 3: `insert … on conflict do nothing returning id`. Se non entra, mírase se a conta xa espera por ese uso e franxa (`booking_wait_once_uk`): se si, devólvese esa espera; se non, próbase o ordinal seguinte. Sen ningún libre, 422 `booking.wait-limit`.
   - `on conflict do nothing` agarda pola transacción que ten o ordinal e segue, **sen abortar a transacción**: tres «esperar» á vez da mesma conta sobre instalacións distintas entran as tres, e a cuarta dá 422. Cada transacción só agarda por unha fila que a outra vai confirmar, así que non hai interbloqueo.
5. **Casos de uso** (todos `@PersonUseCase`: sesión e correo verificado; filtran polo `sub`; sen permisos novos). Porto de entrada `WaitUseCase`, servizo `WaitService`.
   - **Esperar** (`join`): abre unha transacción e toma **o bloqueo da instalación** (`FacilityQueries.lockFacility`, [ADR-0025](0025-reservas-fase-1.md) punto 5). Baixo el, nesta orde:

     | Comproba | Se falla |
     |---|---|
     | Conta de traballo | 403 `booking.staff-account` |
     | Bloqueo por faltas vixente ([ADR-0032](0032-non-presentados-e-validacion.md) punto 7) **(PC6)** | 403 `booking.blocked-no-show` |
     | Instalación visible, activa e con reserva por internet; uso vivo e vixente ese día | 422 `booking.facility-not-bookable` · `booking.use-not-available` |
     | A franxa é un paso da grella, non empezou e a antelación permítea | 422 `booking.slot-invalid` · `booking.too-soon` · `booking.too-far` |
     | A hora segue ocupada | 409 `booking.wait-slot-free` |
     | O correo non ten xa reserva confirmada dese uso que pise a franxa (por `holder_email_key`) **(PC16)** | 409 `booking.wait-own-booking` |
     | Xa espera por ela | 200 coa espera que hai (repetir non cambia nada) |
     | Queda sitio (punto 4) | 422 `booking.wait-limit` (`params.max` = 3) |

     O bloqueo fai falta: sen el, unha espera creada mentres outra transacción cancela podería quedar sen aviso cunha hora xa libre. Con el, ou a cancelación ve a espera e avisa, ou a espera ve a hora libre e dá 409. Non se comproba o límite por persoa do uso **(PC7)**.
   - **Deixar de esperar** (`leave`): borra a fila. Sen reconto.
   - **As miñas esperas** (`mine`): as activas, por inicio, cos nomes de agora da instalación e do uso.
   - **A espera bórrase cando o seu correo consegue reserva nesa franxa, por calquera vía.** Reservar por internet, reservar no mostrador (`BookForService`) e cambiar unha reserva (persoa ou persoal), na súa transacción e despois de escribir a reserva: `delete from booking_wait where municipality_id = :m and email_key = :clave and use_id = :uso and slot_start < :fin and slot_end > :inicio`. Atendida: non conta no reconto. Cóbreo «a reserva é do correo»: a reserva do mostrador a nome dese correo tamén a borra.
   - **A espera bórrase cando a súa hora deixa de existir (PC14).** `BookingImpactService.apply`, que xa corre baixo o bloqueo con cada cambio da instalación (bloqueo, peche, baixa, horario, excepción, uso cambiado ou arquivado), borra as esperas activas que o cambio deixa fóra: as que pisan os tramos perdidos, as do uso arquivado ou cambiado, ou todas as da instalación pechada. Sen correo e sen reconto. Comprobado ao construír: `AffectedBookingsGate` non chamaba a `apply` cando non había reservas afectadas e `BookingImpactService.apply` saía sen facer nada sen decisións; agora o primeiro chámao sempre e o segundo borra as esperas tamén sen decisións. Cambiar un uso de xeito que toque as súas reservas e arquivalo non pasaban pola porta (rexéitanse se hai reservas): `FacilityUseService` chama a `apply` con `UseChanged` e `UseArchived` despois de gardar. Un uso que pasa a bloquear os demais borra as esperas dos outros usos neses días. Cambiar as datas, os días da semana ou a dispoñibilidade dun uso (que se pode facer sempre, con reservas ou sen elas) borra as súas esperas dos días nos que deixa de valer (`FacilityChange.UseDatesChanged`), e conserva as demais.
     - **Rastro (2026-11-11).** Quen bloquea, pecha, cambia o horario ou un uso pode non ter permiso para cancelar reservas (a plataforma ten `facility.manage` sen `booking.cancel`) e, aínda así, o seu cambio borra esperas. Cando borra algunha, `WaitNotifier.gone` rexistra un evento `booking.waits-removed` ([ADR-0020](0020-rexistro-de-actividade.md)) con `count` (cantas), `facilityChange` (o tipo de cambio) e `facilityId`; sen nomes, contas nin correos. Sen esperas borradas non se rexistra nada: un cambio sen reservas afectadas nin esperas segue sen deixar `audit_event` propio nin correo.
     - **Límite coñecido.** Tres camiños deixan unha hora sen poder reservarse e non pasan por `apply`: quitarlle á instalación a reserva por internet ou a visibilidade (`updateDetails` e a publicación, que non toman o bloqueo da instalación) e os festivos do despregue (que non son de ningunha instalación). A espera queda viva: non recibe avisos (a hora non queda «libre»), segue vista como «En espera» e vence soa ao pasar a hora; **entón si conta no reconto anónimo**, aínda que a hora non se puidese reservar. Para evitalo abondaría con que `updateDetails` tomase o bloqueo da instalación e chamase a `apply` cun cambio propio cando se apaga a reserva por internet (as esperas bórranse coma nun peche); non se fixo porque cambia a orde de bloqueo dese caso de uso. Os festivos pedirían percorrer as instalacións de cada concello.
     - **Orde de bloqueo das filas.** Os borrados de varias esperas (`deleteAll`, o vencemento) bloquean as filas por `id` (`select … order by id for update`), así que un cambio da instalación e a tarefa horaria non se interbloquean. `close_account` (unha migración xa aplicada) borra sen esa orde: execútaa á man quen opera o despregue, unha conta cada vez e con tres esperas por concello como moito; se algunha vez chocase, a base aborta unha das dúas transaccións e repítese. Acéptase.

     Así ningunha tarxeta queda «En espera» por unha hora que xa non se pode reservar.
6. **Rutas.** A instalación vai sempre no camiño, coma en `…/availability`.

   | Ruta | Que |
   |---|---|
   | `GET /api/v1/municipalities/{slug}/me/booking-waits` | `[{id, facilityId, facilityName, cover, useId, useName, start, end}]` |
   | `POST …/me/booking-waits` con `{facilityId, useId, start}` | 201 (ou 200 se xa esperaba) coa espera |
   | `DELETE …/me/booking-waits/{id}` | 204; a doutra conta ou a que xa non existe, 404 `booking.wait-not-found` |
   | `GET …/public/facilities/{id}/similar-slots?useId=&start=&slots=` | Ocos parecidos, sen sesión (`@PublicEndpoint`, `@PublicUseCase`) **(PC4)** |
   | `GET …/me/facilities/{id}/similar-slots?useId=&start=&slots=` | Os mesmos, tamén coas normas da persoa (`@PersonUseCase`) |

   **Erros de `similar-slots`:** 404 `common.not-found` (a instalación non se ve ou o uso non é dela); 422 `booking.slot-invalid` (`start` non é un paso da grella dese uso); 400 de validación (`slots` fóra de 1–16). Unha hora pasada, libre ou sen alternativas responde 200 coa lista baleira. Na ruta `me`, conta de traballo ou bloqueada por faltas: 200 e lista baleira.

   Contrato e cliente TS rexéneranse ao construír ([ADR-0009](0009-contrato-api-cliente-xerado.md)).
7. **O aviso: cando, en que transacción e sen repetir.**
   - **Cando.** En todo caso de uso que solta ocupación: cancelar pola persoa (`ChangeMyBookingService.cancel`), cancelar desde a xestión (`ManageBookingService.cancel`), cancelar por un cambio na instalación (`BookingImpactService.apply`) e cambiar de hora, uso, parte ou prazas (persoa e persoal). Todos corren xa baixo o bloqueo da instalación.
   - **Na mesma transacción, despois de escribir:** unha chamada por caso de uso a `WaitNotifier.released(facilityId, ocupacións soltadas, ocupacións escritas)`.
     1. Le as esperas activas da instalación que pisan os períodos soltados: **unha consulta por índice; sen esperas, remata aí**, que é o caso normal.
     2. Se as hai, le a ocupación do día (estado final) e reconstrúe en memoria a de antes (a final, menos o escrito, máis o soltado). Calcula as dúas grellas.
     3. **Só avisa das franxas que pasan de ocupadas a libres** para o uso de cada espera. Nun uso por prazas ou por partes, unha segunda cancelación cando a hora xa tiña sitio non cambia nada e non avisa. Un cambio que deixa a hora igual de chea, ou un bloqueo ou peche que a quita, tampouco. Cobre a hora que ocupaba outro uso.
     4. Publica un evento por uso e franxa: `BookingSlotReleased(municipalityId, releaseId, facilityId, useId, start, end, releasedAt)`, cun `releaseId` (UUID) novo e `releasedAt` = o instante da transacción. Sen ningún dato de persoa (o rexistro de publicacións gárdao).
   - **Despois do commit,** `notification` escoita nun escoitador propio, `WaitMailListener` (`@Async`, `@TransactionalEventListener`, [ADR-0025](0025-reservas-fase-1.md) punto 2), e pide a `BookingQueries.waitNotices(useId, start, releasedAt)` os avisos que tocan. `booking` devolve `WaitNotice(waitId, email, language, facilityId, facilityName, facilityAddress, useId, useName, start, end)` en **orde aleatoria** (non hai quenda, tampouco pola orde de chegada) e deixa fóra:
     - todo, se a hora xa non está libre (colleuna alguén, ou é un reintento tardío);
     - as esperas vencidas e as creadas despois de `releasedAt` (son dunha ocupación posterior);
     - os correos con reserva confirmada dese uso nesa franxa (por `holder_email_key`; unha consulta para todos);
     - os correos con bloqueo por faltas vixente (unha consulta para todos) **(PC6)**.
   - **A todas á vez:** un correo a cada persoa na mesma pasada. Reserva a primeira que entre; as demais atopan a hora ocupada e a súa espera segue viva.
   - **Sen repetir** (`V202611101910__notification_slot_released.sql`): `notification_delivery` gaña `wait_id uuid`; `notification_delivery_kind_ck` admite `SLOT_RELEASED` (ademais dos que haxa); o destino pasa a `num_nonnulls(booking_id, invitation_id, wait_id) = 1`, con `(kind = 'SLOT_RELEASED') = (wait_id is not null)`; índice único parcial `(municipality_id, wait_id, event_id) where wait_id is not null`, con `event_id` = `releaseId`. Un reintento do mesmo evento non volve escribir a quen xa recibiu.
   - **Mínimo entre avisos (PC13):** `notification` non manda a unha espera que xa recibiu un aviso `SENT` hai menos de `furar.notification.wait-notice-gap` (unha consulta ao seu rexistro para todas). Corta o bucle de quen reserva e cancela a mesma hora unha e outra vez.
   - **Reintento.** `BookingSlotReleased` entra na lista `MAIL_EVENTS` de `MailRetry`: se queda sen completar, vólvese entregar aos minutos, coma os demais. Dentro dun evento, cada destinatario vai por separado: un envío que falla rexístrase `FAILED` e **non corta os demais**; ao rematar, se algún fallou, o escoitador lanza `MailNotSentException` e o evento queda pendente. O reintento só escribe a quen non ten `SENT`, e a un fallido só unha vez máis, a regra de sempre.
   - **Sen estorbar as confirmacións.** `WaitMailListener` ten o seu semáforo, dun só envío á vez, e cólleo por correo, non por evento. Os dous envíos de `BookingMailListener` quedan para confirmar, cambiar e cancelar: unha hora con moitas esperas non atrasa a confirmación de ninguén.
   - **Non avisan:** quitar un bloqueo, cambiar o horario ou subir as prazas dun uso. Sobre unha hora bloqueada ou pechada non hai esperas (punto 5), e subir prazas é raro; quen espera vería a hora libre ao entrar.
8. **Ao pasar a hora, a espera bórrase e queda o reconto.** `WaitExpiryService` (`@PlatformTask`, `@Scheduled(cron = "${furar.booking.wait-expiry-cron:0 5 * * * *}")` **(PC12)**, coma `IdempotencyKeyRetentionService`): en cada concello, con `TenantContext.runAs` e a súa RLS, **nunha soa transacción**:

   ```sql
   delete from booking_wait
   where slot_start <= :agora and municipality_id = :m
   returning facility_id, use_id, slot_start;

   -- Por cada uso e franxa borrados, con cantas esperas eran (en orde fixa):
   insert into booking_wait_tally (municipality_id, facility_id, use_id, slot_start, waits)
   values (:m, :instalación, :uso, :franxa, :cantas)
   on conflict do nothing;
   -- e, se a fila xa existía:
   update booking_wait_tally set waits = waits + :cantas
   where use_id = :uso and slot_start = :franxa and municipality_id = :m;
   ```
   `delete … returning` entrégalle cada espera a unha soa transacción, así que cada unha se conta unha vez, tamén se dúas pasadas se cruzan; o `insert` que non entra non aborta a transacción e o `update` agarda pola fila. Non vai nunha soa sentenza con CTE e `on conflict do update` (a primeira redacción) porque a garda do SQL nativo non a le: A8b e `NativeSqlRowLevelSecurityIT` toman o nome da CTE e o `set` do `do update` por táboas sen RLS; o resto do proxecto xa parte os *upsert* en dúas sentenzas polo mesmo. Idempotente; un fallo nun concello non para os demais; o rexistro técnico só leva cantas. O mesmo, con `and account_subject = :s`, corre ao crear unha espera (punto 4).
   - As lecturas filtran sempre `slot_start > agora`: unha espera vencida nunca se mostra nin se avisa, aínda que a tarefa tarde en borrala.
   - **Que conta o reconto (PC8):** as esperas vivas cando empeza a hora. Quen deixou de esperar, reservou esa hora ou perdeu a hora por un bloqueo non conta.
   - **Reconto anónimo:** uso, franxa e número; non se pode saber quen esperaba. A mesma tarefa borra as filas con máis de `furar.booking.wait-tally-retention` **(PC8)**. A súa lectura (`BookingStatistics`) e o gráfico «demanda non atendida» seguen sendo capa posterior ([ADR-0026](0026-indicadores-fase-1.md)).
9. **«Ocos parecidos»: por regras, sen IA, detrás dun porto.** Dentro de `booking`, sen módulo `recommendation`: precisa a grella, a ocupación e as normas, que son de `booking`.
   - **Porto** `booking.application.port.out.SlotRecommender.recommend(target, candidates, max)` → `List<SimilarSlot>`; **adaptador** `adapter.out.recommendation.RuleBasedSlotRecommender`, puro (sen base nin reloxo). `SimilarSlotsService` xunta os candidatos e o adaptador ordénaos: outro adaptador substitúeo sen tocar o servizo nin a API.
   - **Candidatos:** ocos libres (`FreeSlot`) nos que caben as franxas pedidas (`slots`, 1 se falta), xa filtrados polas normas. Sen sesión, as do uso (antelación e duración). Con sesión, tamén as da persoa: o límite por persoa no período de cada candidato e «unha praza por persoa», coas reservas confirmadas do seu correo lidas nunha consulta.
   - **«O mesmo uso» noutra instalación (PC2):** un uso vivo co mesmo nome (sen distinguir maiúsculas nin tiles) **e a mesma duración de franxa**, nunha instalación visible, activa e con reserva por internet. Cunha duración distinta non é «a mesma hora» e non conta.
   - **Regras, nesta orde (PC3)** (día e hora en Europe/Madrid; `D` e `S`, o día e a hora pedidos):

     | | `reason` | Que busca |
     |---|---|---|
     | 1 | `SAME_FACILITY_NEAR` | Esta instalación e uso, día `D`, a franxa anterior ou a seguinte a `S` (a `S` menos ou máis a duración da franxa do uso, dure o que dure: nun uso de 90 minutos, para as 18:30, as 17:00 ou as 20:00); se as dúas están libres, a anterior |
     | 2 | `OTHER_FACILITY_SAME_TIME` | Outra instalación co mesmo uso, libre a `S`; a primeira por nome |
     | 3 | `LATER_SAME_DAY` | Día `D`, a primeira hora libre despois de `S` que aínda non saíse: nesta instalación e uso; se non hai, nas outras co mesmo uso, pola hora máis temperá e, con empate, por nome |
     | 4 | `NEXT_WEEK` | Esta instalación e uso, `D` + 7 días, á mesma hora de reloxo |

     Unha regra sen resultado non deixa fila; sen ningunha, a sección non sae.
   - **Radio de busca:** o concello da petición (a RLS non deixa saír del), o día `D` e, só para a regra 4, `D` + 7.
   - **Custo:** lecturas fixas, sexan cantas sexan as instalacións, coma `ofAllFacilities` ([ADR-0030](0030-portada-comun.md)): usos por lote, tramos abertos de `D` por lote, a ocupación de `D` das instalacións co mesmo uso nunha consulta, tramos e ocupación de `D` + 7 só desta instalación e, con sesión, as reservas da persoa. Unhas 8 consultas, sen caché. Só se pide cando a persoa escolle unha hora ocupada.
   - **Resposta:** `{items: [{reason, facilityId, facilityName, facilityAddress, useId, date, start, end, startMinute}]}`; `facilityAddress` só se é outra instalación.
10. **Erros novos** (`BookingError`, texto en `messages/booking_{gl,es}.properties` e na web):

    | Código | HTTP | Galego | Castelán |
    |---|---|---|---|
    | `booking.wait-limit` | 422 | Podes esperar por {max} horas á vez. Deixa de esperar por unha para engadir outra. | Puedes esperar por {max} horas a la vez. Deja de esperar por una para añadir otra. |
    | `booking.wait-slot-free` | 409 | Esta hora xa está libre: resérvaa. | Esta hora ya está libre: resérvala. |
    | `booking.wait-own-booking` | 409 | Xa tes unha reserva a esa hora. | Ya tienes una reserva a esa hora. |
    | `booking.wait-not-found` | 404 | Xa non estás esperando por esta hora. | Ya no estás esperando por esta hora. |
11. **Correo «Quedou libre»** (o sexto das reservas; `MailKind.SLOT_RELEASED`). Mesma plantilla e mesmo renderizador que escapa; sen saúdo, sen QR, sen `.ics` e sen prazo de cancelación. Tarxeta coa instalación, o uso, o día e a hora. Botón «Reservar esta hora» → `‹web›/‹concello›/instalacions/‹id›/reservar?uso=&data=&hora=`: se segue libre, queda escollida; se xa a colleron, sae ocupada coa súa espera e os ocos parecidos. Porto novo `SendWaitMail` en `notification`. Texto fixo (`mail/notification_{gl,es}.properties`):

    | Clave | Galego | Castelán |
    |---|---|---|
    | `subject.SLOT_RELEASED` | Quedou libre: {0}, {1} ás {2} | Ha quedado libre: {0}, {1} a las {2} |
    | `title.SLOT_RELEASED` | Quedou libre a hora que esperabas | Ha quedado libre la hora que esperabas |
    | `lead.SLOT_RELEASED` | Quedou libre a hora pola que esperabas en {0}. | Ha quedado libre la hora por la que esperabas en {0}. |
    | `wait.first` | Avisamos a todas as persoas que esperaban: resérvaa a primeira que entre. | Hemos avisado a todas las personas que esperaban: la reserva la primera que entre. |
    | `button.reserveSlot` | Reservar esta hora | Reservar esta hora |
    | `footer.whyWait` | Recibes este correo porque pediches que te avisásemos se quedaba libre esta hora. | Recibes este correo porque pediste que te avisáramos si quedaba libre esta hora. |

    O asunto, coma o da falta: a instalación, o día da semana co día (sen o mes) e a hora («Quedou libre: Pista de pádel 1, mércores 11 ás 19:00»). `{0}` de `lead` é o nome do concello.
12. **Datos persoais** (fila en [`datos-persoais.md`](../datos-persoais.md)). Base 6.1.e ([ADR-0017](0017-datos-persoais.md) punto 3 xa conta as listas de espera).
    - **A espera** garda a conta, o correo e o idioma; bórrase cando a persoa deixa de esperar, o seu correo reserva esa hora, a hora deixa de existir ou a hora empeza.
    - **O envío do aviso** (`notification_delivery`, co correo) bórrase aos 7 días **(PC9)** (`furar.notification.wait-notice-retention`, en `NotificationRetentionService`): só serve para non repetir un reintento e para o mínimo entre avisos.
    - **Cruzamento que queda eses días:** a fila do envío leva o `releaseId` e o evento gardado en `event_publication` leva o mesmo `releaseId` co uso e a franxa. Quen ten acceso á base (quen opera o despregue, non o persoal nin a plataforma) pode saber a quen se avisou de que hora. Ao borrar o envío, o evento queda sen ningunha persoa.
    - **O reconto** non é dato persoal; consérvase o prazo do punto 8.
    - O persoal e a plataforma non ven as esperas. Non van ao rexistro de actividade (é do persoal, [ADR-0020](0020-rexistro-de-actividade.md)).
    - **Baixa da conta:** a migración das esperas amplía `close_account` ([ADR-0031](0031-persoas-a-cargo.md) punto 6) para borrar tamén as esperas desa conta, en todos os concellos.
    - **Textos que cambian ao construír** (`web/src/i18n/{gl,es}.json`, rexistro formal; pendentes da revisión legal xa anotada):

    | Clave | Galego | Castelán |
    |---|---|---|
    | `privacy.residents.purpose` (lista de correos) | (confirmación, cambio, cancelación ou falta dunha reserva, ou aviso de que quedou libre unha hora pola que pediu que se lle avisase) | (confirmación, cambio, cancelación o falta de una reserva, o aviso de que ha quedado libre una hora por la que pidió que se le avisara) |
    | `privacy.residents.dataWaits` (nova) | Das esperas: a instalación, o uso e a hora pola que pide que se lle avise se queda libre, coa súa conta e o seu correo. | De las esperas: la instalación, el uso y la hora por la que pide que se le avise si queda libre, con su cuenta y su correo. |
    | `privacy.retention.waits` (nova) | Esperas: ata que deixa de esperar, reserva esa hora ou a hora pasa. Despois só se conserva cantas persoas esperaban, sen identificalas. | Esperas: hasta que deja de esperar, reserva esa hora o la hora pasa. Después solo se conserva cuántas personas esperaban, sin identificarlas. |
13. **Horas.** Todo en `Instant`/`timestamptz` (UTC). En Europe/Madrid só se calculan a grella e «á mesma hora de reloxo» da regra 4; a franxa anterior e a seguinte da regra 1 son tempo real (vale co cambio de hora).
14. **Web: pezas** (sen pestana nin pantalla nova).

    | Onde | Que cambia |
    |---|---|
    | `features/booking/reserve/reserve-fields.component` e `styles/components/reserve.css` | O selector de hora mestura libres e ocupadas por hora. Variante nova do botón de hora (`.f-reserve__slot[data-busy]`): gris, con «Ocupada» debaixo, contraste AA, elixible; o seu nome accesible é o texto que se ve («19:00 Ocupada»), sen `aria-label` (un nome distinto do visible, coma «19:00, ocupada», incumpre WCAG 2.5.3 e detéctao axe). O campo «Hora» mostra a franxa e «· ocupada». Entrada `showBusy` (falsa no mostrador). Entrada no catálogo de compoñentes |
    | `features/booking/reserve/reserve.ts` e `reserve.store.ts` | `?hora=` pode ser unha hora ocupada; `chosen` sabe se o é. Sen total nin «Continuar» mentres o sexa |
    | `features/booking/reserve/wait-panel.component` (novo) e `reserve.page` | Cunha ocupada elixida: «Avísame se se libera» (primario); despois, aviso verde (`furar-callout`, `success`) e «Deixar de esperar» (`data-variant="danger"`, coma «Cancelar») |
    | `features/booking/reserve/similar-slots.component` e `similar-slots.store.ts` (novos) | «Ocos parecidos»: lista de `furar-row` (día e hora; debaixo, onde é). Pulsar unha déixaa elixida: nesta instalación cambia día e hora; noutra, leva á súa páxina de reservar co uso, o día e a hora postos |
    | `features/booking/waits/waits.store.ts` (novo; úsano reservar e as miñas reservas) | Listar, esperar e deixar de esperar; unha soa lectura por páxina |
    | `features/booking/mine/booking-card.component` e `my-bookings.page` | **Unha variante da mesma tarxeta,** non outro compoñente: `furar-booking-card` recibe unha reserva ou unha espera. Coa espera: instalación, día e hora, uso, chip «En espera» (`furar-tag`, ton `info`, o azul do kit) e «Deixar de esperar» (perigo). Sen prezo, QR, «Cambiar» nin «Cancelar». En «Próximas», mesturadas coas reservas por data |
    | `features/booking/manage/book-for.page` | Non cambia: o formulario do persoal segue mostrando só as horas libres **(PC5)** |
15. **Web: estados de pantalla.**

    | Situación | Que se ve |
    |---|---|
    | Sen sesión, hora ocupada elixida **(PC4)** | O panel enteiro e os ocos parecidos. «Avísame se se libera» leva a entrar e volve ao mesmo paso coa hora posta, coma «Continuar»; a persoa pulsa outra vez. Debaixo do botón, a frase de que hai que entrar |
    | Xa espera por esa hora (tamén ao chegar desde o correo) | O aviso verde e «Deixar de esperar» |
    | Bloqueada por faltas **(PC6)** | O aviso do bloqueo de [ADR-0032](0032-non-presentados-e-validacion.md), coma hoxe. As ocupadas vense en gris, pero sen «Avísame» nin «Ocos parecidos» |
    | Conta de traballo | Sen «Avísame» nin «Ocos parecidos» (a web xa sabe que é persoal). Se aínda así chega o 403, o erro no panel |
    | `booking.wait-limit` | `furar-problem-alert` dentro do panel, debaixo do botón, coa ligazón «Ver as miñas reservas» |
    | `booking.wait-slot-free` | Recárganse as horas; a hora queda elixida como libre e volve «Continuar», co texto do erro como aviso |
    | `booking.wait-own-booking` e o resto de 4xx | `furar-problem-alert` no panel |
    | «Ocos parecidos» cargando | Dúas filas de esqueleto (`furar-status-state`) |
    | «Ocos parecidos» falla | Unha liña de texto no sitio da lista; «Avísame» segue funcionando |
    | «Ocos parecidos» baleiro | A sección non sae |
    | «Deixar de esperar» e a API responde 404 (dúas pestanas, ou a hora xa pasou) | Éxito: a tarxeta ou o aviso desaparecen sen erro |
    | «Deixar de esperar» | Sen diálogo de confirmación **(PC11)** |
    | Modo «Cambiar» **(PC15)** | O mesmo selector e o mesmo panel: pódese esperar por outra hora sen tocar a reserva |
16. **Textos da web** (`web/src/i18n/{gl,es}.json`, baixo `booking`; os erros do punto 10 van coas demais claves de erro):

    | Clave | Galego | Castelán |
    |---|---|---|
    | `booking.reserve.busy` | Ocupada | Ocupada |
    | `booking.reserve.busyValue` (campo «Hora») | {{range}} · ocupada | {{range}} · ocupada |
    | `booking.reserve.wait.add` | Avísame se se libera | Avísame si se libera |
    | `booking.reserve.wait.signIn` | Para avisarte tes que entrar coa túa conta. | Para avisarte tienes que entrar con tu cuenta. |
    | `booking.reserve.wait.onTitle` | Avisarémoste por correo se se libera | Te avisaremos por correo si se libera |
    | `booking.reserve.wait.onText` | Reserva a primeira persoa que entre. | Reserva la primera persona que entre. |
    | `booking.reserve.wait.remove` | Deixar de esperar | Dejar de esperar |
    | `booking.reserve.wait.mine` | Ver as miñas reservas | Ver mis reservas |
    | `booking.reserve.similar.title` | Ocos parecidos | Huecos parecidos |
    | `booking.reserve.similar.when` (primeira liña da fila) | {{day}}, {{range}} | {{day}}, {{range}} |
    | `booking.reserve.similar.here` (segunda liña, mesma instalación) **(PC10)** | Esta mesma instalación | Esta misma instalación |
    | `booking.reserve.similar.other` (segunda liña, outra) | {{facility}} · {{address}} | {{facility}} · {{address}} |
    | `booking.reserve.similar.loading` | Buscando ocos parecidos… | Buscando huecos parecidos… |
    | `booking.reserve.similar.error` | Non puidemos buscar ocos parecidos. | No hemos podido buscar huecos parecidos. |
    | `booking.mine.wait.chip` | En espera | En espera |
    | `booking.mine.wait.remove` | Deixar de esperar | Dejar de esperar |
17. **Probas previstas.**
    - **Dominio (`*Test`):** `SlotGridTest` (`busySlots`: os tres modos, outro uso, bloqueo que corta, antelación, hora empezada, día con outro uso exclusivo ou uso non vixente → baleiro); `WaitNotifierTest` (só de ocupada a libre: segunda cancelación en prazas e en partes non avisa; cambio que deixa a hora chea non avisa); `RuleBasedSlotRecommenderTest` (cada regra, a orde, o empate, sen repetir, uso homónimo con outra duración fóra, cambio de hora na regra 4); `WaitLimitTest`; `NotificationMailTextsTest`.
    - **API (`*IT`):** `AvailabilityBusyIT` (`busy` sen datos de ninguén; só con `bookable`); `WaitsIT` (esperar; repetir → 200; hora libre → 409; a propia reserva; a cuarta → 422; **tres á vez sobre instalacións distintas → entran as tres;** catro á vez → tres e un 422; dúas á vez pola mesma hora → unha espera; outra conta → 404; sen correo verificado; conta de traballo; bloqueada por faltas; o correo do token substitúe o vello); `WaitRemovalIT` (reservar por internet borra a espera; **a reserva do mostrador a nome dese correo bórraa;** un cambio a esa hora bórraa; bloqueo, peche e uso arquivado bórrana sen reconto); `WaitNoticeIT` (cancelar pola persoa, pola xestión e por un cambio na instalación; cambiar de hora; a última praza e a última parte; **segunda cancelación en prazas non reavisa;** a hora que ocupaba outro uso; sen esperas, unha soa consulta; un bloqueo que cancela non avisa; a espera creada mentres se cancela, coas dúas ordes); `WaitMailIT` (a todas á vez; non repite co mesmo `releaseId`; non a quen xa ten reserva nesa franxa; non á espera creada despois de `releasedAt`; non se a hora xa se colleu; non á bloqueada; mínimo entre avisos; un destinatario que falla non corta os demais; **o reintento de `BookingSlotReleased` por `MailRetry` só escribe aos que faltan;** as confirmacións saen mentres hai avisos en cola); `WaitExpiryIT` (borra as pasadas, suma o reconto, dúas pasadas á vez non contan dobre, as vencidas non se listan antes da tarefa, o reconto vello bórrase); `NotificationRetentionIT` (os avisos de espera, ao seu prazo); `CloseAccountIT` (**`close_account` borra as esperas da conta**); `SimilarSlotsIT` (sen sesión e con ela: límite por persoa, praza propia; os erros; número de consultas fixo con 1 e con 30 instalacións); `BookingWaitSqlTenantIT` e `BookingWaitTallySqlTenantIT`; `SchemaTenancyIT` (as dúas táboas e as dúas FK en `CrossModuleForeignKeys`) e `NativeSqlRowLevelSecurityIT`; `OpenApiContractIT`.
    - **Web (`*.spec.ts`):** `reserve-fields.component.spec.ts` (ocupadas en gris, nome accesible, `showBusy`), `reserve.spec.ts`, `reserve.store.spec.ts`, `reserve.page.spec.ts` (os estados do punto 15), `wait-panel.component.spec.ts`, `similar-slots.component.spec.ts` e `similar-slots.store.spec.ts`, `waits.store.spec.ts` (404 ao deixar de esperar = éxito), `booking-card.component.spec.ts` (a variante de espera) e `my-bookings.page.spec.ts`.
    - **e2e:** os datos de demo levan unha hora ocupada fixa nun uso con outra instalación co mesmo uso. `reservas.e2e.ts`: hora ocupada → «Avísame» → sae en «Próximas» → «Deixar de esperar»; elixir un oco parecido e reservalo. Con axe en `gl`, `es` e `gl-mobile`.
    - A DoD non gaña filas.

## Consecuencias
- A persoa ve por que non pode reservar a hora que quería e ten dúas saídas na mesma pantalla: esperar ou coller un oco parecido.
- Unha cancelación custa unha consulta máis por índice; só cando hai esperas se calculan as grellas e saen correos, despois do commit e sen alongar o bloqueo.
- Con moitas persoas esperando a mesma hora, unha cancelación manda moitos correos e só unha persoa reserva: é o decidido (sen quenda); o correo dío.
- Esperar toma o bloqueo da instalación un instante: compite coas reservas desa instalación, sen escribir ocupación.
- `notification_delivery` ten un terceiro destino (a espera) e o seu primeiro prazo curto; `notification` ten un segundo escoitador co seu semáforo.
- `BookingImpact.apply` chámase con todo cambio da instalación, tamén sen reservas afectadas.
- Queda gardado o dato para «demanda non atendida» desde o primeiro día, aínda que o panel non o mostre.
- Non hai módulo `recommendation` nin dependencia nova entre módulos; `arquitectura.md` xa o di.
- Dúas instalacións co mesmo deporte pero usos con nomes ou duracións distintos non se propoñen unha á outra: depende de como nomee os usos cada concello.

## Preguntas abertas
Ningunha de produto. A comprobación técnica que quedaba (se `AffectedBookingsGate` chama a `apply` cando non hai reservas afectadas) fíxose ao construír: non o facía; está no punto 5.

## Decidido polo mantedor sobre as preguntas abertas (2026-11-10)
O mantedor aceptou o valor proposto en todas; xa van na decisión.

| | Pregunta | Decisión |
|---|---|---|
| PC1 | Nun uso por partes, espérase por unha parte concreta | Non: espérase polo uso e a hora, e só se non queda ningunha parte libre |
| PC2 | Que é «o mesmo uso» noutra instalación | Un uso co mesmo nome (sen maiúsculas nin tiles) e a mesma duración; a alternativa sería o mesmo deporte da instalación |
| PC3 | Regras de «ocos parecidos»: cantas filas e detalle | Unha fila por regra, 4 como moito; a primeira regra é a franxa anterior ou a seguinte do mesmo uso na mesma instalación, dure o que dure, e coas dúas libres proponse a anterior (decidido polo mantedor o 2026-11-11; o valor de antes, «±1 h» = 60 minutos, non daba fila nos usos con franxas de máis dunha hora); «máis tarde ese día», primeiro nesta instalación e, se non hai, noutra |
| PC4 | Sen sesión | Vense as ocupadas e os ocos parecidos (só coas normas do uso); «Avísame» pide entrar e a persoa pulsa outra vez ao volver |
| PC5 | No mostrador | O formulario do persoal non cambia (só horas libres) e o persoal non ve nin xestiona esperas |
| PC6 | Persoa bloqueada por faltas | Non pode esperar nin ve ocos parecidos, e non se lle avisa mentres dure; as esperas que tiña quedan |
| PC7 | Persoa co límite por persoa do uso cheo | Pode esperar; se ao liberarse segue chea, reservar dálle o erro de sempre |
| PC8 | Que conta o reconto, canto se garda e onde se ve | As esperas vivas ao empezar a hora; 5 anos (`P5Y`); o gráfico do panel segue como capa posterior |
| PC9 | Canto se garda o envío do aviso | 7 días (`P7D`), non o ano dos demais correos |
| PC10 | Texto da fila na mesma instalación («Esta mesma pista» ou máis xeral) | «Esta mesma instalación»: vale tamén para un campo ou unha piscina |
| PC11 | «Deixar de esperar» pide confirmación | Non: un toque, e pódese volver esperar |
| PC12 | Cada canto se borran as esperas pasadas | Cada hora; mentres, non se mostran nin se avisan |
| PC13 | Mínimo entre dous avisos á mesma espera | 30 minutos (`PT30M`) |
| PC14 | A hora esperada queda bloqueada ou pechada, ou o uso arquívase | A espera bórrase sen avisar á persoa e sen contar |
| PC15 | En modo «Cambiar» | Tamén se pode esperar por outra hora; a reserva non se toca |
| PC16 | Esperar por unha hora na que xa se ten reserva dese uso | Non se pode (409) |

## Alternativas descartadas
- **Quenda (avisar á primeira e esperar a súa resposta) ou reserva automática:** pide prazos, caducidades e un estado «reservada á espera de confirmar»; a decisión é avisar a todas e que reserve a primeira.
- **Marcar a hora ocupada dentro de `slots[]` (un `status` por oco):** os clientes que hoxe len `slots` como «libres» (ficha, listado, portada) ofrecerían horas ocupadas; `busy` á parte non rompe nada.
- **Dicir por que está ocupada (o mesmo uso, outro uso, cantas prazas):** non fai falta para esperar e conta máis do preciso.
- **Espera por varias franxas seguidas:** multiplica as claves e as comprobacións ao avisar para un caso que a persoa resolve escollendo a hora que lle falta.
- **Espera sen conta, só cun correo:** sen correo verificado calquera podería apuntar o correo doutra persoa, e non habería onde «deixar de esperar».
- **Crear a espera sen o bloqueo da instalación:** deixa unha xanela na que a hora se libera e a espera recén creada non recibe aviso.
- **Contar o límite cun `count(*)` no código:** dúas peticións á vez pasarían de 3.
- **Escoller o ordinal no código e inserir sen `on conflict`** (primeira versión desta ADR): dúas peticións á vez da mesma conta en instalacións distintas escollían o mesmo, o `23505` abortaba a transacción e daba un `booking.wait-limit` falso, sen distinguirse da espera repetida.
- **Bloqueo consultivo por conta (`pg_advisory_xact_lock`) e contar:** funciona, pero o límite queda no código e non nunha restrición visible ([ADR-0012](0012-ocupacion-exclusiva-e-prazas.md) descartouno polo mesmo).
- **Avisar de toda franxa que queda libre ao final da transacción** (primeira versión): nun uso por prazas cada cancelación volvería avisar a todas aínda que a hora xa tivese sitio.
- **Avisar dentro da transacción da cancelación:** un SMTP lento alongaría o bloqueo da instalación e un SMTP caído desfaría a cancelación ([ADR-0025](0025-reservas-fase-1.md)).
- **Un evento por persoa que espera:** o rexistro de publicacións gardaría correos; un evento por franxa non leva datos de ninguén.
- **Os avisos polo mesmo semáforo ca as confirmacións:** unha hora con cincuenta esperas atrasaría o correo de quen acaba de reservar.
- **Deduplicar cunha marca na propia espera (`notified_at`):** `notification` tería que escribir nunha táboa de `booking`; o seu rexistro de envíos xa distingue un reintento dunha segunda liberación.
- **Borrar a espera só pola conta (`sub`) ao reservar:** a reserva do mostrador a nome do mesmo correo deixaría a persoa «En espera» por unha hora que xa ten.
- **Deixar viva a espera dunha hora bloqueada ou dun uso arquivado ata que pase:** a tarxeta diría «En espera» por algo que non pode liberarse, e contaría como demanda non atendida.
- **Gardar o envío do aviso un ano, coma os demais:** deixaría o correo de quen esperou cando a espera xa se borrou.
- **Contar as esperas pasadas cun `select` e borralas despois:** unha espera creada ou borrada entre as dúas sentenzas contaríase mal; `delete … returning` devolve exactamente as que borra.
- **Borrar as esperas cunha tarefa diaria:** vivirían ata un día de máis.
- **Gardar a espera vencida «anonimizada» en vez dun reconto:** unha fila por persoa, aínda sen nome, é máis do que pide o indicador.
- **Outro compoñente para a tarxeta de espera:** duplicaría a de reserva; unha variante mantén un só aspecto.
- **Módulo `recommendation` á parte:** tería que ler a grella, a ocupación e as normas pola API pública de `booking`, que habería que abrir só para el; o porto dentro de `booking` dá a mesma substitución.
- **Recomendación con IA ou cun servizo externo:** a decisión é por regras; o porto deixa cambialo sen tocar a API.
- **«Horas tranquilas» segundo a ocupación pasada e chips «Que é o máis importante para ti?»:** descartado por agora; capa posterior.
- **Buscar ocos parecidos en varios días ou en todos os usos do mesmo deporte:** o custo crece cos días e as instalacións; un día e unha semana despois dan as catro filas con lecturas fixas.
- **Calcular os ocos parecidos en cada carga das horas:** case nunca se usarían; pídense só ao elixir unha ocupada.
