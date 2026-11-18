-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: CC0-1.0
--
-- Día no que se aplica: ${demo_day}. Flyway volve aplicar o script cando cambia (o primeiro
-- arrinque de cada día; application-demo.yaml): as datas son relativas a current_date.
--
-- Entidades e tempadas de demostración de Narón (perfil demo; ADR-0039): tres entidades ficticias
-- con contacto @example.org; o usuario de demo vecino-naron é responsable da primeira. Unha
-- tempada da primeira (luns e mércores, 19:00–20:30, na Pista 1 das pistas transversais do
-- pavillón, ata o 30 de xuño, pago pendente) e unha solicitude pendente da segunda, cunha nota.
--
-- O nome ordénase despois de R__demo_naron_bookings.sql: as reservas da demo xa están e as
-- sesións saltan o que estea collido. Idempotente: ids fixos; cada sesión só se engade se aínda
-- non existe, se ninguén ocupa ese oco e se o día non é festivo, pechado nin bloqueado, así a
-- carga nunca falla. A ocupación e a historia van coa sesión, como as garda a API.

do $$
declare
  naron     constant uuid := '019a0b6e-0000-7000-8000-000000000001';
  pavillon  constant uuid := '019a0b6e-0000-7000-8000-000000000204';
  transv    constant uuid := '019a0b6e-0000-7000-8000-000000000237';
  pista1    constant uuid := '019a0b6e-0000-7000-8000-000000000243';
  baloncesto constant uuid := '019a0b6e-0000-7000-8000-000000000b01';
  patinaxe  constant uuid := '019a0b6e-0000-7000-8000-000000000b02';
  futsal    constant uuid := '019a0b6e-0000-7000-8000-000000000b03';
  tempada   constant uuid := '019a0b6e-0000-7000-8000-000000000b11';
  solicitude constant uuid := '019a0b6e-0000-7000-8000-000000000b21';
  alphabet  constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  staff     constant text := 'demo';
  first_day date;
  last_day  date;
  d date;
  starts timestamptz;
  ends timestamptz;
  session uuid;
  digest bytea;
  session_code text;
begin
  insert into booking_organization (id, municipality_id, name, contact_name, email, phone,
                                    created_at, updated_at, version)
  values (baloncesto, naron, 'Club Demo Baloncesto', 'Uxía Cancela',
          'baloncesto@example.org', '600 000 001', now(), now(), 0),
         (patinaxe, naron, 'Club Demo Patinaxe', 'Martiño Boado',
          'patinaxe@example.org', '600 000 002', now(), now(), 0),
         (futsal, naron, 'Escola Demo de Fútbol Sala', 'Lois Seoane',
          'futsal@example.org', null, now(), now(), 0)
  on conflict (id) do nothing;

  insert into booking_organization_manager (municipality_id, organization_id, email_key, email,
                                            added_at, added_by)
  values (naron, baloncesto, 'vecino-naron@example.org', 'vecino-naron@example.org', now(), staff)
  on conflict do nothing;

  -- A tempada: do próximo luns ao 30 de xuño (como moito un ano).
  first_day := current_date + 1;
  while extract(isodow from first_day)::int <> 1 loop
    first_day := first_day + 1;
  end loop;
  last_day := make_date(extract(year from first_day)::int
                          + case when extract(month from first_day) > 6 then 1 else 0 end, 6, 30);
  last_day := least(last_day, first_day + 365);

  if not exists (select 1 from booking_series where id = tempada) then
    insert into booking_series (id, municipality_id, organization_id, facility_id, use_id,
                                part_id, rental_mode, weekdays, start_minute, end_minute,
                                first_date, last_date, payment_status, idempotency_key,
                                idempotency_fingerprint, created_by, created_at, updated_at,
                                version)
    values (tempada, naron, baloncesto, pavillon, transv, pista1, 'PARTS', '{1,3}', 1140, 1230,
            first_day, last_day, 'PENDING', 'demo-tempada-baloncesto', repeat('0', 64), staff,
            now(), now(), 0);
  end if;
  select first_date, last_date into first_day, last_day from booking_series where id = tempada;

  d := first_day;
  while d <= last_day loop
    if extract(isodow from d)::int in (1, 3) then
      starts := (d + make_interval(mins => 1140)) at time zone 'Europe/Madrid';
      ends := (d + make_interval(mins => 1230)) at time zone 'Europe/Madrid';
      session := md5(tempada::text || d::text)::uuid;
      digest := decode(md5('codigo' || session::text), 'hex');
      session_code := 'FUR-'
        || substr(alphabet, get_byte(digest, 0) % 32 + 1, 1)
        || substr(alphabet, get_byte(digest, 1) % 32 + 1, 1)
        || substr(alphabet, get_byte(digest, 2) % 32 + 1, 1)
        || substr(alphabet, get_byte(digest, 3) % 32 + 1, 1);
      if starts > now()
         and not exists (select 1 from booking where id = session)
         and not exists (select 1 from booking where municipality_id = naron
                                                 and code = session_code)
         and not exists (select 1 from public_holiday h where h.date = d)
         and not exists (select 1 from schedule_exception e
                          where e.municipality_id = naron and e.facility_id = pavillon
                            and e.date = d)
         and not exists (select 1 from block b
                          where b.municipality_id = naron and b.facility_id = pavillon
                            and b.period && tstzrange(starts, ends, '[)'))
         and not exists (select 1 from booking_occupancy o
                          where o.municipality_id = naron and o.facility_id = pavillon
                            and o.active and o.period && tstzrange(starts, ends, '[)')
                            and (o.use_id <> transv or o.part_id = pista1)) then
        insert into booking (id, municipality_id, code, facility_id, use_id, part_id,
                             rental_mode, period, slots, status, payment_status, price_kind,
                             cancel_until, holder_name, holder_language, booked_by, created_at,
                             updated_at, version, seats, organization_id, series_id)
        values (session, naron, session_code, pavillon, transv, pista1, 'PARTS',
                tstzrange(starts, ends, '[)'), 2, 'CONFIRMED', 'NONE', 'NONE', starts,
                'Club Demo Baloncesto', 'gl', staff, now(), now(), 0, 1, baloncesto, tempada);
        insert into booking_occupancy (id, municipality_id, booking_id, facility_id, use_id,
                                       part_id, rental_mode, period, seats)
        values (uuidv7(), naron, session, pavillon, transv, pista1, 'PARTS',
                tstzrange(starts, ends, '[)'), 1);
        insert into booking_event (id, municipality_id, booking_id, kind, at, actor_subject)
        values (uuidv7(), naron, session, 'CREATED', now(), staff);
      end if;
    end if;
    d := d + 1;
  end loop;

  -- A solicitude pendente da segunda: os venres pola tarde na mesma pista.
  insert into booking_series_request (id, municipality_id, organization_id, facility_id, use_id,
                                      part_id, rental_mode, weekdays, start_minute, end_minute,
                                      first_date, last_date, note, requested_by_email, status,
                                      created_at, updated_at, version)
  values (solicitude, naron, patinaxe, pavillon, transv, pista1, 'PARTS', '{5}', 1080, 1170,
          first_day + 4, last_day,
          'Adestramentos do equipo infantil. Se o venres non pode ser, valería o xoves.',
          'patinaxe@example.org', 'PENDING', now() - interval '2 hours',
          now() - interval '2 hours', 0)
  on conflict (id) do nothing;
end $$;
