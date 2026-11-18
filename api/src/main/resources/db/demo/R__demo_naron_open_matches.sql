-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: CC0-1.0
--
-- Día no que se aplica: ${demo_day}. Flyway volve aplicar o script cando cambia (o primeiro
-- arrinque de cada día; application-demo.yaml).
--
-- Partidas abertas de demostración de Narón (perfil demo, ADR-0040): «Buscar xente para xogar»
-- aceso con 12 horas e dúas partidas sobre reservas futuras de R__demo_naron_bookings.sql (o nome
-- ordénase despois: Flyway aplica as repetibles pola descrición).
--
-- - Fútbol sala na pista central do pavillón, sen aprobación: organiza o usuario de demo
--   vecino-naron; van 4 de 10 (dúas persoas da reserva e dúas que se uniron).
-- - Pádel, con aprobación: organiza unha persoa ficticia; van 3 de 4 e hai unha solicitude
--   pendente.
--
-- Nomes ficticios. Idempotente: ids fixos; cada partida só se engade se a súa reserva existe, está
-- confirmada e aínda non empezou, e se a partida aínda non existe. Nunca falla o arranque.

do $$
declare
  naron    constant uuid := '019a0b6e-0000-7000-8000-000000000001';
  futsal   constant uuid := '019a0b6e-0000-7000-8000-000000000418';
  padel    constant uuid := '019a0b6e-0000-7000-8000-000000000419';
  match1   constant uuid := '019a0b6e-0000-7000-8000-000000000a01';
  match2   constant uuid := '019a0b6e-0000-7000-8000-000000000a02';
  b record;
begin
  -- A política só se acende a primeira vez: despois manda o que garde o xestor.
  update municipality
     set open_match_since = now(), open_match_cancel_hours = 12,
         open_match_policy_changed_at = now()
   where id = naron and open_match_since is null and open_match_policy_changed_at is null;

  select id, holder_subject, holder_name into b
    from booking
   where municipality_id = naron and id = futsal and status = 'CONFIRMED'
     and lower(period) > now() and holder_subject is not null;
  if found and not exists (select 1 from booking_open_match where id = match1) then
    insert into booking_open_match (id, municipality_id, booking_id, organizer_subject,
                                    organizer_name, known_count, minimum, maximum, level, title,
                                    description, approval, status, created_at, updated_at,
                                    version)
    values (match1, naron, futsal, b.holder_subject, b.holder_name, 2, 6, 10, 'INTERMEDIATE',
            'Pachanga de fútbol sala',
            'Buscamos xente para facer dous equipos. Trae unha camiseta clara e outra escura.',
            false, 'OPEN', now(), now(), 0);
    insert into booking_open_match_player (id, municipality_id, match_id, account_subject, email,
                                           name, language, status, created_at, decided_at)
    values ('019a0b6e-0000-7000-8000-000000000a11', naron, match1, 'demo-person-brais',
            'brais.fernandez@example.org', 'Brais Fernández', 'gl', 'CONFIRMED', now(), now()),
           ('019a0b6e-0000-7000-8000-000000000a12', naron, match1, 'demo-person-carme',
            'carme.lopez@example.org', 'Carme López', 'gl', 'CONFIRMED', now(), now());
  end if;

  select id, holder_subject, holder_name into b
    from booking
   where municipality_id = naron and id = padel and status = 'CONFIRMED'
     and lower(period) > now() and holder_subject is not null;
  if found and not exists (select 1 from booking_open_match where id = match2) then
    insert into booking_open_match (id, municipality_id, booking_id, organizer_subject,
                                    organizer_name, known_count, minimum, maximum, level, title,
                                    description, approval, status, created_at, updated_at,
                                    version)
    values (match2, naron, padel, b.holder_subject, b.holder_name, 2, 4, 4, 'BEGINNER', null,
            'Partido tranquilo de 90 minutos para quen está empezando.', true, 'OPEN', now(),
            now(), 0);
    insert into booking_open_match_player (id, municipality_id, match_id, account_subject, email,
                                           name, language, status, created_at, decided_at)
    values ('019a0b6e-0000-7000-8000-000000000a21', naron, match2, 'demo-person-xoan',
            'xoan.rodriguez@example.org', 'Xoán Rodríguez', 'gl', 'CONFIRMED', now(), now()),
           ('019a0b6e-0000-7000-8000-000000000a22', naron, match2, 'demo-person-antia',
            'antia.vazquez@example.org', 'Antía Vázquez', 'es', 'PENDING', now(), null);
  end if;
end $$;
