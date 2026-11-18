-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: CC0-1.0
--
-- Día no que se aplica: ${demo_day}. Flyway volve aplicar o script cando cambia (o primeiro
-- arrinque de cada día; application-demo.yaml): as datas son relativas a current_date.
--
-- Actividades con horario de demostración de Narón (perfil demo; ADR-0041), as escolas deportivas:
--   - Natación · Iniciación (Piscina municipal de Narón, vaso de ensino): luns e mércores,
--     17:00–18:00, desde tres semanas antes do día de carga e seis meses; 20 prazas, 18 con
--     praza (dúas libres), 25,00 € ao trimestre, para nadas e nados de hai 12 a hai 10 anos. Unha
--     persoa a cargo de vecino-naron (Iago, de hai 11 anos) está apuntada e pagou; as demais son
--     inventadas, cos pagos variados. As dúas últimas sesións pasadas teñen a lista pasada.
--   - Natación · Perfeccionamento (a mesma piscina): martes e xoves, 17:00–18:00, as mesmas datas;
--     12 prazas, completa, e tres persoas na lista de espera (vecino-naron é a segunda).
--   - Andaina nórdica (outro sitio, sen límite de idade): sábados, 10:00–11:30, gratis, 25 prazas;
--     vecino-naron está apuntado.
-- As persoas inventadas non teñen conta nin correo: só un nome.
--
-- Cada sesión nunha instalación é unha reserva da actividade (activity_id, sen persoa, co nome da
-- actividade), coa súa ocupación e a fila CREATED da historia, como as garda a API. A carga segue
-- as regras do planificador sen chamalo: un día festivo, cunha excepción do horario, cun
-- bloqueo, fóra do horario da tempada ou cunha ocupación activa na piscina non se crea. O nome
-- ordénase despois de R__demo_naron_bookings.sql e de R__demo_naron_people.sql.
--
-- Idempotente: ids que saen dun texto fixo; se a actividade xa existe non se toca (as súas datas
-- quedan as da primeira carga), así repetir a carga non cambia nada.

do $$
declare
  naron    constant uuid := '019a0b6e-0000-7000-8000-000000000001';
  pool     constant uuid := '019a0b6e-0000-7000-8000-000000000207';
  vaso     constant uuid := '019a0b6e-0000-7000-8000-00000000023a';
  vecino   constant text := '019a0b6e-0000-7000-8000-000000000901';
  staff    constant text := 'demo';
  alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  this_year constant int := extract(year from current_date)::int;
  iago     constant uuid := md5('demo-dependent-iago')::uuid;
  basic    constant uuid := md5('demo-activity-swim-basic')::uuid;
  advanced constant uuid := md5('demo-activity-swim-advanced')::uuid;
  walk     constant uuid := md5('demo-activity-nordic-walk')::uuid;
  names    constant text[] := array['Antía', 'Brais', 'Carme', 'Iago', 'Noa', 'Xoán', 'Uxía',
                                    'Martiño', 'Sabela', 'Lois', 'Iria', 'Roi', 'Alba', 'Breixo'];
  surnames constant text[] := array['Souto', 'Castro', 'Pena', 'Fernández', 'Rodríguez',
                                    'Iglesias', 'Barreiro', 'Muíños', 'Lourido', 'Freire'];
  a record;
  first_day date;
  last_day date;
  d date;
  starts timestamptz;
  ends timestamptz;
  session uuid;
  activity_booking uuid;
  booking_code text;
  digest bytea;
  salt int;
  n int;
  week_day int;
begin
  -- Unha persoa a cargo máis de vecino-naron, na idade da iniciación.
  insert into account_dependent (id, account_subject, name, birth_year, declared_at, created_at,
                                 updated_at, version)
  values (iago, vecino, 'Iago Souto Pena', this_year - 11, now(), now(), now(), 0)
  on conflict do nothing;

  -- O curso: do luns de hai tres semanas a seis meses despois.
  first_day := current_date - 21;
  while extract(isodow from first_day)::int <> 1 loop
    first_day := first_day - 1;
  end loop;
  last_day := first_day + 180;

  -- (id, nome gl, nome es, instalación, días ISO, máscara, inicio, fin, prazas, prezo, período,
  --  ano desde, ano ata, lugar)
  for a in
    select * from (values
      (basic, 'Natación · Iniciación', 'Natación · Iniciación', true, array[1, 3], 5, 1020, 1080,
       20, 2500, 'QUARTER', this_year - 12, this_year - 10, null::text),
      (advanced, 'Natación · Perfeccionamento', 'Natación · Perfeccionamiento', true, array[2, 4],
       10, 1020, 1080, 12, 2500, 'QUARTER', null::int, null::int, null::text),
      (walk, 'Andaina nórdica', 'Marcha nórdica', false, array[6], 32, 600, 690, 25, null::int,
       'ONCE', null::int, null::int, 'Saída do parque do Freixeiro'))
      as t(id, name_gl, name_es, in_pool, days, mask, start_minute, end_minute, capacity, price,
           period, birth_from, birth_to, place)
  loop
    continue when exists (select 1 from event where id = a.id);

    insert into event (id, municipality_id, name_gl, name_es, description_gl, description_es, kind,
                       starts_at, ends_at, facility_id, place, use_id, weekdays, start_minute,
                       end_minute, first_date, last_date, capacity, price_cents, price_period,
                       published, approval, birth_year_from, birth_year_to, created_at,
                       updated_at, version)
    values (a.id, naron, a.name_gl, a.name_es,
            case when a.in_pool then 'Curso das escolas deportivas municipais, con monitor.'
                 else 'Saída en grupo con bastóns; o concello presta os bastóns.' end,
            case when a.in_pool then 'Curso de las escuelas deportivas municipales, con monitor.'
                 else 'Salida en grupo con bastones; el ayuntamiento presta los bastones.' end,
            'SCHEDULED',
            (first_day + make_interval(mins => a.start_minute)) at time zone 'Europe/Madrid',
            (last_day + make_interval(mins => a.end_minute)) at time zone 'Europe/Madrid',
            case when a.in_pool then pool end, a.place, case when a.in_pool then vaso end,
            a.mask, a.start_minute, a.end_minute, first_day, last_day, a.capacity, a.price,
            a.period, true, false, a.birth_from, a.birth_to,
            now() - interval '30 days', now() - interval '30 days', 0);

    -- As sesións: as mesmas clases ca o planificador.
    d := first_day;
    while d <= last_day loop
      if extract(isodow from d)::int = any (a.days) then
        starts := (d + make_interval(mins => a.start_minute)) at time zone 'Europe/Madrid';
        ends := (d + make_interval(mins => a.end_minute)) at time zone 'Europe/Madrid';
        session := md5(a.id::text || d::text)::uuid;
        activity_booking := null;
        week_day := case when exists (select 1 from public_holiday h where h.date = d) then 8
                         else extract(isodow from d)::int end;
        if not a.in_pool then
          insert into event_session (id, municipality_id, event_id, session_date, starts_at,
                                     ends_at, created_at, updated_at, version)
          values (session, naron, a.id, d, starts, ends, now(), now(), 0);
        elsif week_day <> 8
              and not exists (select 1 from schedule_exception e
                              where e.municipality_id = naron and e.facility_id = pool
                                and e.date = d)
              and not exists (select 1 from block b
                              where b.municipality_id = naron and b.facility_id = pool
                                and b.period && tstzrange(starts, ends, '[)'))
              and exists (select 1 from season s
                          join season_hours h on h.season_id = s.id
                          where s.municipality_id = naron and s.facility_id = pool
                            and d between s.start_date and coalesce(s.end_date, d)
                            and h.day = week_day
                            and h.start_minute <= a.start_minute
                            and h.end_minute >= a.end_minute)
              and not exists (select 1 from booking_occupancy o
                              where o.municipality_id = naron and o.facility_id = pool
                                and o.active and o.period && tstzrange(starts, ends, '[)')) then
          activity_booking := md5('reserva' || session::text)::uuid;
          -- Un código que ninguén ten (nin os prefixos das reservas de demo).
          salt := 0;
          loop
            digest := decode(md5('codigo' || session::text || salt::text), 'hex');
            booking_code := 'FUR-'
              || substr(alphabet, get_byte(digest, 0) % 32 + 1, 1)
              || substr(alphabet, get_byte(digest, 1) % 32 + 1, 1)
              || substr(alphabet, get_byte(digest, 2) % 32 + 1, 1)
              || substr(alphabet, get_byte(digest, 3) % 32 + 1, 1);
            exit when booking_code not like 'FUR-DE%' and booking_code not like 'FUR-VR%'
                      and not exists (select 1 from booking
                                      where municipality_id = naron and code = booking_code);
            salt := salt + 1;
          end loop;
          insert into booking (id, municipality_id, code, facility_id, use_id, part_id,
                               rental_mode, period, slots, status, payment_status, price_kind,
                               cancel_until, holder_name, holder_language, booked_by, created_at,
                               updated_at, version, seats, activity_id)
          values (activity_booking, naron, booking_code, pool, vaso, null, 'WHOLE',
                  tstzrange(starts, ends, '[)'), 1, 'CONFIRMED', 'NONE', 'NONE', starts,
                  a.name_gl, 'gl', staff, now() - interval '30 days',
                  now() - interval '30 days', 0, 1, a.id);
          insert into booking_occupancy (id, municipality_id, booking_id, facility_id, use_id,
                                         part_id, rental_mode, period, seats)
          values (uuidv7(), naron, activity_booking, pool, vaso, null, 'WHOLE',
                  tstzrange(starts, ends, '[)'), 1);
          insert into booking_event (id, municipality_id, booking_id, kind, at, actor_subject)
          values (uuidv7(), naron, activity_booking, 'CREATED', now() - interval '30 days', staff);
          insert into event_session (id, municipality_id, event_id, session_date, starts_at,
                                     ends_at, booking_id, created_at, updated_at, version)
          values (session, naron, a.id, d, starts, ends, activity_booking, now(), now(), 0);
        end if;
      end if;
      d := d + 1;
    end loop;

    -- O inicio e o fin da actividade son os das súas sesións.
    update event e
       set starts_at = x.first_start, ends_at = x.last_end
      from (select min(starts_at) as first_start, max(ends_at) as last_end
              from event_session where event_id = a.id and municipality_id = naron) x
     where e.id = a.id and e.municipality_id = naron and x.first_start is not null;

    -- As persoas: as inventadas, con praza e cos pagos variados.
    for n in 1 .. case when a.id = basic then 17 when a.id = advanced then 12 else 9 end loop
      insert into event_registration
        (id, municipality_id, event_id, status, person_subject, person_name, person_email,
         person_language, birth_date, payment_status, created_at, updated_at, version)
      values (md5('demo-registration' || a.id::text || n)::uuid, naron, a.id, 'CONFIRMED', null,
              names[1 + (n - 1) % array_length(names, 1)] || ' '
                || surnames[1 + (n * 3) % array_length(surnames, 1)],
              null, case when n % 5 = 0 then 'es' else 'gl' end,
              case when a.birth_from is not null
                   then make_date(this_year - 11, 1 + n % 12, 1 + n % 28) end,
              case when a.price is null then null
                   when n % 4 = 0 then 'PENDING' when n % 7 = 0 then 'WAIVED' else 'PAID' end,
              now() - interval '25 days' + make_interval(hours => n),
              now() - interval '25 days' + make_interval(hours => n), 0);
    end loop;
  end loop;

  -- vecino-naron: Iago na iniciación (pagada); el mesmo na andaina.
  insert into event_registration
    (id, municipality_id, event_id, status, person_subject, person_name, person_email,
     person_language, dependent_id, dependent_name, birth_year, payment_status, created_at,
     updated_at, version)
  values (md5('demo-registration-iago-basic')::uuid, naron, basic, 'CONFIRMED', vecino,
          'Veciño Narón', 'vecino-naron@example.org', 'gl', iago, 'Iago Souto Pena',
          this_year - 11, 'PAID', now() - interval '24 days', now() - interval '24 days', 0)
  on conflict do nothing;
  insert into event_registration
    (id, municipality_id, event_id, status, person_subject, person_name, person_email,
     person_language, created_at, updated_at, version)
  values (md5('demo-registration-vecino-walk')::uuid, naron, walk, 'CONFIRMED', vecino,
          'Veciño Narón', 'vecino-naron@example.org', 'gl', now() - interval '20 days',
          now() - interval '20 days', 0)
  on conflict do nothing;

  -- A lista de espera do perfeccionamento: dúas persoas inventadas e vecino-naron, a segunda.
  insert into event_registration
    (id, municipality_id, event_id, status, person_subject, person_name, person_email,
     person_language, created_at, updated_at, version)
  values (md5('demo-wait-advanced-1')::uuid, naron, advanced, 'WAITING', 'demo-espera-1',
          'Sabela Lourido', null, 'gl', now() - interval '6 days', now() - interval '6 days', 0),
         (md5('demo-wait-advanced-2')::uuid, naron, advanced, 'WAITING', vecino,
          'Veciño Narón', 'vecino-naron@example.org', 'gl', now() - interval '5 days',
          now() - interval '5 days', 0),
         (md5('demo-wait-advanced-3')::uuid, naron, advanced, 'WAITING', 'demo-espera-3',
          'Roi Freire', null, 'es', now() - interval '2 days', now() - interval '2 days', 0)
  on conflict do nothing;

  -- A lista das dúas últimas sesións pasadas da iniciación: case todas presentes.
  if not exists (select 1 from event_attendance m
                 join event_session s on s.id = m.session_id
                 where s.event_id = basic) then
    for a in
      select s.id from event_session s
       where s.event_id = basic and s.municipality_id = naron and s.ends_at < now()
       order by s.starts_at desc limit 2
    loop
      insert into event_attendance (municipality_id, session_id, registration_id, present,
                                    marked_at)
      select naron, a.id, r.id, (row_number() over (order by r.created_at, r.id)) % 6 <> 0, now()
        from event_registration r
       where r.event_id = basic and r.status = 'CONFIRMED' and r.municipality_id = naron;
      update event_session
         set attendance_taken_at = now(), attendance_saved_at = now(),
             attendance_saved_by_name = 'Recepción Narón'
       where id = a.id and municipality_id = naron;
    end loop;
  end if;
end $$;
