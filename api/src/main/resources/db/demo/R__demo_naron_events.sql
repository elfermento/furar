-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: CC0-1.0
--
-- Día no que se aplica: ${demo_day}. Flyway volve aplicar o script cando cambia (o primeiro
-- arrinque de cada día; application-demo.yaml): as datas son relativas a current_date.
--
-- Eventos de exemplo de Narón (perfil demo, ADR-0038): cinco publicados e un borrador.
--   - Torneo 3x3 de baloncesto (Pavillón de Piñeiros): 48 prazas, completo.
--   - Xornada de portas abertas (Campo de fútbol 1): sen límite de prazas, gratis.
--   - Andaina polo río Freixeiro (outro sitio): 80 prazas, «Apuntarse ata» dous días antes.
--   - Carreira popular de Nadal (Campo de fútbol 1): 200 prazas, 5,00 €; vecino-naron está
--     apuntado, co pago pendente.
--   - Clase aberta de pádel (Pista de pádel 1): 8 prazas, «Con aprobación», dúas solicitudes.
--   - Exhibición de patinaxe de Reis (Pavillón de Piñeiros): borrador.
-- As persoas apuntadas, salvo vecino-naron, non teñen conta: só un nome inventado (nome e apelido
-- que saen de listas, sen ninguén real).
--
-- Idempotente: os ids saen dun texto fixo; cada aplicación pon as datas de novo e volve crear as
-- inscricións destes eventos.

do $$
declare
  naron    constant uuid := '019a0b6e-0000-7000-8000-000000000001';
  football constant uuid := '019a0b6e-0000-7000-8000-000000000201';
  padel    constant uuid := '019a0b6e-0000-7000-8000-000000000202';
  pavillon constant uuid := '019a0b6e-0000-7000-8000-000000000204';
  vecino   constant text := '019a0b6e-0000-7000-8000-000000000901';
  tournament constant uuid := md5('demo-event-tournament')::uuid;
  open_day   constant uuid := md5('demo-event-open-day')::uuid;
  walk       constant uuid := md5('demo-event-walk')::uuid;
  race       constant uuid := md5('demo-event-race')::uuid;
  padel_class constant uuid := md5('demo-event-padel-class')::uuid;
  skating    constant uuid := md5('demo-event-skating')::uuid;
  names    constant text[] := array['Antía', 'Brais', 'Carme', 'Iago', 'Noa', 'Xoán', 'Uxía',
                                    'Martiño', 'Sabela', 'Lois', 'Iria', 'Roi', 'Alba', 'Breixo'];
  surnames constant text[] := array['Souto', 'Castro', 'Pena', 'Fernández', 'Rodríguez',
                                    'Iglesias', 'Barreiro', 'Muíños', 'Lourido', 'Freire'];
  stamp    constant timestamptz := date_trunc('minute', now());
  r record;
begin
  insert into event (id, municipality_id, name_gl, name_es, description_gl, description_es,
                     starts_at, ends_at, facility_id, place, capacity, price_cents,
                     registration_deadline, published, approval, cancelled_at, created_at,
                     updated_at, version)
  values
    (tournament, naron, 'Torneo 3x3 de baloncesto', 'Torneo 3x3 de baloncesto',
     'Torneo por equipos de tres, de 14 anos en diante. Cada equipo apunta as súas tres persoas.',
     'Torneo por equipos de tres, de 14 años en adelante. Cada equipo apunta a sus tres personas.',
     ((current_date + 17) + time '10:00') at time zone 'Europe/Madrid',
     ((current_date + 17) + time '10:00') at time zone 'Europe/Madrid', pavillon, null, 48, null,
     null, true, false, null, stamp - interval '20 days', stamp - interval '20 days', 0),
    (open_day, naron, 'Xornada de portas abertas', 'Jornada de puertas abiertas',
     'Proba os deportes do campo coas escolas deportivas municipais. Para todas as idades.',
     'Prueba los deportes del campo con las escuelas deportivas municipales. Para todas las edades.',
     ((current_date + 24) + time '10:00') at time zone 'Europe/Madrid',
     ((current_date + 24) + time '10:00') at time zone 'Europe/Madrid', football, null, null, null,
     null, true, false, null, stamp - interval '15 days', stamp - interval '15 days', 0),
    (walk, naron, 'Andaina polo río Freixeiro', 'Caminata por el río Freixeiro',
     'Andaina de 8 km pola beira do río, a ritmo tranquilo. Leva auga e calzado cómodo.',
     'Caminata de 8 km por la orilla del río, a ritmo tranquilo. Lleva agua y calzado cómodo.',
     ((current_date + 32) + time '10:30') at time zone 'Europe/Madrid',
     ((current_date + 32) + time '10:30') at time zone 'Europe/Madrid', null,
     'Saída da Praza de Galicia', 80, null,
     ((current_date + 30) + time '20:00') at time zone 'Europe/Madrid',
     true, false, null, stamp - interval '10 days', stamp - interval '10 days', 0),
    (race, naron, 'Carreira popular de Nadal', 'Carrera popular de Navidad',
     'Carreira de 5 km arredor do campo, aberta a todas as idades. Haberá chocolate quente na meta.',
     'Carrera de 5 km alrededor del campo, abierta a todas las edades. Habrá chocolate caliente en la meta.',
     ((current_date + 39) + time '11:00') at time zone 'Europe/Madrid',
     ((current_date + 39) + time '11:00') at time zone 'Europe/Madrid', football, null, 200, 500,
     null, true, false, null, stamp - interval '8 days', stamp - interval '8 days', 0),
    (padel_class, naron, 'Clase aberta de pádel', 'Clase abierta de pádel',
     'Unha hora co monitor para quen nunca xogou. O concello confirma as prazas por orde.',
     'Una hora con el monitor para quien nunca jugó. El ayuntamiento confirma las plazas por orden.',
     ((current_date + 12) + time '18:00') at time zone 'Europe/Madrid',
     ((current_date + 12) + time '18:00') at time zone 'Europe/Madrid', padel, null, 8, null,
     null, true, true, null, stamp - interval '5 days', stamp - interval '5 days', 0),
    (skating, naron, 'Exhibición de patinaxe de Reis', 'Exhibición de patinaje de Reyes',
     null, null,
     ((current_date + 58) + time '17:00') at time zone 'Europe/Madrid',
     ((current_date + 58) + time '17:00') at time zone 'Europe/Madrid', pavillon, null, null, null,
     null, false, false, null, stamp - interval '2 days', stamp - interval '2 days', 0)
  on conflict (id) do update
    set starts_at = excluded.starts_at, ends_at = excluded.ends_at,
        registration_deadline = excluded.registration_deadline,
        name_gl = excluded.name_gl, name_es = excluded.name_es,
        description_gl = excluded.description_gl, description_es = excluded.description_es,
        facility_id = excluded.facility_id, place = excluded.place,
        capacity = excluded.capacity, price_cents = excluded.price_cents,
        published = excluded.published, approval = excluded.approval, cancelled_at = null,
        created_at = excluded.created_at, updated_at = excluded.updated_at;

  delete from event_registration
   where municipality_id = naron
     and event_id in (tournament, open_day, walk, race, padel_class, skating);

  -- As persoas sen conta: n inscricións confirmadas (ou pendentes) por evento.
  for r in
    select * from (values (tournament, 48, 0, false), (open_day, 23, 0, false),
                          (walk, 61, 0, false), (race, 161, 0, true), (padel_class, 3, 2, false))
                  as t(event_id, confirmed, pending, paid)
  loop
    insert into event_registration
      (id, municipality_id, event_id, status, person_subject, person_name, person_email,
       person_language, payment_status, decided_at, created_at, updated_at, version)
    select md5('demo-registration' || r.event_id::text || n)::uuid, naron, r.event_id,
           case when n > r.confirmed then 'PENDING' else 'CONFIRMED' end,
           null,
           names[1 + (n * 7) % array_length(names, 1)] || ' '
             || surnames[1 + (n * 3) % array_length(surnames, 1)],
           null,
           case when n % 5 = 0 then 'es' else 'gl' end,
           case when r.paid then (case when n % 3 = 0 then 'PENDING' else 'PAID' end) end,
           null,
           stamp - (n || ' hours')::interval,
           stamp - (n || ' hours')::interval,
           0
    from generate_series(1, r.confirmed + r.pending) as n;
  end loop;

  -- vecino-naron, apuntado á carreira co pago pendente (o seu nome e correo, coma ao apuntarse).
  insert into event_registration
    (id, municipality_id, event_id, status, person_subject, person_name, person_email,
     person_language, payment_status, decided_at, created_at, updated_at, version)
  values (md5('demo-registration-vecino-race')::uuid, naron, race, 'CONFIRMED', vecino,
          'Veciño Narón', 'vecino-naron@example.org', 'es', 'PENDING', null,
          stamp - interval '1 day', stamp - interval '1 day', 0);
end
$$;
