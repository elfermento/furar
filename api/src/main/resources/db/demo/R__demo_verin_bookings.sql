-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: CC0-1.0
--
-- Día no que se aplica: ${demo_day}. Flyway volve aplicar o script cando cambia (o primeiro
-- arrinque de cada día; application-demo.yaml): as datas son relativas a current_date.
--
-- Reservas de demostración de Verín (perfil demo), como as de Narón (R__demo_naron_bookings.sql):
-- pasadas, de hoxe e futuras, con datas relativas ao día en que se aplica, para que a lista do
-- xestor e o calendario de Verín teñan contido. Quen reserva: persoas ficticias sen conta, con
-- nomes inventados (ningunha conta de demo: a de xestor-verin é de traballo e non reserva).
--
-- Horas que valen calquera día: as instalacións de Verín abren de 9:00 a 22:00 todos os días
-- (suposto de R__demo_municipality_facilities_verin.sql, que se aplica antes: Flyway ordena as
-- repetibles pola descrición).
--
-- Idempotente: ids e códigos fixos; cada reserva só se engade se aínda non existe e, se está
-- confirmada, se ninguén ocupa ese oco. A ocupación e a historia van coa reserva, como as garda a
-- API.

do $$
declare
  verin   constant uuid := '019a0b6e-0000-7000-8000-000000000003';
  -- Usos e partes de R__demo_municipality_facilities_verin.sql.
  pista   constant uuid := '019a0b6e-0000-7000-8000-000000000701';
  f11     constant uuid := '019a0b6e-0000-7000-8000-000000000705';
  f7      constant uuid := '019a0b6e-0000-7000-8000-000000000706';
  granxa  constant uuid := '019a0b6e-0000-7000-8000-000000000708';
  padel   constant uuid := '019a0b6e-0000-7000-8000-000000000709';
  tenis   constant uuid := '019a0b6e-0000-7000-8000-00000000070a';
  campo1  constant uuid := '019a0b6e-0000-7000-8000-000000000781';
  campo2  constant uuid := '019a0b6e-0000-7000-8000-000000000782';
  pista1  constant uuid := '019a0b6e-0000-7000-8000-000000000783';
  pista2  constant uuid := '019a0b6e-0000-7000-8000-000000000784';
  -- O persoal que cobra e cancela na demo.
  staff   constant text := 'demo';
  r record;
  starts timestamptz;
  ends timestamptz;
  created timestamptz;
  cancelled timestamptz;
  last_change timestamptz;
  facility uuid;
  mode text;
  minutes int;
  price int;
  was_paid boolean;
  row_version int;
begin
  for r in
    select * from (values
      -- (id, código, días desde hoxe, uso, parte, minuto de inicio, persoa, estado, cobro)
      -- Pasadas.
      ('019a0b6e-0000-7000-8000-0000000006a1'::uuid, 'FUR-VR2A', -3, padel, pista1::uuid, 1140,
       'lois', 'CONFIRMED', 'PAID'),
      ('019a0b6e-0000-7000-8000-0000000006a2'::uuid, 'FUR-VR2B', -2, f7, campo1, 1200,
       'sabela', 'CONFIRMED', 'PAID'),
      ('019a0b6e-0000-7000-8000-0000000006a3'::uuid, 'FUR-VR2C', -1, tenis, null, 1080,
       'anxo', 'CANCELLED_BY_PERSON', 'PENDING'),
      -- Hoxe.
      ('019a0b6e-0000-7000-8000-0000000006a4'::uuid, 'FUR-VR2D', 0, padel, pista2, 1080,
       'noa', 'CONFIRMED', 'PENDING'),
      ('019a0b6e-0000-7000-8000-0000000006a5'::uuid, 'FUR-VR2E', 0, granxa, null, 1200,
       'breixo', 'CONFIRMED', 'PAID'),
      ('019a0b6e-0000-7000-8000-0000000006a6'::uuid, 'FUR-VR2F', 0, f11, null, 1260,
       'lois', 'CONFIRMED', 'PENDING'),
      -- Futuras.
      ('019a0b6e-0000-7000-8000-0000000006a7'::uuid, 'FUR-VR2G', 1, padel, pista1, 1140,
       'sabela', 'CONFIRMED', 'PENDING'),
      ('019a0b6e-0000-7000-8000-0000000006a8'::uuid, 'FUR-VR2H', 1, pista, null, 1140,
       'anxo', 'CONFIRMED', 'PAID'),
      ('019a0b6e-0000-7000-8000-0000000006a9'::uuid, 'FUR-VR2J', 2, f7, campo2, 1140,
       'breixo', 'CONFIRMED', 'PENDING'),
      ('019a0b6e-0000-7000-8000-0000000006aa'::uuid, 'FUR-VR2K', 3, tenis, null, 600,
       'noa', 'CONFIRMED', 'PENDING'),
      ('019a0b6e-0000-7000-8000-0000000006ab'::uuid, 'FUR-VR2L', 4, granxa, null, 1080,
       'lois', 'CANCELLED_BY_MUNICIPALITY', 'TO_REFUND'))
      as b(id, code, days, use_id, part_id, minute, who, status, payment)
    join (values
      ('lois', 'demo-person-lois', 'Lois Álvarez', 'lois.alvarez@example.org', 'gl'),
      ('sabela', 'demo-person-sabela', 'Sabela Rodríguez', 'sabela.rodriguez@example.org', 'gl'),
      ('anxo', 'demo-person-anxo', 'Anxo Fernández', 'anxo.fernandez@example.org', 'es'),
      ('noa', 'demo-person-noa', 'Noa Gómez', 'noa.gomez@example.org', 'gl'),
      ('breixo', 'demo-person-breixo', 'Breixo Pérez', 'breixo.perez@example.org', 'gl'))
      as p(who, subject, name, email, language) using (who)
    order by days, minute
  loop
    continue when exists (select 1 from booking where id = r.id)
               or exists (select 1 from booking where municipality_id = verin and code = r.code);

    facility := null;
    select u.facility_id, u.rental_mode, u.slot_minutes, u.price_cents
      into facility, mode, minutes, price
    from facility_use u
    where u.id = r.use_id and u.municipality_id = verin;
    continue when facility is null or price is null;

    starts := (current_date + r.days + make_interval(mins => r.minute)) at time zone 'Europe/Madrid';
    ends := starts + make_interval(mins => minutes);
    continue when r.status = 'CONFIRMED' and exists (
      select 1 from booking_occupancy o
      where o.facility_id = facility and o.active
        and o.period && tstzrange(starts, ends, '[)')
        and (o.use_id <> r.use_id or o.rental_mode <> 'PARTS' or o.part_id = r.part_id));

    created := least(starts - interval '3 days', now() - interval '3 hours');
    was_paid := r.payment in ('PAID', 'TO_REFUND', 'REFUNDED');
    cancelled := case when r.status <> 'CONFIRMED' then created + interval '1 hour' end;
    last_change := coalesce(cancelled,
                            case when was_paid then created + interval '30 minutes' end, created);
    row_version := (case when was_paid then 1 else 0 end)
             + (case when cancelled is not null then 1 else 0 end);

    insert into booking (id, municipality_id, code, facility_id, use_id, part_id, rental_mode,
                         period, slots, status, payment_status, price_kind, amount_cents,
                         cancel_until, holder_subject, holder_name, holder_email, holder_language,
                         cancelled_at, cancelled_by, created_at, updated_at, version)
    values (r.id, verin, r.code, facility, r.use_id, r.part_id, mode,
            tstzrange(starts, ends, '[)'), 1, r.status, r.payment, 'PAID', price, starts,
            r.subject, r.name, r.email, r.language,
            cancelled,
            case r.status when 'CANCELLED_BY_PERSON' then r.subject
                          when 'CANCELLED_BY_MUNICIPALITY' then staff end,
            created, last_change, row_version);

    insert into booking_occupancy (id, municipality_id, booking_id, facility_id, use_id, part_id,
                                   rental_mode, period, active)
    values (uuidv7(), verin, r.id, facility, r.use_id, r.part_id, mode,
            tstzrange(starts, ends, '[)'), r.status = 'CONFIRMED');

    insert into booking_event (id, municipality_id, booking_id, kind, at, actor_subject)
    values (uuidv7(), verin, r.id, 'CREATED', created, r.subject);
    if was_paid then
      insert into booking_event (id, municipality_id, booking_id, kind, at, actor_subject)
      values (uuidv7(), verin, r.id, 'PAID', created + interval '30 minutes', staff);
    end if;
    if r.status = 'CANCELLED_BY_PERSON' then
      insert into booking_event (id, municipality_id, booking_id, kind, at, actor_subject)
      values (uuidv7(), verin, r.id, 'CANCELLED_BY_PERSON', cancelled, r.subject);
    elsif r.status = 'CANCELLED_BY_MUNICIPALITY' then
      insert into booking_event (id, municipality_id, booking_id, kind, at, actor_subject,
                                 channels)
      values (uuidv7(), verin, r.id, 'CANCELLED_BY_MUNICIPALITY', cancelled, staff,
              array['EMAIL', 'WEB']);
    end if;
  end loop;
end
$$;
