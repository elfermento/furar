-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: CC0-1.0
--
-- Día no que se aplica: ${demo_day}. Flyway volve aplicar o script cando cambia (o primeiro
-- arrinque de cada día; application-demo.yaml): as datas son relativas a current_date.
--
-- Instalacións de demostración (perfil demo). Narón: Campo de fútbol 1
-- de O Alto, pistas de pádel 1 e 2 de O Alto, Pavillón de Piñeiros e Piscina municipal de Narón
-- (desde o 2026-11-16, para as actividades con horario de R__demo_naron_schools.sql). Ferrol: Pista de atletismo
-- da Malata e, a propósito, a Piscina municipal en borrador (sen deportes, usos nin horario) para
-- ver o aviso do que falta. Enderezos reais do buscador fixo (FixedAddressSearch).
--
-- Para ensinar as persoas a cargo, os non presentados e os ocos parecidos (ADR-0031 a ADR-0033):
-- as dúas pistas de pádel resérvanse por internet co mesmo uso («Partido», 90 minutos), o pavillón
-- ten un uso por prazas con prezo por persoa («Bádminton libre») e o campo de fútbol ten control
-- de entrada desde que se carga a demo (as demais instalacións, non).
--
-- O nome do ficheiro ordénase despois de R__demo_municipalities.sql: Flyway aplica as repetibles
-- pola descrición, e os concellos teñen que existir antes. Sen fotos: cada instalación mostra o
-- debuxo de Furar que lle suxire o seu nome (o Pavillón de Piñeiros, o pavillón polideportivo).
--
-- As datas son relativas ao día en que se aplica: tempadas do curso en curso e do seguinte, para
-- que ningunha instalación caia en borrador por falta de horario. Idempotente: ids fixos e
-- «on conflict do nothing»; os tramos da semana só se a tempada aínda non ten ningún.

insert into facility (id, municipality_id, name, status, type, description, ridega_code, phone,
                      email, online_booking, show_free_slots, covered, street, street_number,
                      postal_code, locality, latitude, longitude, created_at,
                      updated_at, version)
values ('019a0b6e-0000-7000-8000-000000000201', '019a0b6e-0000-7000-8000-000000000001',
        'Campo de fútbol 1 de O Alto', 'ACTIVE', 'FIELD',
        '{"gl": "Campo de herba artificial de fútbol 11, divisible en dous de fútbol 7. Vestiarios e bancadas cubertas.",
          "es": "Campo de hierba artificial de fútbol 11, divisible en dos de fútbol 7. Vestuarios y gradas cubiertas."}',
        '15-054-0012', '981 000 101', 'deportes@naron.example', true, false, false,
        'Rúa Buxo', '16', '15572', 'Narón', 43.510072, -8.191273, now(), now(), 0),
       ('019a0b6e-0000-7000-8000-000000000202', '019a0b6e-0000-7000-8000-000000000001',
        'Pista de pádel 1 de O Alto', 'ACTIVE', 'PADEL_COURT',
        '{"gl": "Pista de pádel cuberta, con paredes de vidro e luz artificial.",
          "es": "Pista de pádel cubierta, con paredes de cristal y luz artificial."}',
        '15-054-0013', '981 000 101', 'deportes@naron.example', true, false, true,
        'Rúa Buxo', '16', '15572', 'Narón', 43.510072, -8.191273, now(), now(), 0),
       ('019a0b6e-0000-7000-8000-000000000203', '019a0b6e-0000-7000-8000-000000000001',
        'Pista de pádel 2 de O Alto', 'ACTIVE', 'PADEL_COURT',
        '{"gl": "Pista de pádel ao aire libre, xunto á pista 1.",
          "es": "Pista de pádel al aire libre, junto a la pista 1."}',
        null, '981 000 101', 'deportes@naron.example', true, false, false,
        'Rúa Buxo', '16', '15572', 'Narón', 43.510072, -8.191273, now(), now(), 0),
       ('019a0b6e-0000-7000-8000-000000000204', '019a0b6e-0000-7000-8000-000000000001',
        'Pavillón de Piñeiros', 'ACTIVE', 'SPORTS_HALL',
        '{"gl": "Pavillón cuberto cunha pista central que se divide en tres pistas transversais.",
          "es": "Pabellón cubierto con una pista central que se divide en tres pistas transversales."}',
        '15-054-0004', '981 000 102', 'pavillons@naron.example', true, false, true,
        'Estrada de Castela', '100', '15570', 'Narón', 43.497415, -8.200025, now(), now(),
        0),
       ('019a0b6e-0000-7000-8000-000000000205', '019a0b6e-0000-7000-8000-000000000002',
        'Pista de atletismo da Malata', 'ACTIVE', 'OTHER',
        '{"gl": "Pista de atletismo de seis rúas con céspede no centro. Uso libre con reserva de praza.",
          "es": "Pista de atletismo de seis calles con césped en el centro. Uso libre con reserva de plaza."}',
        '15-036-0021', '981 000 201', 'deportes@ferrol.example', true, false, false,
        'Avenida de Esteiro', '10', '15403', 'Ferrol', 43.482787, -8.226911, now(), now(),
        0),
       ('019a0b6e-0000-7000-8000-000000000206', '019a0b6e-0000-7000-8000-000000000002',
        'Piscina municipal', 'ACTIVE', 'POOL', null, null, null, null, true, false, true,
        'Rúa Real', '10', '15402', 'Ferrol', 43.485765, -8.229473, now(), now(), 0),
       ('019a0b6e-0000-7000-8000-000000000207', '019a0b6e-0000-7000-8000-000000000001',
        'Piscina municipal de Narón', 'ACTIVE', 'POOL',
        '{"gl": "Piscina cuberta de 25 metros con seis rúas e vaso de ensino. Cursos de natación das escolas deportivas municipais.",
          "es": "Piscina cubierta de 25 metros con seis calles y vaso de enseñanza. Cursos de natación de las escuelas deportivas municipales."}',
        '15-054-0015', '981 000 101', 'deportes@naron.example', false, false, true,
        'Rúa Buxo', '16', '15572', 'Narón', 43.510072, -8.191273, now(), now(), 0)
on conflict do nothing;

-- Control de entrada (ADR-0032) só no campo de fútbol, desde que se carga a demo: as reservas que
-- empecen despois e rematen sen validar pasan soas a «non presentado». Nas demais instalacións a
-- falta márcaa o persoal na ficha da reserva. Só se aínda non o ten.
update facility
set entry_control_since = date_trunc('minute', now())
where id = '019a0b6e-0000-7000-8000-000000000201' and entry_control_since is null;

-- Un campo propio das instalacións de Narón (bloque 1b): «Aparcadoiro de bicis», público e non
-- obrigatorio, para que a tarxeta Datos ensine campos propios. Sí no campo e na pista 1, non no
-- pavillón; a pista 2 queda sen valor.
insert into custom_field (id, municipality_id, code, type, label, help, personal_data, status,
                          created_at, updated_at, version)
values ('019a0b6e-0000-7000-8000-000000000103', '019a0b6e-0000-7000-8000-000000000001',
        'bike_parking', 'BOOLEAN', '{"gl": "Aparcadoiro de bicis", "es": "Aparcamiento de bicis"}',
        null, false, 'ACTIVE', '2026-10-22T08:00:00Z', '2026-10-22T08:00:00Z', 0)
on conflict (id) do nothing;

insert into custom_field_usage (id, municipality_id, field_id, target, required, visibility,
                                personal_data)
values ('019a0b6e-0000-7000-8000-000000000113', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000103', 'FACILITY', false, 'PUBLIC', false)
on conflict do nothing;

insert into custom_field_value (id, municipality_id, field_id, field_type, target, entity_id,
                                bool_value)
values ('019a0b6e-0000-7000-8000-000000000123', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000103', 'BOOLEAN', 'FACILITY',
        '019a0b6e-0000-7000-8000-000000000201', true),
       ('019a0b6e-0000-7000-8000-000000000124', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000103', 'BOOLEAN', 'FACILITY',
        '019a0b6e-0000-7000-8000-000000000202', true),
       ('019a0b6e-0000-7000-8000-000000000125', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000103', 'BOOLEAN', 'FACILITY',
        '019a0b6e-0000-7000-8000-000000000204', false)
on conflict do nothing;

insert into facility_sport (id, municipality_id, facility_id, sport_code, position)
values ('019a0b6e-0000-7000-8000-000000000211', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000201', 'football', 0),
       ('019a0b6e-0000-7000-8000-000000000212', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000201', 'football_7', 1),
       ('019a0b6e-0000-7000-8000-000000000213', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000202', 'padel', 0),
       ('019a0b6e-0000-7000-8000-000000000214', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000203', 'padel', 0),
       ('019a0b6e-0000-7000-8000-000000000215', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000204', 'basketball', 0),
       ('019a0b6e-0000-7000-8000-000000000216', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000204', 'futsal', 1),
       ('019a0b6e-0000-7000-8000-000000000217', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000204', 'handball', 2),
       ('019a0b6e-0000-7000-8000-000000000218', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000204', 'volleyball', 3),
       ('019a0b6e-0000-7000-8000-000000000219', '019a0b6e-0000-7000-8000-000000000002',
        '019a0b6e-0000-7000-8000-000000000205', 'athletics', 0),
       ('019a0b6e-0000-7000-8000-00000000021a', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000207', 'swimming', 0)
on conflict do nothing;

insert into facility_amenity (municipality_id, facility_id, code)
select m, f::uuid, code
from (values ('019a0b6e-0000-7000-8000-000000000001'::uuid,
              '019a0b6e-0000-7000-8000-000000000201',
              array['CHANGING_ROOMS', 'SHOWERS', 'COVERED_STANDS', 'PARKING', 'LIGHTING',
                    'STEP_FREE_ACCESS', 'ACCESSIBLE_TOILET']),
             ('019a0b6e-0000-7000-8000-000000000001'::uuid,
              '019a0b6e-0000-7000-8000-000000000202',
              array['CHANGING_ROOMS', 'PARKING', 'LIGHTING', 'STEP_FREE_ACCESS']),
             ('019a0b6e-0000-7000-8000-000000000001'::uuid,
              '019a0b6e-0000-7000-8000-000000000203', array['PARKING', 'LIGHTING']),
             ('019a0b6e-0000-7000-8000-000000000001'::uuid,
              '019a0b6e-0000-7000-8000-000000000204',
              array['CHANGING_ROOMS', 'SHOWERS', 'COVERED_STANDS', 'CAFE', 'STEP_FREE_ACCESS',
                    'ACCESSIBLE_TOILET', 'ACCESSIBLE_CHANGING_ROOM', 'HEARING_LOOP']),
             ('019a0b6e-0000-7000-8000-000000000002'::uuid,
              '019a0b6e-0000-7000-8000-000000000205',
              array['CHANGING_ROOMS', 'SHOWERS', 'LIGHTING', 'STEP_FREE_ACCESS']))
         as a(m, f, codes),
     unnest(codes) as code
on conflict do nothing;

-- Usos: enteira, por partes e por prazas; un só uso «que bloquea» (Torneo de Nadal, con datas).
insert into facility_use (id, municipality_id, facility_id, name, rental_mode, capacity,
                          slot_minutes, availability, blocks_others, created_at, updated_at,
                          version)
values ('019a0b6e-0000-7000-8000-000000000231', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000201', 'Campo enteiro', 'WHOLE', null, 90, 'ALWAYS',
        false, now(), now(), 0),
       ('019a0b6e-0000-7000-8000-000000000232', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000201', 'Fútbol 7', 'PARTS', null, 60, 'ALWAYS', false,
        now(), now(), 0),
       ('019a0b6e-0000-7000-8000-000000000233', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000201', 'Torneo de Nadal', 'WHOLE', null, 120, 'DATES',
        true, now(), now(), 0),
       ('019a0b6e-0000-7000-8000-000000000234', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000202', 'Partido', 'WHOLE', null, 90, 'ALWAYS', false,
        now(), now(), 0),
       ('019a0b6e-0000-7000-8000-000000000235', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000203', 'Partido', 'WHOLE', null, 90, 'ALWAYS', false,
        now(), now(), 0),
       ('019a0b6e-0000-7000-8000-000000000236', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000204', 'Pista central', 'WHOLE', null, 60, 'ALWAYS',
        false, now(), now(), 0),
       ('019a0b6e-0000-7000-8000-000000000237', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000204', 'Pistas transversais', 'PARTS', null, 60,
        'ALWAYS', false, now(), now(), 0),
       ('019a0b6e-0000-7000-8000-000000000238', '019a0b6e-0000-7000-8000-000000000002',
        '019a0b6e-0000-7000-8000-000000000205', 'Entrenamento libre', 'SEATS', 40, 60, 'ALWAYS',
        false, now(), now(), 0),
       ('019a0b6e-0000-7000-8000-000000000239', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000204', 'Bádminton libre', 'SEATS', 12, 60, 'ALWAYS',
        false, now(), now(), 0),
       -- A piscina de Narón non se reserva por internet: o vaso de ensino é para os cursos.
       ('019a0b6e-0000-7000-8000-00000000023a', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000207', 'Vaso de ensino', 'WHOLE', null, 60, 'ALWAYS',
        false, now(), now(), 0)
on conflict do nothing;

insert into facility_use_part (id, municipality_id, use_id, name, position)
values ('019a0b6e-0000-7000-8000-000000000241', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000232', 'Metade norte', 0),
       ('019a0b6e-0000-7000-8000-000000000242', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000232', 'Metade sur', 1),
       ('019a0b6e-0000-7000-8000-000000000243', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000237', 'Pista 1', 0),
       ('019a0b6e-0000-7000-8000-000000000244', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000237', 'Pista 2', 1),
       ('019a0b6e-0000-7000-8000-000000000245', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000237', 'Pista 3', 2)
-- Co id: a exclusión dos nomes é diferible e non pode facer de árbitro.
on conflict (id) do nothing;

-- Prezos e normas dos usos e normas da instalación (da ficha
-- pública). Con «update» e non no «insert»: así tamén chegan a unha base de demo xa cargada, e só se o
-- uso segue sen prezo (non pisa o que cambiase o xestor). O
-- campo de O Alto leva as catro normas (de 2 horas a 7 días antes, 2 reservas por semana, gratis
-- ata 24 horas antes; Fútbol 7, ata 2 franxas seguidas); o resto, só prezo; o Torneo de Nadal,
-- sen prezo nin normas. Dos dous usos por prazas, o de Ferrol é de balde e o de Narón custa 3,00 €
-- por persoa: unha reserva de varias prazas leva o importe das prazas (ADR-0031).
update facility_use
set price_kind = 'PAID', price_cents = 6000, rule_min_advance_hours = 2,
    rule_max_advance_days = 7, rule_max_slots = 1, rule_cap_count = 2, rule_cap_period = 'WEEK',
    rule_cancel_hours = 24
where id = '019a0b6e-0000-7000-8000-000000000231'
  and price_kind = 'NONE';

update facility_use
set price_kind = 'PAID', price_cents = 3000, rule_min_advance_hours = 2,
    rule_max_advance_days = 7, rule_max_slots = 2, rule_cap_count = 2, rule_cap_period = 'WEEK',
    rule_cancel_hours = 24
where id = '019a0b6e-0000-7000-8000-000000000232'
  and price_kind = 'NONE';

update facility_use set price_kind = 'PAID', price_cents = 800
where id in ('019a0b6e-0000-7000-8000-000000000234', '019a0b6e-0000-7000-8000-000000000235')
  and price_kind = 'NONE';

update facility_use set price_kind = 'PAID', price_cents = 2500
where id = '019a0b6e-0000-7000-8000-000000000236'
  and price_kind = 'NONE';

update facility_use set price_kind = 'PAID', price_cents = 1000
where id = '019a0b6e-0000-7000-8000-000000000237'
  and price_kind = 'NONE';

update facility_use set price_kind = 'FREE', price_cents = null
where id = '019a0b6e-0000-7000-8000-000000000238'
  and price_kind = 'NONE';

update facility_use set price_kind = 'PAID', price_cents = 300
where id = '019a0b6e-0000-7000-8000-000000000239'
  and price_kind = 'NONE';

-- Deportes dos usos (ADR-0025, revisión do 2026-11-18): o campo enteiro e o torneo, fútbol; as
-- metades, fútbol 7; as pistas transversais do pavillón, baloncesto e voleibol.
-- Só se o uso segue con todos os deportes da instalación (non pisa o que cambiase o xestor).
update facility_use u
set sports = v.sports
from (values ('019a0b6e-0000-7000-8000-000000000231', array['football']::text[]),
             ('019a0b6e-0000-7000-8000-000000000232', array['football_7']::text[]),
             ('019a0b6e-0000-7000-8000-000000000233', array['football']::text[]),
             ('019a0b6e-0000-7000-8000-000000000237', array['basketball', 'volleyball']::text[]))
         as v(id, sports)
where u.id = v.id::uuid
  and (u.sports = '{}'::text[]
       or u.sports = (select array_agg(coalesce(s.sport_code, s.custom_sport_id::text)
                                       order by s.position)
                        from facility_sport s
                       where s.municipality_id = u.municipality_id
                         and s.facility_id = u.facility_id));

update facility
set house_rules = '{"gl": "É obrigatorio usar calzado de herba artificial (tacos curtos ou multitaco). Non se permite entrar con comida nin bebidas en envases de vidro. As persoas menores de 14 anos deben ir acompañadas dunha persoa adulta. O concello non se fai responsable dos obxectos esquecidos nos vestiarios.",
                    "es": "Es obligatorio usar calzado de hierba artificial (tacos cortos o multitaco). No se permite entrar con comida ni bebidas en envases de vidrio. Los menores de 14 años deben ir acompañados de una persona adulta. El concello no se hace responsable de los objetos olvidados en los vestuarios."}'
where id = '019a0b6e-0000-7000-8000-000000000201' and house_rules is null;

-- Horario, períodos do uso, excepcións e o bloqueo: datas relativas ao curso en curso (de
-- setembro a agosto). Semana: 1 = luns … 7 = domingo; 8 = festivos (copia do domingo).
do $$
declare
  naron  constant uuid := '019a0b6e-0000-7000-8000-000000000001';
  ferrol constant uuid := '019a0b6e-0000-7000-8000-000000000002';
  y      constant int  := extract(year from current_date - interval '8 months')::int;
  winter constant text := format('Inverno %s-%s', y, (y + 1) % 100);
  next_winter constant text := format('Inverno %s-%s', y + 1, (y + 2) % 100);
  summer constant text := format('Verán %s', y + 1);
  -- Tramos (día, inicio, fin) en minutos desde a medianoite.
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
  -- A piscina de Narón só abre polas tardes para os cursos e os sábados pola mañá.
  pool_week constant int[] := array[
    [1, 960, 1260], [2, 960, 1260], [3, 960, 1260], [4, 960, 1260], [5, 960, 1260],
    [6, 600, 840]];
  track_week constant int[] := array[
    [1, 450, 1320], [2, 450, 1320], [3, 450, 1320], [4, 450, 1320], [5, 450, 1320],
    [6, 540, 1200], [7, 540, 840], [8, 540, 840]];
  s record;
  r int;
  block_day constant date := current_date + 10;
begin
  -- Tempadas: (id, municipio, instalación, nome, desde, ata, semana).
  for s in
    select * from (values
      ('019a0b6e-0000-7000-8000-000000000261'::uuid, naron,
       '019a0b6e-0000-7000-8000-000000000201'::uuid, winter, make_date(y, 9, 1),
       make_date(y + 1, 6, 30), long_week),
      ('019a0b6e-0000-7000-8000-000000000262'::uuid, naron,
       '019a0b6e-0000-7000-8000-000000000201'::uuid, summer, make_date(y + 1, 7, 1),
       make_date(y + 1, 8, 31), summer_week),
      ('019a0b6e-0000-7000-8000-000000000263'::uuid, naron,
       '019a0b6e-0000-7000-8000-000000000201'::uuid, next_winter, make_date(y + 1, 9, 1),
       make_date(y + 2, 6, 30), long_week),
      ('019a0b6e-0000-7000-8000-000000000264'::uuid, naron,
       '019a0b6e-0000-7000-8000-000000000202'::uuid, 'Todo o ano', make_date(y, 9, 1),
       make_date(y + 2, 8, 31), padel_week),
      ('019a0b6e-0000-7000-8000-000000000265'::uuid, naron,
       '019a0b6e-0000-7000-8000-000000000203'::uuid, 'Todo o ano', make_date(y, 9, 1),
       make_date(y + 2, 8, 31), padel_week),
      ('019a0b6e-0000-7000-8000-000000000266'::uuid, naron,
       '019a0b6e-0000-7000-8000-000000000204'::uuid, winter, make_date(y, 9, 1),
       make_date(y + 1, 6, 30), long_week),
      ('019a0b6e-0000-7000-8000-000000000267'::uuid, naron,
       '019a0b6e-0000-7000-8000-000000000204'::uuid, next_winter, make_date(y + 1, 9, 1),
       make_date(y + 2, 6, 30), long_week),
      ('019a0b6e-0000-7000-8000-000000000268'::uuid, ferrol,
       '019a0b6e-0000-7000-8000-000000000205'::uuid, 'Todo o ano', make_date(y, 9, 1),
       make_date(y + 2, 8, 31), track_week),
      ('019a0b6e-0000-7000-8000-000000000269'::uuid, naron,
       '019a0b6e-0000-7000-8000-000000000207'::uuid, 'Todo o ano', make_date(y, 9, 1),
       make_date(y + 2, 8, 31), pool_week))
      as t(id, m, facility, name, start_date, end_date, week)
  loop
    insert into season (id, municipality_id, facility_id, name, start_date, end_date,
                        created_at, updated_at, version)
    values (s.id, s.m, s.facility, s.name, s.start_date, s.end_date, now(), now(), 0)
    on conflict do nothing;
    if not exists (select 1 from season_hours where season_id = s.id) then
      for r in 1 .. array_length(s.week, 1) loop
        insert into season_hours (id, municipality_id, season_id, day, start_minute, end_minute)
        values (uuidv7(), s.m, s.id, s.week[r][1], s.week[r][2], s.week[r][3]);
      end loop;
    end if;
  end loop;

  -- O Torneo de Nadal só vale do 26 ao 30 de decembro e bloquea os demais usos do campo.
  insert into facility_use_period (id, municipality_id, use_id, from_date, to_date)
  values ('019a0b6e-0000-7000-8000-000000000251', naron, '019a0b6e-0000-7000-8000-000000000233',
          make_date(y, 12, 26), make_date(y, 12, 30))
  on conflict do nothing;

  -- Excepcións: Noiteboa con horario curto e Fin de ano pechado (campo); limpeza (pavillón).
  insert into schedule_exception (id, municipality_id, facility_id, date, open, start_minute,
                                  end_minute, reason, created_at, updated_at, version)
  values ('019a0b6e-0000-7000-8000-000000000271', naron, '019a0b6e-0000-7000-8000-000000000201',
          make_date(y, 12, 24), true, 600, 840, 'Noiteboa', now(), now(), 0),
         ('019a0b6e-0000-7000-8000-000000000272', naron, '019a0b6e-0000-7000-8000-000000000201',
          make_date(y, 12, 31), false, null, null, 'Fin de ano', now(), now(), 0),
         ('019a0b6e-0000-7000-8000-000000000273', naron, '019a0b6e-0000-7000-8000-000000000204',
          make_date(y + 1, 1, 2), false, null, null, 'Limpeza a fondo do pavillón', now(), now(),
          0)
  on conflict do nothing;

  -- Un bloqueo: mantemento do céspede do campo, de 8:00 a 12:00 (hora de Galicia).
  insert into block (id, municipality_id, facility_id, date, start_minute, end_minute, starts_at,
                     ends_at, reason, created_by, created_at, updated_at, version)
  values ('019a0b6e-0000-7000-8000-000000000281', naron, '019a0b6e-0000-7000-8000-000000000201',
          block_day, 480, 720, (block_day + time '08:00') at time zone 'Europe/Madrid',
          (block_day + time '12:00') at time zone 'Europe/Madrid', 'Mantemento do céspede',
          'demo', now(), now(), 0)
  on conflict do nothing;
end
$$;
