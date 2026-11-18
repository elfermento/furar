-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: CC0-1.0
--
-- Día no que se aplica: ${demo_day}. Flyway volve aplicar o script cando cambia (o primeiro
-- arrinque de cada día; application-demo.yaml): as datas son relativas a now().
--
-- Incidencias de exemplo de Narón (perfil demo, ADR-0036): unhas poucas en cada estado, avisadas
-- pola veciñanza (sen conta real: persoas e correos inventados en example.org) e unha anotada polo
-- persoal. Sen fotos. Idempotente: ids fixos e «on conflict do nothing».

insert into incident
  (id, municipality_id, facility_id, booking_id, type, description, status, origin,
   reporter_subject, reporter_name, reporter_email, reporter_language, internal_note,
   resolution_message, resolved_at, created_at, updated_at, version)
values
  ('019a0b6e-0000-7000-8000-000000000901', '019a0b6e-0000-7000-8000-000000000001',
   '019a0b6e-0000-7000-8000-000000000203', null, 'BREAKDOWN',
   'A rede está rota polo medio e non se pode xogar.', 'NEW', 'RESIDENT',
   null, 'Veciña de exemplo', 'vecina.exemplo@example.org', 'gl', null, null, null,
   now() - interval '3 hours', now() - interval '3 hours', 0),
  ('019a0b6e-0000-7000-8000-000000000902', '019a0b6e-0000-7000-8000-000000000001',
   '019a0b6e-0000-7000-8000-000000000204', null, 'CLEANING',
   'As papeleiras da bancada están cheas.', 'NEW', 'RESIDENT',
   null, 'Veciño de exemplo', 'vecino.exemplo@example.org', 'es', null, null, null,
   now() - interval '1 day', now() - interval '1 day', 0),
  ('019a0b6e-0000-7000-8000-000000000903', '019a0b6e-0000-7000-8000-000000000001',
   '019a0b6e-0000-7000-8000-000000000201', null, 'SAFETY',
   'Unha porta do valado non pecha ben.', 'IN_PROGRESS', 'RESIDENT',
   null, 'Persoa de exemplo', 'persoa.exemplo@example.org', 'gl',
   'Avisado o servizo de mantemento.', null, null,
   now() - interval '2 days', now() - interval '1 day', 1),
  ('019a0b6e-0000-7000-8000-000000000904', '019a0b6e-0000-7000-8000-000000000001',
   '019a0b6e-0000-7000-8000-000000000202', null, 'OTHER',
   'O marcador electrónico non acende.', 'RESOLVED', 'STAFF',
   null, 'Recepción de exemplo', null, 'gl', 'Cambiado o fusible.', null,
   now() - interval '4 days', now() - interval '6 days', now() - interval '4 days', 2)
on conflict do nothing;
