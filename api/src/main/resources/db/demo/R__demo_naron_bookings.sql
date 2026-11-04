-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: CC0-1.0
--
-- Día no que se aplica: ${demo_day}. Flyway volve aplicar o script cando cambia (o primeiro
-- arrinque de cada día; application-demo.yaml): as datas son relativas a current_date.
--
-- Reservas de demostración de Narón (perfil demo; plan do dominio 2, D2-08): pasadas, de hoxe e
-- futuras, con datas relativas ao día en que se aplica, para que «As miñas reservas», a lista do
-- xestor e o calendario teñan contido. Todos os estados de cobro da pantalla de reservas do xestor:
-- pendente, pagada, a devolver, devolta e canceladas (pola persoa e polo concello).
--
-- Quen reserva: o usuario de demo vecino-naron (o seu id de Keycloak está fixado no realm de demo,
-- deploy/keycloak/demo/overlay.json) e persoas ficticias sen conta, con nomes inventados.
--
-- O nome ordénase despois de R__demo_municipality_facilities.sql: Flyway aplica as repetibles pola
-- descrición, e as instalacións e os usos teñen que existir antes.
--
-- Horas que valen calquera día: o pádel abre de 8:00 a 23:00 todos os días (franxas de 90 minutos
-- desde as 8:00) e o pavillón de 10:00 a 14:00 tamén o domingo. O campo e o fútbol 7 só en días de
-- semana (a data móvese ao día laborable máis próximo) e pola tarde, na grella da tempada.
--
-- Idempotente: ids e códigos fixos; cada reserva só se engade se aínda non existe e, se está
-- confirmada, se ninguén ocupa ese oco (nunha base de demo xa usada pode habelo). A ocupación e a
-- historia van coa reserva, como as garda a API.

do $$
declare
  naron    constant uuid := '019a0b6e-0000-7000-8000-000000000001';
  -- Instalacións e usos de R__demo_municipality_facilities.sql.
  campo    constant uuid := '019a0b6e-0000-7000-8000-000000000201';
  padel    constant uuid := '019a0b6e-0000-7000-8000-000000000202';
  pavillon constant uuid := '019a0b6e-0000-7000-8000-000000000204';
  enteiro  constant uuid := '019a0b6e-0000-7000-8000-000000000231';
  futbol7  constant uuid := '019a0b6e-0000-7000-8000-000000000232';
  partido  constant uuid := '019a0b6e-0000-7000-8000-000000000234';
  central  constant uuid := '019a0b6e-0000-7000-8000-000000000236';
  transv   constant uuid := '019a0b6e-0000-7000-8000-000000000237';
  norte    constant uuid := '019a0b6e-0000-7000-8000-000000000241';
  sur      constant uuid := '019a0b6e-0000-7000-8000-000000000242';
  pista1   constant uuid := '019a0b6e-0000-7000-8000-000000000243';
  pista2   constant uuid := '019a0b6e-0000-7000-8000-000000000244';
  -- O persoal que cobra e cancela na demo.
  staff    constant text := 'demo';
  r record;
  day date;
  start_minute int;
  minutes int;
  starts timestamptz;
  ends timestamptz;
  created timestamptz;
  cancelled timestamptz;
  last_change timestamptz;
  facility uuid;
  mode text;
  price int;
  rules boolean;
  was_paid boolean;
  row_version int;
begin
  for r in
    select * from (values
      -- (id, código, días desde hoxe, uso, parte, minuto de inicio, persoa, estado, cobro)
      -- -1 como minuto = a tarde do campo enteiro (20:30 no inverno, 20:00 no verán).
      -- Pasadas.
      ('019a0b6e-0000-7000-8000-000000000401'::uuid, 'FUR-DE2A', -6, enteiro, null::uuid, -1,
       'brais', 'CONFIRMED', 'PAID'),
      ('019a0b6e-0000-7000-8000-000000000402'::uuid, 'FUR-DE2B', -5, partido, null, 1110,
       'vecino', 'CONFIRMED', 'PAID'),
      ('019a0b6e-0000-7000-8000-000000000403'::uuid, 'FUR-DE2C', -4, futbol7, norte, 1080,
       'carme', 'CONFIRMED', 'PENDING'),
      ('019a0b6e-0000-7000-8000-000000000404'::uuid, 'FUR-DE2D', -3, partido, null, 1200,
       'xoan', 'CANCELLED_BY_PERSON', 'PENDING'),
      ('019a0b6e-0000-7000-8000-000000000405'::uuid, 'FUR-DE2E', -2, central, null, 660,
       'antia', 'CANCELLED_BY_MUNICIPALITY', 'REFUNDED'),
      ('019a0b6e-0000-7000-8000-000000000406'::uuid, 'FUR-DE2F', -1, partido, null, 1020,
       'vecino', 'CONFIRMED', 'PAID'),
      -- Hoxe.
      ('019a0b6e-0000-7000-8000-000000000407'::uuid, 'FUR-DE2G', 0, partido, null, 570,
       'martino', 'CONFIRMED', 'PAID'),
      ('019a0b6e-0000-7000-8000-000000000408'::uuid, 'FUR-DE2H', 0, transv, pista1, 660,
       'roi', 'CONFIRMED', 'PENDING'),
      ('019a0b6e-0000-7000-8000-000000000409'::uuid, 'FUR-DE2J', 0, transv, pista2, 660,
       'uxia', 'CONFIRMED', 'PAID'),
      ('019a0b6e-0000-7000-8000-000000000410'::uuid, 'FUR-DE2K', 0, partido, null, 1110,
       'iria', 'CONFIRMED', 'PENDING'),
      ('019a0b6e-0000-7000-8000-000000000411'::uuid, 'FUR-DE2L', 0, partido, null, 1200,
       'vecino', 'CONFIRMED', 'PENDING'),
      -- Futuras.
      ('019a0b6e-0000-7000-8000-000000000412'::uuid, 'FUR-DE2M', 1, futbol7, sur, 1080,
       'uxia', 'CONFIRMED', 'PENDING'),
      ('019a0b6e-0000-7000-8000-000000000413'::uuid, 'FUR-DE2N', 1, futbol7, norte, 1080,
       'brais', 'CONFIRMED', 'PAID'),
      ('019a0b6e-0000-7000-8000-000000000414'::uuid, 'FUR-DE2P', 2, enteiro, null, -1,
       'xoan', 'CONFIRMED', 'PAID'),
      ('019a0b6e-0000-7000-8000-000000000415'::uuid, 'FUR-DE2Q', 2, partido, null, 1020,
       'vecino', 'CONFIRMED', 'PENDING'),
      ('019a0b6e-0000-7000-8000-000000000416'::uuid, 'FUR-DE2R', 3, central, null, 600,
       'carme', 'CANCELLED_BY_MUNICIPALITY', 'TO_REFUND'),
      ('019a0b6e-0000-7000-8000-000000000417'::uuid, 'FUR-DE2S', 4, partido, null, 1110,
       'antia', 'CANCELLED_BY_PERSON', 'TO_REFUND'),
      ('019a0b6e-0000-7000-8000-000000000418'::uuid, 'FUR-DE2T', 5, central, null, 720,
       'vecino', 'CONFIRMED', 'PAID'),
      ('019a0b6e-0000-7000-8000-000000000419'::uuid, 'FUR-DE2U', 6, partido, null, 1200,
       'iria', 'CONFIRMED', 'PENDING'))
      as b(id, code, days, use_id, part_id, minute, who, status, payment)
    join (values
      ('vecino', '019a0b6e-0000-7000-8000-000000000901', 'Veciño Narón',
       'vecino-naron@example.org', 'es'),
      ('brais', 'demo-person-brais', 'Brais Fernández', 'brais.fernandez@example.org', 'gl'),
      ('carme', 'demo-person-carme', 'Carme López', 'carme.lopez@example.org', 'gl'),
      ('xoan', 'demo-person-xoan', 'Xoán Rodríguez', 'xoan.rodriguez@example.org', 'gl'),
      ('antia', 'demo-person-antia', 'Antía Vázquez', 'antia.vazquez@example.org', 'es'),
      ('martino', 'demo-person-martino', 'Martiño Castro', 'martino.castro@example.org', 'gl'),
      ('roi', 'demo-person-roi', 'Roi Varela', 'roi.varela@example.org', 'gl'),
      ('uxia', 'demo-person-uxia', 'Uxía Pérez', 'uxia.perez@example.org', 'gl'),
      ('iria', 'demo-person-iria', 'Iria Souto', 'iria.souto@example.org', 'es'))
      as p(who, subject, name, email, language) using (who)
    order by days, minute
  loop
    continue when exists (select 1 from booking where id = r.id)
               or exists (select 1 from booking where municipality_id = naron and code = r.code);

    day := current_date + r.days;
    if r.use_id in (enteiro, futbol7) then
      -- Ao día laborable máis próximo, cara a adiante (ou cara a atrás se é pasada).
      day := day + case extract(isodow from day)::int
                     when 6 then case when r.days < 0 then -1 else 2 end
                     when 7 then case when r.days < 0 then -2 else 1 end
                     else 0 end;
    end if;
    start_minute := case when r.minute >= 0 then r.minute
                         when extract(month from day) in (7, 8) then 1200
                         else 1230 end;

    facility := null;
    select u.facility_id, u.rental_mode, u.slot_minutes, u.price_cents,
           u.rule_cancel_hours is not null
      into facility, mode, minutes, price, rules
    from facility_use u
    where u.id = r.use_id and u.municipality_id = naron;
    continue when facility is null or price is null;

    starts := (day + make_interval(mins => start_minute)) at time zone 'Europe/Madrid';
    ends := starts + make_interval(mins => minutes);
    continue when r.status = 'CONFIRMED' and exists (
      select 1 from booking_occupancy o
      where o.facility_id = facility and o.active
        and o.period && tstzrange(starts, ends, '[)')
        and (o.use_id <> r.use_id or o.rental_mode <> 'PARTS' or o.part_id = r.part_id));

    created := least(starts - interval '3 days', now() - interval '3 hours');
    was_paid := r.payment in ('PAID', 'TO_REFUND', 'REFUNDED');
    cancelled := case when r.status <> 'CONFIRMED' then created + interval '1 hour' end;
    last_change := coalesce(
      case when r.payment = 'REFUNDED' then created + interval '2 hours' end,
      cancelled,
      case when was_paid then created + interval '30 minutes' end,
      created);
    row_version := (case when was_paid then 1 else 0 end)
             + (case when cancelled is not null then 1 else 0 end)
             + (case when r.payment = 'REFUNDED' then 1 else 0 end);

    -- O campo e o fútbol 7 levan as normas do uso conxeladas (gratis ata 24 horas antes).
    insert into booking (id, municipality_id, code, facility_id, use_id, part_id, rental_mode,
                         period, slots, status, payment_status, price_kind, amount_cents,
                         cancel_until, rule_min_advance_hours, rule_max_advance_days,
                         rule_max_slots, rule_cap_count, rule_cap_period, rule_cancel_hours,
                         holder_subject, holder_name, holder_email, holder_language,
                         cancelled_at, cancelled_by, created_at, updated_at, version)
    values (r.id, naron, r.code, facility, r.use_id, r.part_id, mode,
            tstzrange(starts, ends, '[)'), 1, r.status, r.payment, 'PAID', price,
            case when rules then starts - interval '24 hours' else starts end,
            case when rules then 2 end, case when rules then 7 end,
            case when rules then case when r.use_id = enteiro then 1 else 2 end end,
            case when rules then 2 end, case when rules then 'WEEK' end,
            case when rules then 24 end,
            r.subject, r.name, r.email, r.language,
            cancelled,
            case r.status when 'CANCELLED_BY_PERSON' then r.subject
                          when 'CANCELLED_BY_MUNICIPALITY' then staff end,
            created, last_change, row_version);

    insert into booking_occupancy (id, municipality_id, booking_id, facility_id, use_id, part_id,
                                   rental_mode, period, active)
    values (uuidv7(), naron, r.id, facility, r.use_id, r.part_id, mode,
            tstzrange(starts, ends, '[)'), r.status = 'CONFIRMED');

    insert into booking_event (id, municipality_id, booking_id, kind, at, actor_subject)
    values (uuidv7(), naron, r.id, 'CREATED', created, r.subject);
    if was_paid then
      insert into booking_event (id, municipality_id, booking_id, kind, at, actor_subject)
      values (uuidv7(), naron, r.id, 'PAID', created + interval '30 minutes', staff);
    end if;
    if r.status = 'CANCELLED_BY_PERSON' then
      insert into booking_event (id, municipality_id, booking_id, kind, at, actor_subject)
      values (uuidv7(), naron, r.id, 'CANCELLED_BY_PERSON', cancelled, r.subject);
    elsif r.status = 'CANCELLED_BY_MUNICIPALITY' then
      insert into booking_event (id, municipality_id, booking_id, kind, at, actor_subject,
                                 channels)
      values (uuidv7(), naron, r.id, 'CANCELLED_BY_MUNICIPALITY', cancelled, staff,
              array['EMAIL', 'WEB']);
    end if;
    if r.payment = 'REFUNDED' then
      insert into booking_event (id, municipality_id, booking_id, kind, at, actor_subject)
      values (uuidv7(), naron, r.id, 'REFUNDED', created + interval '2 hours', staff);
    end if;
  end loop;
end
$$;
