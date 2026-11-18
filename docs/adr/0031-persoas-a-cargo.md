<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0031 · Persoas a cargo: fichas na conta e unha reserva con N prazas

- **Estado:** aceptada · 2026-11-10 (decidida polo mantedor) · construída o 2026-11-10 (API, web e probas, tamén as de punta a punta no navegador)
- **Fonte de produto:** [especificación](../produto/especificacion.md), bloque 5 («Persoas a cargo»), decidido o 2026-11-10.
- **Revisa:** [ADR-0012](0012-ocupacion-exclusiva-e-prazas.md) («unha praza por persoa», `seats = 1`), [ADR-0025](0025-reservas-fase-1.md) punto 10 e [ADR-0017](0017-datos-persoais.md) punto 4 (o nome dunha persoa menor na reserva).
- **Revisada 2026-11-10 (decisión do mantedor, «a reserva é do correo»):** a reserva do mostrador queda a nome do correo que dá a persoa; se ese correo ten conta, é súa desde ese momento e, se non, pasa á súa conta cando entre con el verificado (punto 7). O titular identifícase polo correo en «unha praza por persoa» (punto 5). Revisa tamén a «reserva por outra persoa» do 2026-11-04 ([ADR-0025](0025-reservas-fase-1.md)).
- **Revisada 2026-11-10 (revisión do deseño antes de construír):** o titular só leva fila se ocupa praza; onde e en que transacción se ligan as reservas (punto 7); que ve a conta ligada; corrección dun correo mal escrito; anonimización; límite e pegada das lecturas do mostrador; purga de `account_email`; textos da web (punto 14).
- **Revisada 2026-11-10 (respostas do mantedor ás preguntas abertas):** as reservas do mostrador tamén levan «Para quen» (punto 7); unha ficha con reservas non se borra, desactívase (punto 6); o resto dos valores propostos quedan decididos. O que segue aberto vai en [«Preguntas abertas»](#preguntas-abertas).
- **Revisada 2026-11-12 ([ADR-0040](0040-buscar-xente-para-xogar.md)):** nas partidas abertas, quen se une non vai a `booking_participant`: non ocupa praza nin ten reserva.

- **Revisada 2026-11-11 (preferencias de accesibilidade na conta):** a conta garda tamén o tamaño do texto, o alto contraste e o menos movemento (`account_display_preference`, táboa de plataforma sen concello coma `account_dependent`; `GET`/`PUT /api/v1/me/display-preferences`, 204 se nunca se gardaron; `null` nun interruptor = segue o sistema). Poden revelar unha discapacidade: só eses tres valores e cando cambiaron, sen historial; non van ao rexistro de actividade nin ao técnico; `close_account` bórraas; entrarán na descarga dos datos da persoa cando exista ([`datos-persoais.md`](../datos-persoais.md)).

- **Revisada 2026-11-10 (ao construír; correccións técnicas, sen cambiar o decidido):**
  - As migracións son `V202611101700__people_account_dependent.sql` e `V202611101710__booking_participants.sql`.
  - `booking_participant` leva unha columna máis, `replaced boolean not null default false` (`check (not (replaced and active))`): un cambio da reserva deixa as persoas de antes inactivas **e substituídas**; cancelar só as deixa inactivas. Sen ela non se sabería que persoas levaba unha reserva cancelada que antes se cambiara. «Para quen» son as filas sen substituír.
  - A FK `(municipality_id, booking_id, use_id)` cara a `booking` leva `on update cascade`: ao cambiar de uso, as filas vellas (xa inactivas) seguen a reserva.
  - `VerifiedAccounts.seen` e `PersonArrival.arrived` chámaas `ActorPreloadInterceptor` (ten o token e corre antes do controlador e fóra de toda transacción), non `JwtCurrentActor`; o interceptor cobre tamén `/api/v1/me/**`. Un token sen `auth_time` non escribe.
  - `account_email` escríbese cun `update … where seen_at < :seenAt` e un `insert … on conflict (account_subject) do nothing`, no canto dun `on conflict do update`: é o mesmo resultado e a regra A8b le as dúas sentenzas.
  - Eliminar unha ficha bloquea primeiro a súa fila (`for update`) e pregunta despois a `DependentUsage`: unha reserva que a estea usando nese momento ou xa confirmou (a ficha desactívase) ou espera e recibe `booking.dependent-unknown`. Así o `23503` ao borrar non chega a darse.
  - As respostas levan, en cada persoa a cargo de `participants[]`, o `dependentId` da súa ficha: fai falta para volver marcala ao cambiar a reserva.
  - Ao cambiar sen `participants`, a reserva queda coas mesmas persoas (o «sen o campo, `{self: true}`» vale só ao reservar). Cando cambia a propia persoa, os acompañantes que escribiu o persoal quedan: ela só marca «Eu» e as súas fichas.
  - `booking.dependents-read` conta as lecturas boas da última hora; a pegada vai en `details.emailHash`.
- **Revisada 2026-11-12 ([ADR-0039](0039-tempada-de-entidades.md)):** unha reserva pode ter de titular unha entidade (`organization_id`), sen conta nin correo: «a reserva é do correo» non a toca (`PersonArrival` filtra por `holder_email_key`).

## Contexto
Hoxe nun uso por prazas cada reserva é unha praza para quen ten a conta, e a base impide que a mesma conta teña dúas no mesmo uso e franxa (`booking_one_seat_per_person`). Unha nai que leva dúas crianzas á piscina non pode reservar por internet: ten que ir ao mostrador, onde tamén son tres reservas. As persoas menores de 14 anos non teñen conta ([ADR-0017](0017-datos-persoais.md) punto 4). Hai que deixar reservar para outras persoas sen pedir máis datos dos precisos (RGPD art. 5.1.c), sen perder «ninguén ten dúas prazas na mesma franxa», sen multiplicar reservas, códigos e QR, e deixando constancia de que conta reservou para que persoa.

## Decisión
1. **Fichas gardadas na conta.** Unha persoa a cargo é unha ficha da conta: nome e apelidos, **ano** de nacemento e a declaración «Esta persoa está ao meu cargo» (obrigatoria ao crear e ao editar; gárdase a data). Vale calquera persoa a cargo, menor ou adulta, sen xustificar a relación. **Sen DNI, sen parentesco e sen datos de saúde.** Se un trámite esixe o DNI, pídeo ese concello como campo personalizado da reserva ([ADR-0011](0011-campos-personalizados.md)); Furar tampouco garda o NIF de quen ten a conta ([ADR-0019](0019-autenticacion-publica-clave.md)). Como moito **10 fichas activas** por conta; non pode haber dúas activas co mesmo nome e ano; o ano vai do actual menos 120 ao actual.
2. **As fichas son da conta, non dun concello: táboa de plataforma.** A conta é común a todos os concellos ([ADR-0017](0017-datos-persoais.md) punto 2): a persoa escribe as fichas unha vez e reserva con elas en calquera.
   - Táboa `account_dependent`, **sen `municipality_id`**, na lista pechada `TenancyAllowlist.TABLES` (coma `platform_admin`): non leva a política `<táboa>_tenant` porque non hai concello polo que filtrar.
   - Toda lectura e escritura leva `account_subject = :s` (`DependentSql`, `@NativeSql(GLOBAL)` co seu motivo). O `sub` é o do token; a única excepción é o mostrador (punto 7), onde sae do correo que escribe o persoal. A plataforma non a le.
   - Responsable: o da conta común ([ADR-0017](0017-datos-persoais.md) punto 2, pendente da revisión legal). Cada concello é responsable da copia que queda nas súas reservas e do acceso do seu persoal no mostrador.
   - **Portabilidade** ([ADR-0018](0018-portabilidade.md)): as fichas non entran en `furar-export`, coma as contas de Keycloak (o `sub` só vale neste despregue). Os participantes viaxarán coas reservas cando entren na exportación, co nome copiado e sen a ligazón á ficha.
3. **Módulo novo `people`,** creado con `scripts/novo-modulo.sh` e reducido (sen entidade JPA nin sección de exportación, coma `staff`). É o módulo que a arquitectura reserva para o perfil da veciñanza. `people → shared`; `booking → people`:
   - API pública `PeopleQueries.dependents(subject)` e `dependents(subject, ids)` → `DependentSummary(id, name, birthYear)`, só as activas.
   - `PeopleQueries.accountByEmail(email)` → o `sub` da conta que entrou con ese correo verificado, se a hai (punto 7).
   - SPI `people.DependentUsage.isUsed(dependentId)`, que implementa `booking` (a dependencia invértese cun porto, [ADR-0005](0005-monolito-modular-hexagonal.md); sen ningún, `NoDependentUsage`). Le todos os concellos cunha función `security definer` que só di si ou non, `dependent_has_bookings(uuid)`, coma `has_resident_bookings`.
   - `ModularityTest` leva a dependencia nova.
4. **Modelo da ficha** (`V202611101200__people_account_dependent.sql`; a hora axústase á do día en que se cree):

   ```sql
   create table account_dependent (
     id uuid primary key,
     account_subject text not null check (length(account_subject) between 1 and 255),
     name text not null check (length(name) between 1 and 200),
     birth_year int not null check (birth_year between 1900 and 2200),
     declared_at timestamptz not null,           -- «Esta persoa está ao meu cargo»
     deactivated_at timestamptz,                 -- «eliminada» pola persoa, pero con reservas
     created_at timestamptz not null, updated_at timestamptz not null, version bigint not null
   );
   create index account_dependent_account_idx on account_dependent (account_subject);
   create unique index account_dependent_person_uk
     on account_dependent (account_subject, lower(name), birth_year) where deactivated_at is null;
   ```
   **Contas coñecidas polo correo** (mesma migración): para saber se un correo ten conta sen preguntarlle ao sistema de acceso, Furar garda o que xa lle chega no token.

   ```sql
   create table account_email (             -- de plataforma, en TenancyAllowlist
     account_subject text primary key check (length(account_subject) between 1 and 255),
     email text not null unique check (length(email) between 3 and 254),   -- normalizado
     seen_at timestamptz not null
   );
   ```
   - Escríbea `people` pola SPI `shared.security.VerifiedAccounts.seen(subject, verifiedEmail, authTime)`. Chámaa `JwtCurrentActor`, que é quen ten o token (`Actor` non leva o `auth_time`), na mesma lectura previa ao controlador en que pide os roles do persoal (`ActorPreloadInterceptor`, [ADR-0027](0027-persoal-e-permisos-fase-1.md) punto 2): fóra de toda transacción do caso de uso, nunha transacción curta propia e só se `email_verified` é verdadeiro. Unha escritura por sesión: `delete from account_email where email = :e and account_subject <> :s` e `insert … on conflict (account_subject) do update set email = excluded.email, seen_at = excluded.seen_at where account_email.seen_at < excluded.seen_at`. Se dúas sesións chocan no `unique (email)` (`23505`), reinténtase unha vez; se volve fallar, a petición segue sen escribir (o directorio é unha axuda, non autoriza nada).
   - **Unha soa normalización do correo:** `shared.domain.EmailKey.of` (a regra do persoal, `strip()` e minúsculas con `Locale.ROOT`, que sae de `staff.domain.EmailAddress`). Úsana `staff`, `people` e `booking`, e é a única que escribe `account_email.email`, `booking.holder_email_key` e o `e:` de `person_key`. A base non normaliza por si: `lower(btrim())` non é idéntico.
   - **Purga:** `DependentRetentionService` borra as filas con `seen_at` de hai máis de `furar.booking.retention` (2 anos, o prazo das reservas): unha baixa esquecida non deixa o mostrador ligando a unha conta morta, e unha conta viva volve escribirse en canto entra.
   - Unha conta de traballo non conta como conta para reservar ([ADR-0027](0027-persoal-e-permisos-fase-1.md) punto 16): `booking` descártaa con `StaffAccounts`.
5. **Unha reserva con N prazas e os seus participantes.** Nun uso por prazas a reserva leva de 1 a **11** persoas: quen ten a conta (se se marca «Eu») e as persoas a cargo escollidas. Pódese reservar só para unha persoa a cargo: quen ten a conta segue sendo o titular (correo, QR, cancelar), sen praza. **O titular só leva fila de participante se ocupa praza** (`self = true`): sen ela, pode reservar despois esa mesma hora para si. Na API o participante titular chámase `SELF`; na táboa, `HOLDER`. **`booking.seats` é o número de participantes activos da reserva.** **Un código e un QR;** valídase, cámbiase, cancélase e conta como falta enteira ([ADR-0032](0032-non-presentados-e-validacion.md)). No **límite por persoa** conta como unha reserva do titular.
   - `booking.seats int not null default 1` e `booking_occupancy.seats`: o `check (seats = 1)` pasa a `check (seats between 1 and 11 and (rental_mode = 'SEATS' or seats = 1))`. `use_slot.taken` sobe e baixa `seats` dunha vez; o `CHECK (taken between 0 and capacity)` segue impedindo pasar do cupo.
   - `amount_cents` = importe por persoa × franxas × prazas, conxelado coma hoxe.
   - Táboa nova do concello `booking_participant` (`V202611101210__booking_participants.sql`), con RLS e a política `booking_participant_tenant` na mesma migración:

   ```sql
   create table booking_participant (
     id uuid primary key,
     municipality_id uuid not null references municipality (id),
     booking_id uuid not null,
     use_id uuid not null, period tstzrange not null,     -- copia da reserva, para a exclusión (ver abaixo)
     kind text not null check (kind in ('HOLDER', 'DEPENDENT', 'COMPANION')),
     person_key text not null,        -- 'e:<correo do titular>' · 'd:<id da ficha>' · 'w:<id da fila>'
                                      -- (sen correo ou acompañante) · 'x:<id da fila>' (anonimizada)
     dependent_id uuid references account_dependent (id),  -- a mesma persoa a cargo en todas as súas reservas
     name text, birth_year int,       -- copia: DEPENDENT e COMPANION, ata a anonimización
     position int not null,
     active boolean not null default true,                -- false ao cancelar ou cambiar
     constraint booking_participant_tenant_uk unique (municipality_id, id),
     foreign key (municipality_id, booking_id, use_id) references booking (municipality_id, id, use_id),
     check ((dependent_id is not null) = (person_key like 'd:%')),
     check (dependent_id is null or kind = 'DEPENDENT'),
     check ((kind <> 'HOLDER' and person_key not like 'x:%') = (name is not null and birth_year is not null)),
     check (person_key not like 'x:%' or not active),
     -- unha praza por persoa: a mesma persoa, titular ou a cargo, non ten dúas no mesmo uso e franxa
     constraint booking_participant_one_seat
       exclude using gist (use_id with =, person_key with =, period with &&) where (active)
   );
   create index booking_participant_dependent_idx on booking_participant (dependent_id);
   ```
   - **A exclusión `booking_one_seat_per_person` de `booking` bórrase** na mesma migración e substitúea `booking_participant_one_seat`: a garantía pasa de «por conta» a «por persoa». **O titular identifícase polo correo** (`e:<correo normalizado>`), teña conta ou non: a reserva é do correo, e así a mesma persoa non ten dúas prazas entre o mostrador e internet desde o primeiro momento, sen ter que cambiar a clave cando a reserva pasa á súa conta. Sen correo (`w:`) non hai exclusión entre reservas. A migración crea unha fila `HOLDER` por cada reserva por prazas xa feita e sen anonimizar (`e:` se ten correo, `w:` se non; activa se está confirmada); se dúas reservas vellas do mesmo correo se pisan, a máis antiga leva `e:` e as outras `w:`.
   - **A reserva queda ligada á ficha** (`dependent_id`), ademais de copiar o nome e o ano: sábese que a conta X reservou para a mesma persoa Y tal día aínda que a ficha cambie de nome ou se desactive. É unha FK a unha táboa doutro módulo: `account_dependent` entra na lista pechada `CrossModuleForeignKeys` como táboa publicada de `people`. A FK impide na base borrar unha ficha con reservas, en calquera concello.
   - O nome do titular non se duplica: sae de `booking.holder_name`.
   - **Copias á par da reserva:** `use_id` vai por FK composta (`booking` gaña `unique (municipality_id, id, use_id)`); `period` e `active` escríbeos `booking` na mesma transacción en que reserva, cambia ou cancela, e `BookingParticipantsConsistencyIT` comproba despois de cada operación que todo participante activo ten o período da súa reserva, que esta está confirmada e que `seats` é o seu número. O límite de 10 fichas vixíao a aplicación (conta baixo `for update` das filas da conta), non a base.
6. **Eliminar unha ficha: borrar se nunca se usou, desactivar se ten reservas.**
   - `DELETE /api/v1/me/dependents/{id}` pregunta a `DependentUsage.isUsed`: **sen ningunha reserva** (en ningún concello nin estado), a fila bórrase; **con reservas**, ponse `deactivated_at`. Nos dous casos responde 204 e para a persoa é o mesmo: a ficha deixa de saír na súa lista e en «Para quen», e non se pode usar nin editar (404). Se entre a pregunta e o borrado se crea unha reserva, a FK dá `23503` e a ficha desactívase.
   - As reservas feitas non cambian: seguen co nome copiado e ligadas á ficha. As futuras mantéñense; para quitar a persoa, «Cambiar».
   - **Conservación:** a ficha desactivada consérvase mentres se conserve algunha das súas reservas. Cando `BookingRetentionService` anonimiza unha reserva (2 anos; as de pago, 5; [ADR-0017](0017-datos-persoais.md) punto 5), todos os seus participantes, tamén o `HOLDER` co seu `e:<correo>`, quedan con `person_key = 'x:<id>'`, `active = false` e `name`, `birth_year` e `dependent_id` a nulo, na mesma transacción; `holder_email_key` vai a nulo co correo. A tarefa diaria `DependentRetentionService` (`@PlatformTask`, en `people`, coma as demais de conservación) borra as fichas desactivadas que xa non usa ningunha reserva.
   - **Baixa da conta:** as fichas sen reservas bórranse e as que as teñen quedan desactivadas co mesmo prazo. A API non sabe cando se borra unha conta en Keycloak: é un **paso obrigatorio do procedemento de baixa** ([`datos-persoais.md`](../datos-persoais.md), «Dereitos»). Quen atende a baixa é quen opera o despregue (a mesma persoa que borra a conta na consola do sistema de acceso e ten o acceso de dono á base; o persoal do concello non pode): executa, antes de borrar a conta, `select close_account('<sub>')`, función que crea a migración dos participantes e que o rol da API non pode chamar; borra tamén a fila da conta en `account_email`. As reservas quedan co correo e seguen a súa conservación.
   - **Que ve o persoal** nunha reserva dunha ficha desactivada: o mesmo ca antes, o nome e o ano copiados, sen ningunha marca; cando a reserva se anonimiza, «Persoa anonimizada» e só o número de prazas.
7. **No mostrador, a reserva é do correo** (decisión do 2026-11-10; revisa a «reserva por outra persoa» do 2026-11-04). O formulario (`booking.book-for`) pide nome e apelidos e **correo**, máis o teléfono, coma hoxe. A reserva queda a nome dese correo e a persoa recibe a confirmación de sempre. Quen a fixo segue constando (`booked_by`, «Reservada por …»).
   - **Modelo** (na migración dos participantes): `booking.holder_email_key text`, que escribe a aplicación con `EmailKey.of` (`check ((holder_email_key is null) = (holder_email is null))`), con índices `(municipality_id, holder_email_key, lower(period))` e `(municipality_id, holder_email_key) where holder_subject is null`. A migración éncheo unha vez con `lower(btrim(holder_email))`. Bórrase `booking_booked_by_holder_ck`: unha reserva do persoal pode ter conta. `holder_subject` pasa a ser «a conta ligada»: nula mentres o correo non teña conta.
   - **Se o correo xa ten conta** (`PeopleQueries.accountByEmail`), a reserva nace con `holder_subject`: é súa desde ese momento. Sae en «As miñas reservas», cámbiaa e cancélaa coas normas de sempre, conta no seu límite por persoa e nas súas faltas ([ADR-0032](0032-non-presentados-e-validacion.md)), e o correo leva «Ver a miña reserva».
   - **Se non hai conta, non se crea ningunha.** A reserva queda con `holder_subject` nulo e o correo; o correo de confirmación vai sen as ligazóns a «as miñas reservas», coma hoxe.
   - **Cando a persoa entra con ese correo verificado, as reservas pasan á súa conta soas.** Non se fai dentro dos casos de uso da persoa (os de lectura son `readOnly`). SPI `shared.security.PersonArrival.arrived(subject, verifiedEmail)`, que implementa `booking` e chama `ActorPreloadInterceptor` antes do controlador, só nas rutas da persoa no concello (`…/municipalities/{slug}/me/…`) e co correo verificado: nunha transacción curta propia, coa RLS do concello, unha lectura sobre o índice parcial e, só se hai algo, `update booking set holder_subject = :sub where municipality_id = :m and holder_subject is null and anonymized_at is null and holder_email_key = :correo and not is_active_staff_account(:sub)` (a función que xa separa a conta de traballo). Repetilo non cambia nada. Cada reserva ligada deixa unha fila `LINKED` en `booking_event` (quen: a propia persoa); **non vai ao rexistro de actividade**, que é do persoal. Faise concello a concello, cando a persoa o visita: ningunha escritura cruza concellos. O mesmo camiño liga as reservas do mostrador feitas antes desta decisión.
   - **Que ve a conta ligada:** o mesmo que xa leva o correo de confirmación e nada máis. `MyBookingResponse` hoxe non leva nome, correo nin teléfono do titular; leva instalación, uso, parte, franxa, estado, pago, prezo, código e QR, e os campos da reserva que ve «quen a fixo». Nunha reserva do mostrador (`booked_by`) eses campos van baleiros (escribiunos o persoal e non van no correo), e «Para quen» leva os mesmos nomes ca o correo. Pódea cambiar e cancelar coas normas de sempre.
   - **Correo mal escrito.** O correo de confirmación dunha reserva do mostrador gaña a liña `note.notYours`: «Se esta reserva non é túa, avisa ao servizo de deportes: {0}.» / «Si esta reserva no es tuya, avisa al servicio de deportes: {0}.» (co teléfono do servizo; sen teléfono, o seu correo). O persoal corríxeo en «Cambiar»: na mesma transacción recalcúlanse `holder_email_key`, o `person_key` do titular e `holder_subject` (a conta do correo novo, ou nulo se non a ten), e queda unha fila `HOLDER_CHANGED` en `booking_event` (sen correos na fila). Se a reserva tiña unha falta, pasa a contar para o correo novo; se causara un bloqueo ao correo equivocado, levántase nese momento ([ADR-0032](0032-non-presentados-e-validacion.md) punto 7) e a ficha avisa: «Levantouse o bloqueo do correo anterior.» Unha falta que non debeu ser quítaa o persoal con «Desfacer».
   - **Conta de traballo:** un correo dunha conta de traballo non se liga nunca; no mostrador dá 422 `booking.work-account-email` (pídese o correo persoal ou resérvase sen correo).
   - **Na demo,** a identidade simulada confía no correo que escribe a persoa (`trustEmail`, [ADR-0019](0019-autenticacion-publica-clave.md)): alí pódense reclamar reservas de calquera correo. É só da demo; en produción ese provedor non existe.
   - **O límite por persoa e as faltas cóntanse por correo** (`holder_email_key`), así valen igual antes e despois de ligar. O persoal pode reservar aínda que a persoa pase do límite (decidido o 2026-11-10).
   - **«Para quen» con conta:** `POST …/bookings/holder-dependents` con `{email}` (ou `{bookingId}` ao cambiar) → `[{id, name, birthYear}]`, as fichas activas. O correo vai no corpo, non no URL; o `sub` nunca sae na resposta. Sen conta ou sen fichas, a mesma lista baleira: o persoal só sabe do correo que el mesmo escribe, e ningunha resposta di «este correo ten conta».
   - **Mínima exposición:** permiso `booking.book-for` (Xestión e Recepción), só nese formulario e só da persoa escollida; non hai lista nin busca de fichas; o persoal non as crea, edita nin elimina. Cada lectura deixa `booking.dependents-read` no rexistro de actividade **antes de devolver nada** (sen rexistro non hai lista), con cantas e `emailHash`, a pegada con clave do correo consultado (HMAC-SHA256 con `FURAR_AUDIT_HASH_SECRET`, un segredo do despregue de 32 caracteres ou máis sen o que `prod` non arrinca): permite saber despois se alguén consultou un correo concreto sen gardar o correo. Como moito 30 lecturas por persoa do persoal e hora, contadas no propio rexistro; pasado, 429 `booking.dependents-rate-limited` con `Retry-After`. Ao gardar, `booking` volve resolver a conta e rexeita as fichas que non sexan dela.
   - **Acompañantes (decidido o 2026-11-10):** sen conta, ou ademais das fichas, o persoal escribe o nome e o ano de nacemento de cada acompañante só para esa reserva, varios se fai falta, sen crear fichas. Quedan en `booking_participant` co nome copiado e sen ficha ligada (`COMPANION`, `w:<id>`). Vale o máximo de 11 prazas e «unha praza por persoa» **dentro da reserva**: dous participantes co mesmo nome e ano, 422 `booking.participant-duplicate`.
   - **As persoas a cargo e os acompañantes nunca levan correo:** o correo é só do titular.
   - **Sen correo** séguese podendo reservar (nome e teléfono, coma hoxe): esas reservas non se ligan a ningunha conta, non acumulan faltas e o titular vai como `w:<id>`.
8. **Reservar e cambiar, baixo o bloqueo da instalación** ([ADR-0025](0025-reservas-fase-1.md) punto 5). `booking` pide as fichas a `PeopleQueries` co `sub` (do token ou o da conta do correo no mostrador; unha ficha doutra conta ou desactivada non existe: 422 `booking.dependent-unknown`; o mesmo se a ficha se borra mentres se reserva e a FK dá `23503`), comproba que ningunha persoa ten xa praza nesa franxa (di quen: 409 `booking.dependent-seat-taken` ou `booking.seat-taken-by-you`) e que quedan N prazas (409 `booking.not-enough-seats` con `params.free`), e escribe reserva, ocupación, prazas e participantes na mesma transacción. Se dúas peticións se cruzan, a exclusión dá `23P01` → 409 `booking.seat-taken-by-you`. No mostrador, se o correo xa ten praza nesa franxa, o mesmo 409: o persoal ve que esa persoa xa a ten. **Cambiar** ([ADR-0025](0025-reservas-fase-1.md) punto 8) desactiva os participantes vellos e escribe os novos: así se quita ou se engade unha persoa. Unha reserva xa pagada non cambia de número de prazas (422 `booking.change-price-paid`, a regra de sempre). A pegada da clave de reintento inclúe os participantes.
9. **API.**
   - Fichas, da persoa e sen concello (ruta nova na lista pechada das rutas sen concello), con `@PersonUseCase`: `GET /api/v1/me/dependents`, `POST /api/v1/me/dependents`, `PUT /api/v1/me/dependents/{id}` (con `If-Match`) e `DELETE /api/v1/me/dependents/{id}`. Corpo: `{name, birthYear, inMyCare: true}`. A ficha doutra conta ou desactivada dá 404.
   - Reservas: `CreateMyBookingRequest` e `ChangeMyBookingRequest` gañan `participants {self: boolean, dependentIds: uuid[]}`; `BookForRequest` e `ChangeBookingRequest`, o mesmo máis `companions [{name, birthYear}]`. Sen o campo, `{self: true}` (un cliente antigo segue reservando unha praza). As respostas gañan `seats` e `participants[] {kind: SELF|DEPENDENT|COMPANION, name, birthYear}`; o ano só vai na xestión. `BookingNotice` leva o mesmo para o correo.
   - Sen permisos novos: as fichas son da persoa (`@PersonUseCase`), o persoal ve «Para quen» con `booking.view` e leas no mostrador con `booking.book-for`.
10. **Erros** (`PeopleError` e `BookingError`, texto en `messages/*_{gl,es}.properties` e na web):

    | Código | HTTP | Galego | Castelán |
    |---|---|---|---|
    | `people.dependent-not-found` | 404 | Esta persoa xa non está na túa conta. | Esta persona ya no está en tu cuenta. |
    | `people.dependent-limit` | 422 | Podes gardar ata {max} persoas. | Puedes guardar hasta {max} personas. |
    | `people.dependent-duplicate` | 409 | Xa tes gardada unha persoa con ese nome e ano. | Ya tienes guardada una persona con ese nombre y año. |
    | `people.dependent-declaration-required` | 422 | Marca que esta persoa está ao teu cargo. | Marca que esta persona está a tu cargo. |
    | `people.dependent-birth-year-invalid` | 422 | Escribe un ano de nacemento válido. | Escribe un año de nacimiento válido. |
    | `booking.participants-required` | 422 | Escolle para quen é a reserva. | Elige para quién es la reserva. |
    | `booking.participants-not-allowed` | 422 | Neste uso a reserva non leva persoas. | En este uso la reserva no lleva personas. |
    | `booking.too-many-seats` | 422 | Unha reserva leva como moito {max} prazas. | Una reserva lleva como mucho {max} plazas. |
    | `booking.participant-duplicate` | 422 | Hai dúas persoas co mesmo nome e ano. | Hay dos personas con el mismo nombre y año. |
    | `booking.dependent-unknown` | 422 | Algunha das persoas xa non está na conta. | Alguna de las personas ya no está en la cuenta. |
    | `booking.dependent-seat-taken` | 409 | {name} xa ten praza a esa hora. | {name} ya tiene plaza a esa hora. |
    | `booking.not-enough-seats` | 409 | Só quedan {free} prazas a esa hora. | Solo quedan {free} plazas a esa hora. |

    | `booking.work-account-email` | 422 | Ese correo é dunha conta de traballo. Pide o correo persoal ou reserva sen correo. | Ese correo es de una cuenta de trabajo. Pide el correo personal o reserva sin correo. |
    | `booking.dependents-rate-limited` | 429 | Demasiadas consultas seguidas. Proba dentro dun anaco. | Demasiadas consultas seguidas. Prueba dentro de un rato. |

    `booking.seat-taken-by-you` queda para o titular.
11. **Rexistro de actividade e correos.** As fichas edítaas a propia persoa: iso non vai ao rexistro de actividade, que é do persoal ([ADR-0020](0020-rexistro-de-actividade.md)). Acción nova: `booking.dependents-read` (punto 7), con éxito e fallo. Ligar unha reserva á súa conta non se rexistra aquí: fíxoo unha veciña ou un veciño, e queda en `booking_event`. Os cambios dunha reserva desde a xestión seguen gardando só `{field, hidden: true}` para as persoas; as prazas si van co valor (`booking.seats`). Sen correos novos: os da reserva gañan a fila «Para quen» (`row.who`) e o prezo con «N prazas» (`price.seats`). A descarga CSV gaña a columna «Prazas»; os nomes das persoas a cargo non saen.
12. **Datos persoais** (filas en [`datos-persoais.md`](../datos-persoais.md)). Con base 6.1.e non hai consentimento: a casiña é unha declaración de quen reserva, non un «acepto». Conservación, no punto 6. **Textos legais que cambian ao construír** (rexistro formal; pendentes da revisión legal xa anotada):

    | Clave (`web/src/i18n/{gl,es}.json`) | Galego | Castelán |
    |---|---|---|
    | `legalNotice.conditions.booking` (primeira frase) | Cada reserva faise coa conta da persoa titular, para unha instalación, un uso e unha franxa concretos. Nos usos por prazas, a persoa titular pode reservar tamén para as persoas que declara ter ao seu cargo, que garda na súa conta; a persoa titular responde da reserva. | Cada reserva se hace con la cuenta de la persona titular, para una instalación, un uso y una franja concretos. En los usos por plazas, la persona titular puede reservar también para las personas que declara tener a su cargo, que guarda en su cuenta; la persona titular responde de la reserva. |
    | `legalNotice.conditions.onBehalf` | O persoal municipal tamén pode rexistrar reservas no nome dunha persoa e das persoas que a acompañan. Se facilita un correo electrónico, a reserva queda asociada a ese correo: se ten ou crea unha conta con el, verá a reserva en «As miñas reservas». | El personal municipal también puede registrar reservas a nombre de una persona y de las personas que la acompañan. Si facilita un correo electrónico, la reserva queda asociada a ese correo: si tiene o crea una cuenta con él, verá la reserva en «Mis reservas». |
    | `privacy.residents.dataDependents` (nova) | Das persoas a cargo: nome, apelidos e ano de nacemento de cada persoa que a persoa titular da conta declara ter ao seu cargo. Non se piden documento de identidade, parentesco nin datos de saúde. Facilítaos a persoa titular. Cada reserva garda para que persoas se fixo. O persoal municipal veos nas reservas e cando rexistra unha reserva no nome da persoa titular. | De las personas a cargo: nombre, apellidos y año de nacimiento de cada persona que la persona titular de la cuenta declara tener a su cargo. No se piden documento de identidad, parentesco ni datos de salud. Los facilita la persona titular. Cada reserva guarda para qué personas se hizo. El personal municipal los ve en las reservas y cuando registra una reserva a nombre de la persona titular. |
    | `privacy.retention.dependents` (nova) | Persoas a cargo: ata que a persoa titular as elimina. Se xa figuran nalgunha reserva, consérvanse sen poder usarse ata que se anonimiza a última reserva na que figuran. | Personas a cargo: hasta que la persona titular las elimina. Si ya figuran en alguna reserva, se conservan sin poder usarse hasta que se anonimiza la última reserva en la que figuran. |
13. **Horas.** Nada novo: a franxa segue en `tstzrange` e o ano de nacemento é un número, sen data nin zona.
14. **Web** (sen pantalla nova):

    | Onde | Que cambia |
    |---|---|
    | `shared/dependents/` (novo: unha feature non importa outra e úsano o menú e as reservas) | `dependents.store.ts`; selector «Para quen» (o mesmo na reserva pública e no mostrador); diálogo «Persoa ao meu cargo» (engadir e editar); diálogo «Persoas ao meu cargo» (lista, editar, eliminar e «Engadir persoa»). Eliminar confirma con «Xa non poderás reservar para esta persoa. As reservas feitas consérvanse.» |
    | `core/layout/user-menu-items.ts` | Entrada «Persoas ao meu cargo», só para a veciñanza (unha conta de traballo non a ve, coma «As miñas reservas»); abre o diálogo de xestión |
    | `features/booking/reserve/reserve.page` | Fila «Para quen» debaixo de «Hora», só nos usos por prazas: «Eu», as fichas (varias á vez), «Engadir persoa» e «Xestionar»; o total multiplícase |
    | `features/booking/reserve/reserve-confirmed.component` | «Para quen» e o prezo con «N prazas» |
    | `features/booking/mine/booking-card.component` e `booking-qr-dialog.component` | «Para quen» |
    | `features/booking/manage/book-for.page` e `book-for-who.component` | Nos usos por prazas, «Para quen»: a persoa, as súas persoas a cargo (pídense ao saír do campo do correo) e «Engadir acompañante» (nome e ano, só para esa reserva); sen «Xestionar» |
    | `features/booking/manage/booking-detail.component` | «Para quen» cos nomes (e o ano das persoas a cargo e acompañantes) e «N prazas» xunto ao uso |
    **Textos da web** (`web/src/i18n/{gl,es}.json`).

    | Clave | Galego | Castelán |
    |---|---|---|
    | `session.dependents` · `dependents.title` | Persoas ao meu cargo | Personas a mi cargo |
    | `dependents.add` | Engadir persoa | Añadir persona |
    | `dependents.manage` | Xestionar | Gestionar |
    | `dependents.dialog.title` | Persoa ao meu cargo | Persona a mi cargo |
    | `dependents.name` | Nome e apelidos | Nombre y apellidos |
    | `dependents.birthYear` | Ano de nacemento | Año de nacimiento |
    | `dependents.inMyCare` | Esta persoa está ao meu cargo | Esta persona está a mi cargo |
    | `dependents.empty` | Aínda non gardaches ningunha persoa. | Aún no has guardado ninguna persona. |
    | `dependents.delete.title` | Eliminar a {{name}}? | ¿Eliminar a {{name}}? |
    | `dependents.delete.body` | Xa non poderás reservar para esta persoa. As reservas feitas consérvanse. | Ya no podrás reservar para esta persona. Las reservas hechas se conservan. |
    | `dependents.deleted` (toast) | Persoa eliminada | Persona eliminada |
    | `booking.who.label` | Para quen | Para quién |
    | `booking.who.placeholder` | Escolle | Elige |
    | `booking.who.me` | Eu | Yo |
    | `booking.who.seats` | {{count}} prazas | {{count}} plazas |
    | `booking.who.companion.add` | Engadir acompañante | Añadir acompañante |
    | `booking.who.companion.hint` | Só para esta reserva | Solo para esta reserva |
    | `booking.price.seats` | {{amount}} · {{count}} prazas | {{amount}} · {{count}} plazas |
    | `booking.manage.blockLifted` | Levantouse o bloqueo do correo anterior. | Se ha levantado el bloqueo del correo anterior. |

    Os botóns «Editar», «Eliminar», «Cancelar», «Gardar» e «Engadir» son os que xa hai.
15. **Probas previstas.** `DependentTest` (dominio: ano, declaración, límite); `DependentsIT` (alta, edición, duplicada, outra conta → 404, sen correo verificado; eliminar sen reservas borra e con reservas desactiva; a desactivada non se lista nin se usa); `DependentRetentionIT` (a desactivada bórrase cando se anonimiza a súa última reserva; `close_account`; o rol da API non pode chamala); `ReserveParticipantsIT` (só eu, eu e dúas, só unha persoa a cargo, cupo, importe, ficha allea ou desactivada, máis de 11, clave de reintento); `BookForParticipantsIT` (fichas da conta do correo, lista baleira igual sen conta, rexistro de cada lectura, acompañantes sen repetir, permisos); `BookForEmailIT` (con conta, a reserva nace ligada e sae en «As miñas reservas»; sen conta, lígase ao entrar co correo verificado e non co correo sen verificar; repetir non cambia nada; outro concello lígase ao visitalo; sen correo non se liga; conta de traballo non; límite e praza por correo entre mostrador e internet; fila `LINKED` e nada no rexistro de actividade; que ve a conta ligada: sen teléfono nin campos; corrixir o correo recalcula clave, praza e conta e levanta o bloqueo do correo vello; correo de conta de traballo → 422); `HolderDependentsIT` (a resposta é a mesma para «sen conta» e «con conta sen fichas»; `emailHash` no rexistro; 31.ª lectura → 429); `VerifiedAccountsIT` (unha escritura por sesión, cambio de correo, correo reutilizado, dúas sesións á vez co mesmo correo, purga por `seen_at`); `EmailKeyTest` (a mesma clave en `staff`, `people` e `booking`); `BookingParticipantsConsistencyIT`; `ReserveParticipantsIT` cobre tamén «só para unha persoa a cargo e despois para min na mesma hora»; a migración coas reservas do mostrador xa feitas; `ChangeBookingIT` (quitar e engadir unha persoa; pagada); `OccupancyModelIT` (`samePersonNeverHoldsTwoSeats` por participante, tamén a mesma persoa a cargo en dúas reservas simultáneas, e N prazas contra o cupo); `SeatCapacityIT` (liberar N ao cancelar); `BookingRetentionIT` (os participantes anonimízanse e soltan a ficha); `BookingParticipantSqlTenantIT`; `ModularityTest`; `dependents.store.spec.ts`, `reserve.store.spec.ts`, `reserve.page.spec.ts`, `book-for.page.spec.ts`, `user-menu-items.spec.ts`; e2e `reservas.e2e.ts` (engadir unha persoa, reservar para dúas, eliminala) e `xestion-reservas.e2e.ts` («Para quen» no mostrador e na ficha) con axe.

## Consecuencias
- «Unha praza por persoa» segue garantido na base, agora por persoa e non por conta: quen ten a conta pode reservar unha hora para a súa filla e, noutra reserva, a mesma hora para si.
- Queda constancia de para quen reservou cada conta mentres se conserve a reserva, aínda que a persoa «elimine» a ficha: eliminar non é sempre borrar, e a «Privacidade» dío.
- Sen DNI, a identidade dunha persoa a cargo é a súa ficha: a mesma crianza en dúas contas (a nai e o pai) pode ter dúas prazas, e unha ficha inventada ocupa unha praza. Acéptase: o límite de 10 fichas acouta o abuso e pedir o DNI sería tratar máis datos dos precisos.
- Un correo mal escrito no mostrador deixa a reserva a nome doutra persoa, que a vería se ten conta: xa hoxe recibiría a confirmación. O correo leva a liña «Se esta reserva non é túa…», a conta ligada non ve teléfono nin campos, e o persoal corrixe o correo en «Cambiar» (punto 7).
- Furar garda, por primeira vez, que conta ten que correo (`account_email`): só o que xa vén no token, para non pedirlle nada ao sistema de acceso.
- A primeira táboa con datos da veciñanza fóra dun concello, e a primeira lectura dela polo persoal: acoutada a un formulario, á conta do correo que o propio persoal escribe e sempre rexistrada. A plataforma segue sen ver datos da veciñanza.
- Unha FK máis entre módulos e unha función que le todos os concellos e só di si ou non.
- Os indicadores non cambian de forma: as horas reservadas dun uso por prazas pesan `seats`/prazas e «persoas distintas» segue contando titulares.
- A lista das rutas sen concello gaña `/me/dependents` (`arquitectura.md`, ao construír).
- [ADR-0017](0017-datos-persoais.md) punto 4 queda revisado: a reserva si leva o nome e o ano de nacemento da persoa a cargo, que escribe quen a ten ao seu cargo.

## Decidido o 2026-11-10
Respostas do mantedor ás preguntas abertas da primeira versión; xa van na decisión.

| Pregunta | Decisión |
|---|---|
| Cantas fichas por conta e cantas prazas por reserva | 10 fichas; ata 11 prazas. Sen norma «máximo de prazas por reserva» no uso |
| Como conta unha reserva de N prazas no «límite por persoa» | Como unha reserva do titular |
| Acompañante solto no mostrador tamén cando a persoa ten conta | Si, só no mostrador; por internet, só fichas gardadas |
| Volver gardar unha persoa eliminada | Créase unha ficha nova; as reservas vellas seguen ligadas á desactivada |
| Conta de traballo e límite no mostrador | Unha conta de traballo non se liga; o persoal pode reservar aínda que a persoa pase do seu límite |
| De quen é a reserva do mostrador | Do correo: da súa conta se a ten ou cando a teña (punto 7); sen correo, de ninguén |
| Varias prazas nunha reserva do mostrador | Si, co mesmo «Para quen» (punto 7); antes propúñase que non |
| Acompañantes dunha persoa sen conta, no mostrador | O persoal escribe nome e ano de cada un só para esa reserva, sen fichas; sen nomes repetidos e ata 11 prazas |
| Eliminar unha ficha e baixa da conta | Sen reservas, bórrase; con reservas, desactívase e consérvase o que as reservas (punto 6) |
| Dúas fichas activas co mesmo nome e ano | Rexéitase |
| Rango do ano de nacemento | Do ano actual menos 120 ao actual |
| Unha conta de traballo pode gardar fichas | A web non lle mostra a entrada; a API non o impide (non pode reservar) |
| Descarga CSV | Gaña a columna «Prazas»; sen nomes das persoas a cargo |

## Preguntas abertas
Valor que se constrúe mentres o mantedor non diga outra cousa.

| | Pregunta | Valor posto |
|---|---|---|
| PA1 | Fai falta un paso de «reclamar» antes de que unha reserva do mostrador pase á conta | Non: lígase soa ao entrar co correo verificado |

## Alternativas descartadas
- **Fichas por concello (táboa con `municipality_id` e RLS):** a persoa tería que escribilas de novo en cada concello dunha conta que é común, e habería tantas copias dos datos dunha crianza coma concellos.
- **RLS por conta en `account_dependent`** (política sobre un axuste `furar.account_subject`): obriga a fixar un segundo axuste de sesión en cada transacción só para unha táboa, e o mostrador precisaría saltala; o filtro por `sub` e a proba «outra conta → 404» son a mesma garantía ca en «as miñas reservas».
- **Fichas dentro de `booking`:** mestura datos da conta, sen concello, nun módulo onde todo é do concello, e a baixa da conta é cousa de `people`.
- **Unha reserva por persoa (N códigos e N QR):** N correos, N cancelacións e N validacións no mostrador para unha familia que chega xunta.
- **Só o número de prazas, sen nomes** ([ADR-0017](0017-datos-persoais.md) punto 4, antes): non se podería impedir que a mesma persoa teña dúas prazas nin saber no mostrador para quen é a reserva.
- **Pedir DNI, parentesco ou data de nacemento completa:** máis datos dos necesarios (RGPD art. 5.1.c); o ano chega para as franxas de idade e o DNI pídeo o concello que o precise.
- **Manter a exclusión por conta e engadir outra por persoa a cargo:** impediría reservar a mesma hora para unha filla e, despois, para si.
- **Só o nome copiado, sen ligazón á ficha** (primeira versión desta ADR): non se sabería que dúas reservas foron para a mesma persoa a cargo, e borrar a ficha borraría a constancia.
- **Borrar sempre a ficha e deixar o `dependent_id` solto:** un identificador que non apunta a nada non proba para quen se reservou; a FK é a que garante que a ficha segue aí.
- **Conservar as fichas desactivadas un prazo propio:** terían que durar tanto coma a reserva máis longa (5 anos) aínda sen reservas; atadas ás reservas, bórranse en canto deixan de facer falta.
- **Que o persoal busque fichas por nome ou vexa a lista dunha conta fóra do formulario:** abre unha táboa de plataforma a todo o persoal de todos os concellos.
- **Que o persoal cree fichas na conta doutra persoa:** a declaración «está ao meu cargo» só a pode facer quen ten a conta.
- **Crear a conta ao voo no mostrador:** a API tería que poder crear contas no sistema de acceso, e non ten nin debe ter esas credenciais ([ADR-0027](0027-persoal-e-permisos-fase-1.md) punto 2); un correo mal escrito daríalle a outra persoa unha conta e unha reserva alleas; crearía contas que ninguén pediu; e non serve para quen non ten correo.
- **Preguntarlle ao sistema de acceso se un correo ten conta:** as mesmas credenciais; o token de cada persoa xa trae o seu correo verificado.
- **Ligar todas as reservas ao entrar, en todos os concellos á vez:** unha escritura fóra da RLS; ligar no concello que a persoa visita dá o mesmo resultado sen cruzar concellos.
- **Non ligar e buscar sempre polo correo:** «as miñas reservas», os permisos sobre a reserva e a separación da conta de traballo miran `holder_subject`; un correo que cambia de conta arrastraría reservas alleas.
- **Identificar o titular pola conta (`a:<sub>`) na exclusión:** ao ligar unha reserva habería que cambiar a clave, e podería chocar cunha reserva feita por internet na mesma franxa.
- **Deducir a conta do correo dunha reserva anterior no concello** (primeira proposta): non atopa a quen nunca reservou alí e deixa a reserva sen dono.
