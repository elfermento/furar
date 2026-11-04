-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: CC0-1.0
--
-- Día no que se aplica: ${demo_day}. Flyway volve aplicar o script cando cambia (o primeiro
-- arrinque de cada día; application-demo.yaml): as datas son relativas a current_date.
--
-- Historia de reservas de Narón (perfil demo; plan do dominio 3, D3-05): ano e medio de reservas
-- ata dentro dunha semana para que o panel de indicadores teña que contar, tamén na semana e no
-- mes en curso. Só Narón: Ferrol queda baleiro a propósito (o panel sen datos tamén se ensina).
--
-- Que trae:
--   - As tempadas dos dous cursos anteriores ao da demo (R__demo_municipality_facilities.sql),
--     cos mesmos horarios: o campo ten inverno e verán, as pistas de pádel todo o ano e o pavillón
--     só inverno (no verán pecha). Sen solaparse coas da demo: rematan o 31 de agosto do curso
--     anterior.
--   - Unhas 5.000 reservas en ocos da grella da tempada (cada tramo desde o seu inicio, en pasos do
--     uso), de 600 persoas ficticias (demo-person-h001 … h600, nomes inventados e correos de
--     example.org): 40 habituais e un padrón que medra co tempo. Máis reservas canto máis preto de
--     hoxe, tardes e fins de semana cheos, mañás baleiras, o campo baixa no verán e agosto en xeral.
--   - Os próximos seis días, só o que xa se reservou: cada reserva faise de 2 a 6 días antes, así
--     que entran as que se fixeron antes de agora (mañá case todas, dentro de seis días poucas).
--     Ningunha data de alta nin de cancelación pasa de agora; as futuras confirmadas están
--     pendentes de cobro (cóbrase no mostrador) e as que a persoa xa cancelou, sen cobrar.
--   - Un 8 % canceladas pola persoa en prazo. Unha semana de hai uns catro meses o concello pecha
--     a pista de pádel 1 por obras (un bloqueo cada día) e cancela todas as reservas desa semana.
--   - Cobros: o pendente cóbrase no mostrador ao chegar, así que case todas as pasadas están
--     pagadas: pendentes un terzo das dos últimos dez días e moi poucas (1 de cada 30) dos 45
--     últimos. Canceladas pagadas: devoltas; nos 45 últimos días, a metade aínda a devolver. Así o
--     mes pasado e o en curso ensinan os catro estados nos ingresos.
--
-- Festivos: os de public_holiday (2026 e 2027). Os de 2025 non están publicados nesa táboa e non
-- se inventan aquí: eses días contan coa semana normal da tempada.
--
-- Nada aleatorio: cada oco ten unha clave (uso, parte, día e minuto) e as decisións saen de
-- hashtext da clave, así dúas bases do mesmo día teñen a mesma historia. O id da reserva tamén
-- sae da clave.
--
-- Só en ocos libres, coas regras de booking_occupancy (un uso por franxa, unha ocupación por
-- franxa enteira e unha por parte): os candidatos non se pisan entre si pola súa forma (cada tramo
-- dun día é dun só uso da instalación, as partes van cada unha pola súa conta e as reservas de dúas
-- franxas empezan nas pares) e descártase o que pise unha ocupación activa que xa estea (a demo de
-- reservas, ou o que se reservase nunha base xa usada). As exclusións da táboa son a garantía. Fóra
-- quedan os días con excepción de horario, as franxas con bloqueo e os días cun uso que bloquea os
-- demais. Os usos de Narón non son por prazas: use_slot non se toca.
--
-- O nome ordénase despois de R__demo_naron_bookings.sql (Flyway aplica as repetibles pola
-- descrición): as reservas da demo collen o seu oco primeiro.
--
-- Idempotente: tempadas e bloqueos con ids fixos e «on conflict do nothing»; os tramos só se a
-- tempada non ten ningún; cada reserva só se o seu id non existe e se o oco segue libre; a historia
-- só para as reservas que aínda non a teñen.

do $$
declare
  naron    constant uuid := '019a0b6e-0000-7000-8000-000000000001';
  campo    constant uuid := '019a0b6e-0000-7000-8000-000000000201';
  padel    constant uuid := '019a0b6e-0000-7000-8000-000000000202';
  padel2   constant uuid := '019a0b6e-0000-7000-8000-000000000203';
  pavillon constant uuid := '019a0b6e-0000-7000-8000-000000000204';
  enteiro  constant uuid := '019a0b6e-0000-7000-8000-000000000231';
  futbol7  constant uuid := '019a0b6e-0000-7000-8000-000000000232';
  partido  constant uuid := '019a0b6e-0000-7000-8000-000000000234';
  partido2 constant uuid := '019a0b6e-0000-7000-8000-000000000235';
  central  constant uuid := '019a0b6e-0000-7000-8000-000000000236';
  transv   constant uuid := '019a0b6e-0000-7000-8000-000000000237';
  norte    constant uuid := '019a0b6e-0000-7000-8000-000000000241';
  sur      constant uuid := '019a0b6e-0000-7000-8000-000000000242';
  pista1   constant uuid := '019a0b6e-0000-7000-8000-000000000243';
  pista2   constant uuid := '019a0b6e-0000-7000-8000-000000000244';
  pista3   constant uuid := '019a0b6e-0000-7000-8000-000000000245';
  staff    constant text := 'demo';
  -- O curso da demo (de setembro a agosto), como en R__demo_municipality_facilities.sql.
  y          constant int  := extract(year from current_date - interval '8 months')::int;
  first_day  constant date := (current_date - interval '18 months')::date;
  last_day   constant date := current_date + 6;
  -- A semana de obras na pista de pádel 1: o concello péchaa e avisa o xoves anterior.
  works_day  constant date := date_trunc('week', current_date - 133)::date;
  works_said constant timestamptz := ((works_day - 4) + time '09:15') at time zone 'Europe/Madrid';
  works_reason constant text := 'Obras na cuberta da pista';
  -- Tramos (día, inicio, fin) en minutos, os mesmos ca na demo de instalacións.
  long_week constant int[] := array[
    [1, 540, 840], [1, 960, 1380], [2, 540, 840], [2, 960, 1380], [3, 540, 840],
    [3, 960, 1380], [4, 540, 840], [4, 960, 1380], [5, 540, 840], [5, 960, 1380],
    [6, 540, 1260], [7, 600, 840], [8, 600, 840]];
  summer_week constant int[] := array[
    [1, 540, 840], [1, 1020, 1320], [2, 540, 840], [2, 1020, 1320], [3, 540, 840],
    [3, 1020, 1320], [4, 540, 840], [4, 1020, 1320], [5, 540, 840], [5, 1020, 1320],
    [6, 540, 840]];
  padel_week constant int[] := array[
    [1, 480, 1380], [2, 480, 1380], [3, 480, 1380], [4, 480, 1380], [5, 480, 1380],
    [6, 480, 1380], [7, 480, 1380], [8, 480, 1380]];
  -- Persoas ficticias: 41 nomes (primo, para que as combinacións non se repitan) e 30 apelidos.
  first_names constant text[] := array[
    'Uxía', 'Brais', 'Antía', 'Xoán', 'Iria', 'Martiño', 'Noa', 'Roi', 'Sabela', 'Iago', 'Lúa',
    'Anxo', 'Carme', 'Xabier', 'Alba', 'Breixo', 'Icía', 'Daniel', 'Laura', 'Pablo', 'Sara',
    'Hugo', 'Lucía', 'Diego', 'Paula', 'Mateo', 'Nerea', 'Adrián', 'Andrea', 'Manuel', 'Eva',
    'Xurxo', 'Rosalía', 'Fernando', 'Olalla', 'Tomé', 'Aldara', 'Carlos', 'Mónica', 'Óscar',
    'Xiana'];
  surnames constant text[] := array[
    'Fernández', 'López', 'Rodríguez', 'Vázquez', 'Castro', 'Varela', 'Pérez', 'Souto', 'García',
    'Martínez', 'González', 'Rey', 'Pena', 'Lema', 'Otero', 'Seoane', 'Bouzas', 'Freire',
    'Mosquera', 'Pazos', 'Rivas', 'Dopico', 'Barreiro', 'Teijeiro', 'Novo', 'Graña', 'Mouriño',
    'Iglesias', 'Sanmartín', 'Carballo'];
  alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  s record;
  r int;
  -- As reservas de historia que xa hai (unha base xa usada ou doutro día).
  already int;
begin
  -- 1. Tempadas dos dous cursos anteriores: (id, instalación, nome, desde, ata, semana).
  for s in
    select * from (values
      ('019a0b6e-0000-7000-8000-000000000501'::uuid, campo,
       format('Inverno %s-%s', y - 2, lpad(((y - 1) % 100)::text, 2, '0')),
       make_date(y - 2, 9, 1), make_date(y - 1, 6, 30), long_week),
      ('019a0b6e-0000-7000-8000-000000000502'::uuid, campo, format('Verán %s', y - 1),
       make_date(y - 1, 7, 1), make_date(y - 1, 8, 31), summer_week),
      ('019a0b6e-0000-7000-8000-000000000503'::uuid, campo,
       format('Inverno %s-%s', y - 1, lpad((y % 100)::text, 2, '0')),
       make_date(y - 1, 9, 1), make_date(y, 6, 30), long_week),
      ('019a0b6e-0000-7000-8000-000000000504'::uuid, campo, format('Verán %s', y),
       make_date(y, 7, 1), make_date(y, 8, 31), summer_week),
      ('019a0b6e-0000-7000-8000-000000000505'::uuid, padel,
       format('Todo o ano %s-%s', y - 2, lpad((y % 100)::text, 2, '0')),
       make_date(y - 2, 9, 1), make_date(y, 8, 31), padel_week),
      ('019a0b6e-0000-7000-8000-000000000506'::uuid, padel2,
       format('Todo o ano %s-%s', y - 2, lpad((y % 100)::text, 2, '0')),
       make_date(y - 2, 9, 1), make_date(y, 8, 31), padel_week),
      ('019a0b6e-0000-7000-8000-000000000507'::uuid, pavillon,
       format('Inverno %s-%s', y - 2, lpad(((y - 1) % 100)::text, 2, '0')),
       make_date(y - 2, 9, 1), make_date(y - 1, 6, 30), long_week),
      ('019a0b6e-0000-7000-8000-000000000508'::uuid, pavillon,
       format('Inverno %s-%s', y - 1, lpad((y % 100)::text, 2, '0')),
       make_date(y - 1, 9, 1), make_date(y, 6, 30), long_week))
      as t(id, facility, name, start_date, end_date, week)
  loop
    -- Sen árbitro: tamén salta se choca coa exclusión de solapes (unha base con outras tempadas).
    insert into season (id, municipality_id, facility_id, name, start_date, end_date,
                        created_at, updated_at, version)
    select s.id, naron, s.facility, s.name, s.start_date, s.end_date, now(), now(), 0
    where exists (select 1 from facility where id = s.facility and municipality_id = naron)
    on conflict do nothing;
    if exists (select 1 from season where id = s.id)
       and not exists (select 1 from season_hours where season_id = s.id) then
      for r in 1 .. array_length(s.week, 1) loop
        insert into season_hours (id, municipality_id, season_id, day, start_minute, end_minute)
        values (uuidv7(), naron, s.id, s.week[r][1], s.week[r][2], s.week[r][3]);
      end loop;
    end if;
  end loop;

  -- 2. A semana de obras: a pista de pádel 1 pecha os sete días enteiros (bloqueo de 0:00 a 24:00).
  for r in 0 .. 6 loop
    insert into block (id, municipality_id, facility_id, date, start_minute, end_minute, starts_at,
                       ends_at, reason, created_by, created_at, updated_at, version)
    select ('019a0b6e-0000-7000-8000-0000000005b' || r)::uuid, naron, padel, works_day + r, 0,
           1440, (works_day + r)::timestamp at time zone 'Europe/Madrid',
           (works_day + r + 1)::timestamp at time zone 'Europe/Madrid', works_reason, staff,
           works_said, works_said, 0
    where exists (select 1 from facility where id = padel and municipality_id = naron)
    on conflict do nothing;
  end loop;

  -- 3. Os ocos que se reservan. Cada oco da grella dun día aberto é un candidato; pasa se o seu
  -- número (hashtext da clave, de 0 a 9.999) queda por debaixo da súa probabilidade. Os
  -- candidatos non se pisan entre si: cada tramo dun día é do campo enteiro ou do fútbol 7 (no
  -- pavillón, da pista central ou das transversais), e as reservas de dúas franxas empezan nas
  -- franxas pares do tramo. Fóra os que pisan unha ocupación activa ou un bloqueo alleo.
  select count(*) into already from booking
  where municipality_id = naron and id::text like '019a0b6e-0000-7000-8001-%';
  create temp table demo_history on commit drop as
  with uses (use_id, part_id, side, slots, weight) as (
    values (enteiro, null::uuid, 'WHOLE', 1, 1.0), (futbol7, norte, 'PARTS', 2, 0.9),
           (futbol7, sur, 'PARTS', 2, 0.9), (partido, null, 'ANY', 1, 1.0),
           (partido2, null, 'ANY', 1, 0.8), (central, null, 'WHOLE', 2, 1.0),
           (transv, pista1, 'PARTS', 2, 0.8), (transv, pista2, 'PARTS', 2, 0.8),
           (transv, pista3, 'PARTS', 2, 0.8)),
  days as (
    select d::date as day,
           case when exists (select 1 from public_holiday h where h.date = d::date) then 8
                else extract(isodow from d)::int end as week_day
    from generate_series(first_day, last_day, interval '1 day') as d),
  grid as (
    select x.day, x.week_day, u.use_id, u.part_id, u.slots, u.weight, fu.facility_id,
           fu.rental_mode, fu.slot_minutes, fu.price_kind, fu.price_cents,
           fu.rule_min_advance_hours, fu.rule_max_advance_days, fu.rule_max_slots,
           fu.rule_cap_count, fu.rule_cap_period, fu.rule_cancel_hours,
           h.start_minute + k * fu.slot_minutes as minute
    from uses u
    join facility_use fu on fu.id = u.use_id and fu.municipality_id = naron
    join facility f on f.id = fu.facility_id and f.status = 'ACTIVE'
    join season se on se.facility_id = fu.facility_id and se.municipality_id = naron
    join days x on x.day between se.start_date and se.end_date
    join season_hours h on h.season_id = se.id and h.day = x.week_day
    cross join lateral generate_series(
        0, (h.end_minute - h.start_minute) / fu.slot_minutes - u.slots, u.slots) as k
    where fu.availability = 'ALWAYS'
      and (u.side = 'ANY'
           or (u.side = 'WHOLE') = ((hashtext(fu.facility_id || ':' || x.day || ':'
                                              || h.start_minute) & 2147483647) % 100 < 55))
      and not exists (select 1 from schedule_exception e
                      where e.facility_id = fu.facility_id and e.date = x.day)
      and not exists (select 1 from facility_use o
                      join facility_use_period p on p.use_id = o.id
                      where o.facility_id = fu.facility_id and o.blocks_others
                        and x.day between p.from_date and p.to_date)),
  keyed as (
    select g.*,
           g.use_id || ':' || coalesce(g.part_id::text, '-') || ':' || g.day || ':' || g.minute
             as slot_key,
           (g.day - first_day)::numeric / greatest(last_day - first_day, 1) as progress,
           -- Pasos de tempo real desde o inicio do tramo, como en SlotGrid.
           (g.day + make_interval(mins => g.minute)) at time zone 'Europe/Madrid' as starts
    from grid g),
  chances as (
    select k.*,
           k.starts + make_interval(mins => k.slot_minutes * k.slots) as ends,
           (hashtext(k.slot_key) & 2147483647) % 10000 as pick,
           k.weight
             -- Tendencia: moita menos xente hai ano e medio ca onte.
             * (0.15 + 0.85 * k.progress)
             -- Fins de semana e festivos; tardes cheas; mañás case baleiras.
             * case when k.week_day >= 6 then 0.85
                    when k.minute >= 1080 then 0.95
                    when k.minute >= 1020 then 0.8
                    when k.minute >= 960 then 0.45
                    else 0.1 end
             -- O campo baixa no verán; agosto, todo.
             * case when k.facility_id = campo and extract(month from k.day) in (7, 8) then 0.35
                    when extract(month from k.day) = 8 then 0.7
                    else 1.0 end as chance
    from keyed k),
  keys as (
    select c.*,
           ('019a0b6e-0000-7000-8001-' || substr(md5('furar-demo-history:' || c.slot_key), 1, 12))::uuid
             as booking_id,
           hashtext(c.slot_key || ':state') & 2147483647 as h_state,
           hashtext(c.slot_key || ':person') & 2147483647 as h_person
    from chances c)
  -- O posto conta só as reservas novas e segue despois das que xa hai: outro día, as novas non
  -- collen os códigos das de onte.
  select c.*, already + row_number() over (order by c.starts, c.slot_key) as rank
  from keys c
  where c.pick < c.chance * 10000
    -- Xa reservada (a reserva faise de 2 a 6 días antes) e aínda non gardada.
    and c.starts - make_interval(days => 2 + c.h_state % 5, hours => (c.h_state / 7) % 9) < now()
    and not exists (select 1 from booking b where b.id = c.booking_id)
    and not exists (
      select 1 from block b
      where b.facility_id = c.facility_id and b.municipality_id = naron
        and b.id::text not like '019a0b6e-0000-7000-8000-0000000005b_'
        and b.period && tstzrange(c.starts, c.ends, '[)'))
    and not exists (
      select 1 from booking_occupancy o
      where o.facility_id = c.facility_id and o.municipality_id = naron and o.active
        and o.period && tstzrange(c.starts, c.ends, '[)')
        and (o.use_id <> c.use_id or o.rental_mode <> 'PARTS' or o.part_id = c.part_id));

  -- 4. As reservas. Estado: a semana de obras, cancelada polo concello; un 8 %, pola persoa en
  -- prazo (á metade entre a reserva e o fin do prazo, se xa pasou). Reservada de 2 a 6 días antes
  -- (dentro da antelación das normas do campo); a que aínda non se reservou non entra. A persoa:
  -- un grupo de 40 habituais e o resto dun padrón que medra co tempo ata 600. O código: H (de
  -- historia) e o posto da reserva nova despois das que xa hai, barallado (bixección módulo 32³)
  -- en tres letras; se xa o ten outra reserva, o oco queda sen reservar.
  insert into booking (id, municipality_id, code, facility_id, use_id, part_id, rental_mode,
                       period, slots, status, payment_status, price_kind, amount_cents,
                       cancel_until, rule_min_advance_hours, rule_max_advance_days,
                       rule_max_slots, rule_cap_count, rule_cap_period, rule_cancel_hours,
                       holder_subject, holder_name, holder_email, holder_language,
                       cancelled_at, cancelled_by, created_at, updated_at, version)
  select c.booking_id, naron, code.value, c.facility_id, c.use_id, c.part_id, c.rental_mode,
         tstzrange(c.starts, c.ends, '[)'), c.slots, st.status, pay.payment, c.price_kind,
         case when c.price_kind = 'PAID' then c.price_cents * c.slots end,
         t.cancel_until, c.rule_min_advance_hours, c.rule_max_advance_days, c.rule_max_slots,
         c.rule_cap_count, c.rule_cap_period, c.rule_cancel_hours,
         who.subject,
         who.first_name || ' ' || who.surname1 || ' ' || who.surname2,
         translate(lower(who.first_name || '.' || who.surname1), 'áéíóúüñ', 'aeiouun')
           || '.' || who.number || '@example.org',
         case when who.n % 4 = 0 then 'es' else 'gl' end,
         t.cancelled,
         case st.status when 'CANCELLED_BY_PERSON' then who.subject
                        when 'CANCELLED_BY_MUNICIPALITY' then staff end,
         t.created,
         coalesce(case when pay.payment = 'REFUNDED' then t.cancelled + interval '2 hours' end,
                  t.cancelled,
                  case when pay.paid then t.created + interval '20 minutes' end,
                  t.created),
         (case when pay.paid then 1 else 0 end)
           + (case when t.cancelled is not null then 1 else 0 end)
           + (case when pay.payment = 'REFUNDED' then 1 else 0 end)
  from demo_history c
  cross join lateral (
    select base.created,
           c.starts - make_interval(hours => coalesce(c.rule_cancel_hours, 0)) as cancel_until
    from (select c.starts - make_interval(days => 2 + c.h_state % 5,
                                          hours => (c.h_state / 7) % 9) as created) base) t0
  cross join lateral (
    -- Cancelada pola persoa só se xa o fixo: se a metade do prazo aínda non chegou, segue en pé.
    select case when c.facility_id = padel and c.day between works_day and works_day + 6
                  then 'CANCELLED_BY_MUNICIPALITY'
                when c.h_state % 100 < 8
                     and t0.created + (t0.cancel_until - t0.created) / 2 < now()
                  then 'CANCELLED_BY_PERSON'
                else 'CONFIRMED' end as status) st
  cross join lateral (
    select case when st.status = 'CANCELLED_BY_MUNICIPALITY'
                  then least(t0.created, works_said - interval '1 day')
                else t0.created end as created,
           t0.cancel_until,
           case st.status
             when 'CANCELLED_BY_PERSON' then t0.created + (t0.cancel_until - t0.created) / 2
             when 'CANCELLED_BY_MUNICIPALITY' then works_said + make_interval(mins => c.h_state % 50)
           end as cancelled) t
  cross join lateral (
    select p.payment, p.payment in ('PAID', 'TO_REFUND', 'REFUNDED') as paid
    from (select case
                   when c.price_kind <> 'PAID' then 'NONE'
                   -- Aínda sen xogar: nada cobrado (cóbrase no mostrador ao chegar).
                   when c.starts >= now() then 'PENDING'
                   when st.status = 'CONFIRMED' then
                     case when c.starts >= now() - interval '10 days' and (c.h_state / 100) % 3 = 0
                            then 'PENDING'
                          when c.starts >= now() - interval '45 days' and (c.h_state / 100) % 30 = 0
                            then 'PENDING'
                          else 'PAID' end
                   when st.status = 'CANCELLED_BY_PERSON' then
                     case when (c.h_state / 100) % 2 = 0 then 'PENDING'
                          when c.starts >= now() - interval '45 days' and (c.h_state / 1000) % 2 = 0
                            then 'TO_REFUND'
                          else 'REFUNDED' end
                   else
                     case when (c.h_state / 100) % 4 = 0 then 'PENDING'
                          when (c.h_state / 100) % 5 = 0 then 'TO_REFUND'
                          else 'REFUNDED' end
                 end as payment) p) pay
  cross join lateral (
    select n, lpad(n::text, 3, '0') as number, 'demo-person-h' || lpad(n::text, 3, '0') as subject,
           first_names[1 + n % 41] as first_name, surnames[1 + (n * 7) % 30] as surname1,
           surnames[1 + (n * 11 + 3) % 30] as surname2
    from (select case when c.h_person % 3 = 0 then 1 + (c.h_person / 3) % 40
                      else 1 + (c.h_person / 3) % (120 + floor(480 * c.progress)::int)
                 end as n) p) who
  cross join lateral (
    select 'FUR-H' || substr(alphabet, 1 + ((v >> 10) & 31), 1)
             || substr(alphabet, 1 + ((v >> 5) & 31), 1)
             || substr(alphabet, 1 + (v & 31), 1) as value
    from (select ((c.rank * 690163 + 4099) % 32768)::int as v) shuffled) code
  where t.created < now()
    and not exists (select 1 from booking b where b.id = c.booking_id)
    and not exists (select 1 from booking b
                    where b.municipality_id = naron and b.code = code.value);

  -- A ocupación: activa só nas confirmadas.
  insert into booking_occupancy (id, municipality_id, booking_id, facility_id, use_id, part_id,
                                 rental_mode, period, active)
  select uuidv7(), naron, b.id, b.facility_id, b.use_id, b.part_id, b.rental_mode, b.period,
         b.status = 'CONFIRMED'
  from booking b
  where b.municipality_id = naron
    and b.id::text like '019a0b6e-0000-7000-8001-%'
    and not exists (select 1 from booking_occupancy o
                    where o.municipality_id = naron and o.booking_id = b.id);

  -- 5. A historia de cada reserva nova, coas mesmas horas que a reserva.
  insert into booking_event (id, municipality_id, booking_id, kind, at, actor_subject, channels)
  select uuidv7(), naron, b.id, e.kind, e.at, e.actor, e.channels
  from booking b
  cross join lateral (
    select 'CREATED' as kind, b.created_at as at, b.holder_subject as actor,
           '{}'::text[] as channels
    union all
    select 'PAID', b.created_at + interval '20 minutes', staff, '{}'
    where b.payment_status in ('PAID', 'TO_REFUND', 'REFUNDED')
    union all
    select b.status, b.cancelled_at, b.cancelled_by,
           case when b.status = 'CANCELLED_BY_MUNICIPALITY' then array['EMAIL', 'WEB']
                else '{}'::text[] end
    where b.status <> 'CONFIRMED'
    union all
    select 'REFUNDED', b.cancelled_at + interval '2 hours', staff, '{}'
    where b.payment_status = 'REFUNDED') as e
  where b.municipality_id = naron
    and b.id::text like '019a0b6e-0000-7000-8001-%'
    and not exists (select 1 from booking_event x
                    where x.municipality_id = naron and x.booking_id = b.id);
end
$$;
