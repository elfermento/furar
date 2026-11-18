-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: CC0-1.0
--
-- Día no que se aplica: ${demo_day}. Flyway volve aplicar o script cando cambia (o primeiro
-- arrinque de cada día; application-demo.yaml): as datas son relativas a current_date.
--
-- Persoas a cargo, non presentados e lista de espera de demostración en Narón (perfil demo;
-- ADR-0031, ADR-0032 e ADR-0033), para que se vexan ao entrar:
--
--   - vecino-naron ten dúas persoas ao seu cargo, unha menor e unha maior (nomes inventados), e
--     unha reserva próxima de tres prazas (el e as dúas) no uso por prazas do pavillón
--     («Bádminton libre», 3,00 € por persoa: 9,00 €).
--   - vecino-naron ten unha reserva pasada na pista de pádel 1 marcada «non presentado» pola
--     recepción: unha falta, sen bloqueo.
--   - Unha persoa sen conta (vecina-naron@example.org, reservas feitas no mostrador) leva tres
--     faltas en menos de 30 días e está bloqueada 15 días desde a terceira. O bloqueo vai polo
--     correo: unha conta con ese correo ve o aviso ao reservar por internet.
--   - vecino-naron espera por unha hora ocupada dos próximos días na pista de pádel 1 (se pode
--     ser, unha na que a pista 2 está libre, para ver «ocos parecidos»).
--
-- As horas ocupadas das dúas pistas de pádel nos próximos días son as da historia de reservas
-- (R__demo_naron_history.sql). A penalización de Narón acéndese en R__demo_municipalities.sql; o
-- uso por prazas e o control de entrada van en R__demo_municipality_facilities.sql.
--
-- O nome ordénase despois de R__demo_naron_history.sql (Flyway aplica as repetibles pola
-- descrición): cada reserva de aquí colle a primeira hora da súa lista que está aberta e libre,
-- e a espera precisa as horas ocupadas da historia.
--
-- Os ids das reservas seguen aos de R__demo_naron_bookings.sql (…04xx); os códigos son FUR-DE3…
--
-- Idempotente: ids e códigos fixos. Cada reserva só se engade se aínda non existe; o bloqueo,
-- só se están as tres faltas e aínda non existe; a espera, só se non a hai (ao pasar a súa hora
-- bórrase soa, e o día seguinte entra outra).

do $$
declare
  naron     constant uuid := '019a0b6e-0000-7000-8000-000000000001';
  -- Instalacións e usos de R__demo_municipality_facilities.sql.
  padel     constant uuid := '019a0b6e-0000-7000-8000-000000000202';
  padel2    constant uuid := '019a0b6e-0000-7000-8000-000000000203';
  partido   constant uuid := '019a0b6e-0000-7000-8000-000000000234';
  badminton constant uuid := '019a0b6e-0000-7000-8000-000000000239';
  -- Usuarios do realm de demo (deploy/keycloak/demo/overlay.json).
  vecino    constant text := '019a0b6e-0000-7000-8000-000000000901';
  recepcion constant text := '019a0b6e-0000-7000-8000-000000000904';
  vecino_email  constant text := 'vecino-naron@example.org';
  blocked_email constant text := 'vecina-naron@example.org';
  child     constant uuid := '019a0b6e-0000-7000-8000-000000000601';
  elder     constant uuid := '019a0b6e-0000-7000-8000-000000000602';
  block_id  constant uuid := '019a0b6e-0000-7000-8000-000000000611';
  wait_id   constant uuid := '019a0b6e-0000-7000-8000-000000000621';
  this_year constant int := extract(year from current_date)::int;
  declared  constant timestamptz := date_trunc('minute', now()) - interval '20 days';
  r record;
  slot_day date;
  week_day int;
  start_minute int;
  candidate int;
  shift int;
  starts timestamptz;
  ends timestamptz;
  created timestamptz;
  marked timestamptz;
  facility uuid;
  mode text;
  minutes int;
  use_capacity int;
  use_price_kind text;
  price int;
  last_fault timestamptz;
  wait_start timestamptz;
  wait_end timestamptz;
  wait_ordinal int;
begin
  -- 1. As persoas ao cargo de vecino-naron: a ficha leva o nome, o ano de nacemento e a data da
  -- declaración «Esta persoa está ao meu cargo».
  insert into account_dependent (id, account_subject, name, birth_year, declared_at, created_at,
                                 updated_at, version)
  values (child, vecino, 'Lúa Souto Pena', this_year - 9, declared, declared, declared, 0),
         (elder, vecino, 'Manuel Souto Rey', this_year - 79, declared, declared, declared, 0)
  on conflict do nothing;

  -- 2. As reservas: (id, código, días desde hoxe, uso, horas que se proban en orde (minutos),
  -- persoa, prazas, falta). A de varias prazas é a única futura; as pasadas quedan sen cobrar.
  for r in
    select * from (values
      ('019a0b6e-0000-7000-8000-000000000431'::uuid, 'FUR-DE3A', 4, badminton,
       array[660, 720, 600, 780], 'vecino', 3, false),
      ('019a0b6e-0000-7000-8000-000000000432'::uuid, 'FUR-DE3B', -3, partido,
       array[660, 570, 750, 840, 480, 930, 1020], 'vecino', 1, true),
      ('019a0b6e-0000-7000-8000-000000000433'::uuid, 'FUR-DE3C', -12, partido,
       array[660, 570, 750, 840, 480, 930, 1020], 'blocked', 1, true),
      ('019a0b6e-0000-7000-8000-000000000434'::uuid, 'FUR-DE3D', -7, partido,
       array[660, 570, 750, 840, 480, 930, 1020], 'blocked', 1, true),
      ('019a0b6e-0000-7000-8000-000000000435'::uuid, 'FUR-DE3E', -2, partido,
       array[660, 570, 750, 840, 480, 930, 1020], 'blocked', 1, true))
      as b(id, code, days, use_id, minutes, who, seats, no_show)
    join (values
      -- A persoa bloqueada reservou no mostrador: sen conta, co teléfono que deu.
      ('vecino', vecino, null::text, 'Veciño Narón', vecino_email, null::text, 'es'),
      ('blocked', null::text, recepcion, 'Veciña Narón', blocked_email, '600 000 000', 'gl'))
      as p(who, subject, booked_by, name, email, phone, language) using (who)
    order by days
  loop
    continue when exists (select 1 from booking where id = r.id)
               or exists (select 1 from booking where municipality_id = naron and code = r.code);

    facility := null;
    select u.facility_id, u.rental_mode, u.slot_minutes, u.capacity, u.price_kind, u.price_cents
      into facility, mode, minutes, use_capacity, use_price_kind, price
    from facility_use u
    where u.id = r.use_id and u.municipality_id = naron and u.archived_at is null;
    continue when facility is null or use_price_kind = 'NONE';
    -- A de varias prazas é para a persoa e as dúas ao seu cargo: sen as dúas fichas, non entra.
    continue when r.seats > 1 and r.seats - 1 <> (
      select count(*) from account_dependent d
      where d.id in (child, elder) and d.account_subject = r.subject
        and d.deactivated_at is null);

    -- O primeiro día (o seu ou, se non pode ser, un do lado) e a primeira hora da lista que cae
    -- nun tramo aberto da tempada e está libre: sen bloqueo e sen ocupación doutro uso (nin do
    -- mesmo, se o uso non é por prazas). A falta ten que estar xa anotada; a outra, por xogar.
    start_minute := null;
    foreach shift in array array[0, -1, 1, -2, 2] loop
      slot_day := current_date + r.days + shift;
      week_day := case when exists (select 1 from public_holiday h where h.date = slot_day)
                         then 8
                       else extract(isodow from slot_day)::int end;
      continue when exists (select 1 from schedule_exception e
                            where e.facility_id = facility and e.date = slot_day);
      foreach candidate in array r.minutes loop
        starts := (slot_day + make_interval(mins => candidate)) at time zone 'Europe/Madrid';
        ends := starts + make_interval(mins => minutes);
        if (case when r.no_show then ends + interval '15 minutes' < now() else starts > now() end)
           and exists (
             select 1 from season s
             join season_hours h on h.season_id = s.id
             where s.facility_id = facility and s.municipality_id = naron
               and slot_day between s.start_date and s.end_date and h.day = week_day
               and candidate >= h.start_minute and candidate + minutes <= h.end_minute
               and (candidate - h.start_minute) % minutes = 0)
           and not exists (
             select 1 from block k
             where k.facility_id = facility and k.municipality_id = naron
               and k.period && tstzrange(starts, ends, '[)'))
           and not exists (
             select 1 from booking_occupancy o
             where o.facility_id = facility and o.municipality_id = naron and o.active
               and o.period && tstzrange(starts, ends, '[)')
               and (o.use_id <> r.use_id or mode <> 'SEATS'))
           and (mode <> 'SEATS' or coalesce(
                 (select s.taken from use_slot s
                  where s.municipality_id = naron and s.use_id = r.use_id
                    and s.slot_start = starts),
                 0) + r.seats <= use_capacity) then
          start_minute := candidate;
          exit;
        end if;
      end loop;
      exit when start_minute is not null;
    end loop;
    continue when start_minute is null;

    created := least(starts - interval '3 days', now() - interval '3 hours');
    -- A recepción anota a falta un cuarto de hora despois de rematar a franxa.
    marked := case when r.no_show then ends + interval '15 minutes' end;

    insert into booking (id, municipality_id, code, facility_id, use_id, rental_mode, period,
                         slots, seats, status, payment_status, price_kind, amount_cents,
                         cancel_until, holder_subject, holder_name, holder_email,
                         holder_email_key, holder_phone, holder_language, booked_by,
                         attendance, attendance_at, attendance_by, created_at, updated_at,
                         version)
    values (r.id, naron, r.code, facility, r.use_id, mode, tstzrange(starts, ends, '[)'),
            1, r.seats, 'CONFIRMED',
            case when use_price_kind = 'PAID' then 'PENDING' else 'NONE' end,
            use_price_kind, price * r.seats,
            starts, r.subject, r.name, r.email, lower(btrim(r.email)), r.phone, r.language,
            r.booked_by,
            case when r.no_show then 'NO_SHOW' else 'NONE' end, marked,
            case when r.no_show then recepcion end, created, coalesce(marked, created),
            case when r.no_show then 1 else 0 end);

    insert into booking_occupancy (id, municipality_id, booking_id, facility_id, use_id,
                                   rental_mode, period, seats, active)
    values (uuidv7(), naron, r.id, facility, r.use_id, mode, tstzrange(starts, ends, '[)'),
            r.seats, true);

    if mode = 'SEATS' then
      -- O contador de prazas da franxa e as persoas da reserva, como as garda a API: o titular
      -- polo seu correo e cada persoa a cargo pola súa ficha, coa copia do nome e do ano.
      insert into use_slot (municipality_id, use_id, slot_start, capacity, taken)
      values (naron, r.use_id, starts, use_capacity, r.seats)
      on conflict (municipality_id, use_id, slot_start)
        do update set taken = use_slot.taken + excluded.taken;

      insert into booking_participant (id, municipality_id, booking_id, use_id, period, kind,
                                       person_key, dependent_id, name, birth_year, position)
      select uuidv7(), naron, r.id, r.use_id, tstzrange(starts, ends, '[)'), x.kind, x.person_key,
             x.dependent_id, x.name, x.birth_year, x.position
      from (
        select 'HOLDER' as kind, 'e:' || lower(btrim(r.email)) as person_key,
               null::uuid as dependent_id, null::text as name, null::int as birth_year,
               0 as position
        union all
        select 'DEPENDENT', 'd:' || d.id, d.id, d.name, d.birth_year,
               (row_number() over (order by d.birth_year desc, d.id))::int
        from account_dependent d
        where d.id in (child, elder) and d.account_subject = r.subject
          and d.deactivated_at is null) x
      order by x.position;
    end if;

    insert into booking_event (id, municipality_id, booking_id, kind, at, actor_subject)
    values (uuidv7(), naron, r.id, 'CREATED', created, coalesce(r.subject, r.booked_by));
    if r.no_show then
      insert into booking_event (id, municipality_id, booking_id, kind, at, actor_subject)
      values (uuidv7(), naron, r.id, 'NO_SHOW', marked, recepcion);
    end if;
  end loop;

  -- 3. O bloqueo da persoa das tres faltas: desde a terceira ata o final do día de 15 días
  -- despois (hora de Galicia), coas faltas que o causaron. Só coas tres en pé.
  select max(attendance_at) into last_fault
  from booking
  where municipality_id = naron and holder_email_key = blocked_email and attendance = 'NO_SHOW'
    and id in ('019a0b6e-0000-7000-8000-000000000433', '019a0b6e-0000-7000-8000-000000000434',
               '019a0b6e-0000-7000-8000-000000000435')
  having count(*) = 3;
  if last_fault is not null
     and not exists (select 1 from booking_no_show_block where id = block_id) then
    insert into booking_no_show_block (id, municipality_id, holder_email_key, starts_at, ends_at,
                                       created_at)
    values (block_id, naron, blocked_email, last_fault,
            ((last_fault at time zone 'Europe/Madrid')::date + 16)::timestamp
              at time zone 'Europe/Madrid',
            last_fault);
    insert into booking_no_show_block_fault (municipality_id, block_id, booking_id)
    select naron, block_id, b.id
    from booking b
    where b.municipality_id = naron and b.holder_email_key = blocked_email
      and b.attendance = 'NO_SHOW'
      and b.id in ('019a0b6e-0000-7000-8000-000000000433',
                   '019a0b6e-0000-7000-8000-000000000434',
                   '019a0b6e-0000-7000-8000-000000000435');
  end if;

  -- 4. A espera de vecino-naron: unha hora ocupada da pista de pádel 1, de mañá en diante, que
  -- non sexa súa; antes as horas nas que a pista 2 está libre. Como moito tres esperas por conta.
  if not exists (select 1 from booking_wait where id = wait_id)
     and (select count(*) from booking_wait
          where municipality_id = naron and account_subject = vecino) < 3 then
    select lower(o.period), upper(o.period) into wait_start, wait_end
    from booking_occupancy o
    join booking b on b.id = o.booking_id and b.municipality_id = naron
    where o.municipality_id = naron and o.facility_id = padel and o.use_id = partido and o.active
      and b.slots = 1 and b.holder_email_key is distinct from vecino_email
      and lower(o.period) >= (current_date + 1)::timestamp at time zone 'Europe/Madrid'
      and not exists (select 1 from booking_wait w
                      where w.municipality_id = naron and w.account_subject = vecino
                        and w.use_id = partido and w.slot_start = lower(o.period))
    order by exists (select 1 from booking_occupancy p
                     where p.municipality_id = naron and p.facility_id = padel2 and p.active
                       and p.period && o.period),
             lower(o.period)
    limit 1;
    select min(n) into wait_ordinal
    from generate_series(1, 3) as n
    where not exists (select 1 from booking_wait w
                      where w.municipality_id = naron and w.account_subject = vecino
                        and w.ordinal = n);
    if wait_start is not null and wait_ordinal is not null then
      insert into booking_wait (id, municipality_id, facility_id, use_id, slot_start, slot_end,
                                account_subject, email_key, language, ordinal, created_at)
      values (wait_id, naron, padel, partido, wait_start, wait_end, vecino, vecino_email, 'es',
              wait_ordinal, least(now() - interval '2 hours', wait_start - interval '1 hour'));
    end if;
  end if;
end
$$;
