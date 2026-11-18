-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: CC0-1.0
--
-- Datos de demostración (perfil demo): Narón e Ferrol, co tema neutro de Furar, e campos
-- personalizados de exemplo en Narón (táboas tipadas, V202610250900); e os oito concellos da
-- comarca de Verín, cos seus datos reais. Idempotente.

insert into municipality (id, slug, name, languages, default_language, theme, created_at,
                          updated_at, version)
values ('019a0b6e-0000-7000-8000-000000000001', 'naron', 'Narón (demo)', array['gl', 'es'], 'gl',
        '{"primary": "#0b4f8a", "onPrimary": "#ffffff", "accent": "#b3541e", "background": "#ffffff", "text": "#1a1a1a"}',
        '2026-10-22T08:00:00Z', '2026-10-22T08:00:00Z', 0),
       ('019a0b6e-0000-7000-8000-000000000002', 'ferrol', 'Ferrol (demo)', array['gl', 'es'], 'gl',
        '{"primary": "#0b4f8a", "onPrimary": "#ffffff", "accent": "#b3541e", "background": "#ffffff", "text": "#1a1a1a"}',
        '2026-10-22T08:00:00Z', '2026-10-22T08:00:00Z', 0)
on conflict (id) do nothing;

-- Os concellos da comarca de Verín, con datos públicos (consultados o 2026-10-30) salvo o contacto:
-- correos e teléfonos ficticios (@<concello>.example.org, 988 000 3xx), ningún real na demo. Sen
-- tema propio nin escudo: o tema neutro de Furar (Theme.DEFAULT). O que non consta queda baleiro.
insert into municipality (id, slug, name, languages, default_language, theme, created_at,
                          updated_at, version)
select v.id::uuid, v.slug, v.name, array['gl', 'es'], 'gl',
       '{"primary": "#0b4f8a", "onPrimary": "#ffffff", "accent": "#b3541e", "background": "#ffffff", "text": "#1a1a1a"}',
       '2026-10-30T08:00:00Z', '2026-10-30T08:00:00Z', 0
from (values ('019a0b6e-0000-7000-8000-000000000003', 'verin', 'Verín (demo)'),
             ('019a0b6e-0000-7000-8000-000000000004', 'vilardevos', 'Vilardevós (demo)'),
             ('019a0b6e-0000-7000-8000-000000000005', 'rios', 'Riós (demo)'),
             ('019a0b6e-0000-7000-8000-000000000006', 'castrelo-do-val', 'Castrelo do Val (demo)'),
             ('019a0b6e-0000-7000-8000-000000000007', 'cualedro', 'Cualedro (demo)'),
             ('019a0b6e-0000-7000-8000-000000000008', 'laza', 'Laza (demo)'),
             ('019a0b6e-0000-7000-8000-000000000009', 'monterrei', 'Monterrei (demo)'),
             ('019a0b6e-0000-7000-8000-00000000000a', 'oimbra', 'Oímbra (demo)'))
         as v(id, slug, name)
on conflict (id) do nothing;

-- Narón (demo): datos legais inventados (.example) para que a páxina «Privacidade» e a frase do
-- rexistro teñan responsable, DPD e canles (ADR-0017, revisión do 2026-11-03: xa non hai
-- FURAR_PRIVACY_*). Só se aínda non os ten.
update municipality
set legal_entity_name = 'Concello de Narón (demo)', address = 'Praza de Galicia, 1, 15570 Narón',
    general_email = 'concello@naron.example', dpo_email = 'dpd@naron.example'
where id = '019a0b6e-0000-7000-8000-000000000001' and legal_entity_name is null and dpo_email is null;

-- Narón e Ferrol (demo) cos cinco datos que usan os textos legais: sen pasos pendentes de «Para
-- abrir as reservas», a xestión entra onde estaba. Inventados; só os que faltan.
update municipality set nif = 'P1505400J'
where id = '019a0b6e-0000-7000-8000-000000000001' and nif is null;
update municipality
set legal_entity_name = 'Concello de Ferrol (demo)', nif = 'P1503700E',
    address = 'Praza de Armas, s/n, 15402 Ferrol', general_email = 'concello@ferrol.example',
    dpo_email = 'dpd@ferrol.example'
where id = '019a0b6e-0000-7000-8000-000000000002' and legal_entity_name is null and nif is null
  and dpo_email is null;

-- Datos legais e contacto (só os que constan; só se o concello aínda non os ten). Verín ten servizo
-- de deportes propio; nos demais, o contacto para reservar é o do concello.
update municipality m
set dir3_code = v.dir3, nif = v.nif, legal_entity_name = v.legal_name,
    e_office_url = v.e_office, address = v.address, general_email = v.general_email,
    dpo_email = v.dpo_email, sports_service_name = v.sports_name, sports_address = v.sports_address,
    sports_phone = v.phone, sports_email = v.sports_email
from (values
  ('019a0b6e-0000-7000-8000-000000000003'::uuid, 'L01320855', 'P3208600A',
   'Concello de Verín', 'https://sede.verin.example.org',
   'Praza do Concello, 1, 32600 Verín (Ourense)', 'notificacions@verin.example.org', 'secretario@verin.example.org',
   'Servizo Municipal de Deportes', 'Rúa Irmáns Moreno, 13 · 32600 Verín', '988 000 301',
   'deportes@verin.example.org'),
  ('019a0b6e-0000-7000-8000-000000000004'::uuid, null, null, 'Concello de Vilardevós',
   'https://sede.vilardevos.example.org', 'Paseo Manuel Núñez, 36, 32616 Vilardevós (Ourense)',
   'concello@vilardevos.example.org', null, 'Concello de Vilardevós',
   'Paseo Manuel Núñez, 36 · 32616 Vilardevós', '988 000 302', 'concello@vilardevos.example.org'),
  ('019a0b6e-0000-7000-8000-000000000005'::uuid, null, null, 'Concello de Riós',
   'https://sede.rios.example.org', 'Praza do Cruceiro, 139, 32610 Riós (Ourense)',
   'concello@rios.example.org', null, 'Concello de Riós', 'Praza do Cruceiro, 139 · 32610 Riós',
   '988 000 303', 'concello@rios.example.org'),
  ('019a0b6e-0000-7000-8000-000000000006'::uuid, null, null, 'Concello de Castrelo do Val',
   'https://sede.castrelo-do-val.example.org', null, 'administracion@castrelo-do-val.example.org', null,
   'Concello de Castrelo do Val', null, '988 000 304', 'administracion@castrelo-do-val.example.org'),
  ('019a0b6e-0000-7000-8000-000000000007'::uuid, null, null, 'Concello de Cualedro',
   'https://sede.cualedro.example.org', 'Avenida de Portugal, 8, Cualedro (Ourense)',
   'concello@cualedro.example.org', null, 'Concello de Cualedro', 'Avenida de Portugal, 8 · Cualedro',
   '988 000 305', 'concello@cualedro.example.org'),
  -- Laza: sen web nin correo publicados; o teléfono é o que se dá para reservar o pádel.
  ('019a0b6e-0000-7000-8000-000000000008'::uuid, null, null, 'Concello de Laza',
   'https://sede.laza.example.org', null, null, null, 'Concello de Laza', null, '988 000 306',
   null),
  ('019a0b6e-0000-7000-8000-000000000009'::uuid, null, null, 'Concello de Monterrei',
   'https://sede.monterrei.example.org', 'Rúa Fontiñas, 2, 32618 Albarellos, Monterrei (Ourense)',
   'concello@monterrei.example.org', null, 'Concello de Monterrei', 'Rúa Fontiñas, 2 · 32618 Albarellos',
   '988 000 307', 'concello@monterrei.example.org'),
  ('019a0b6e-0000-7000-8000-00000000000a'::uuid, null, null, 'Concello de Oímbra',
   'https://sede.oimbra.example.org', 'Avenida do Carregal, 3, 32613 Oímbra (Ourense)',
   'concello@oimbra.example.org', null, 'Concello de Oímbra', 'Avenida do Carregal, 3 · 32613 Oímbra',
   '988 000 308', 'concello@oimbra.example.org'))
  as v(id, dir3, nif, legal_name, e_office, address, general_email, dpo_email, sports_name,
       sports_address, phone, sports_email)
where m.id = v.id and m.legal_entity_name is null;

-- Contacto do servizo de deportes: sae na ficha e no pé dos correos. As notas de
-- pago e devolución quedan baleiras: valen as de Furar («No mostrador, ao chegar»…).
update municipality
set sports_service_name = 'Servizo Municipal de Deportes', sports_address = 'Rúa Buxo, 16 · 15572 Narón',
    sports_phone = '981 000 101', sports_email = 'deportes@naron.example'
where id = '019a0b6e-0000-7000-8000-000000000001' and sports_email is null;

update municipality
set sports_service_name = 'Servizo Municipal de Deportes', sports_address = 'Avenida de Esteiro, 10 · 15403 Ferrol',
    sports_phone = '981 000 201', sports_email = 'deportes@ferrol.example'
where id = '019a0b6e-0000-7000-8000-000000000002' and sports_email is null;

-- Narón penaliza as faltas (ADR-0032): 3 faltas en 30 días, 15 días sen reservar por internet.
-- Acesa desde 45 días antes de cargar a demo, para que as faltas de exemplo
-- (R__demo_naron_people.sql) conten. Só se está apagada; Ferrol e os demais quedan sen penalización.
update municipality
set no_show_penalty_since = date_trunc('day', now()) - interval '45 days',
    no_show_threshold = 3, no_show_window_days = 30, no_show_block_days = 15
where id = '019a0b6e-0000-7000-8000-000000000001' and no_show_penalty_since is null;

-- Datos abertos (ADR-0034): acesos nos concellos da demo, coma nos que xa existían ao chegar a
-- migración (V202611111200). Só se están apagados.
update municipality set open_data_since = now()
where id::text like '019a0b6e-0000-7000-8000-0000000000__' and open_data_since is null;

insert into custom_field (id, municipality_id, code, type, label, help, personal_data,
                          max_length, text_format, status, created_at, updated_at, version)
values ('019a0b6e-0000-7000-8000-000000000101', '019a0b6e-0000-7000-8000-000000000001', 'web',
        'TEXT', '{"gl": "Web do concello", "es": "Web del ayuntamiento"}',
        '{"gl": "Enderezo que empeza por https://", "es": "Dirección que empieza por https://"}',
        false, 200, 'URL', 'ACTIVE', '2026-10-22T08:00:00Z', '2026-10-22T08:00:00Z', 0),
       ('019a0b6e-0000-7000-8000-000000000102', '019a0b6e-0000-7000-8000-000000000001',
        'ridega_code', 'TEXT', '{"gl": "Código RIDEGA", "es": "Código RIDEGA"}', null, false, 20,
        null, 'ACTIVE', '2026-10-22T08:00:00Z', '2026-10-22T08:00:00Z', 0)
on conflict (id) do nothing;

insert into custom_field_usage (id, municipality_id, field_id, target, required, visibility,
                                personal_data)
values ('019a0b6e-0000-7000-8000-000000000111', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000101', 'MUNICIPALITY', false, 'PUBLIC', false),
       ('019a0b6e-0000-7000-8000-000000000112', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000102', 'MUNICIPALITY', false, 'STAFF', false)
on conflict do nothing;

insert into custom_field_value (id, municipality_id, field_id, field_type, target, entity_id,
                                text_value)
values ('019a0b6e-0000-7000-8000-000000000121', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000101', 'TEXT', 'MUNICIPALITY',
        '019a0b6e-0000-7000-8000-000000000001', 'https://naron.example.org'),
       ('019a0b6e-0000-7000-8000-000000000122', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000102', 'TEXT', 'MUNICIPALITY',
        '019a0b6e-0000-7000-8000-000000000001', '15-054')
on conflict do nothing;

-- Dous campos propios da reserva en Narón (revisión do 2026-11-04), para ver o formulario de
-- reservar e a ficha da reserva con campos: «Número de federado» (texto, dato persoal) e «Nivel»
-- (lista). Opcionais e só para quen reserva e a xestión (OWNER): reservar sen enchelos segue igual.
insert into custom_field (id, municipality_id, code, type, label, help, personal_data,
                          max_length, status, created_at, updated_at, version)
values ('019a0b6e-0000-7000-8000-000000000104', '019a0b6e-0000-7000-8000-000000000001',
        'federation_number', 'TEXT', '{"gl": "Número de federado", "es": "Número de federado"}',
        '{"gl": "Se tes licenza federativa", "es": "Si tienes licencia federativa"}', true, 30,
        'ACTIVE', '2026-11-04T08:00:00Z', '2026-11-04T08:00:00Z', 0),
       ('019a0b6e-0000-7000-8000-000000000105', '019a0b6e-0000-7000-8000-000000000001',
        'level', 'SELECT', '{"gl": "Nivel", "es": "Nivel"}', null, false, null,
        'ACTIVE', '2026-11-04T08:00:00Z', '2026-11-04T08:00:00Z', 0)
on conflict (id) do nothing;

insert into custom_field_option (id, municipality_id, field_id, field_type, code, label)
values ('019a0b6e-0000-7000-8000-000000000131', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000105', 'SELECT', 'beginner',
        '{"gl": "Iniciación", "es": "Iniciación"}'),
       ('019a0b6e-0000-7000-8000-000000000132', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000105', 'SELECT', 'intermediate',
        '{"gl": "Medio", "es": "Medio"}'),
       ('019a0b6e-0000-7000-8000-000000000133', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000105', 'SELECT', 'advanced',
        '{"gl": "Avanzado", "es": "Avanzado"}')
on conflict do nothing;

insert into custom_field_usage (id, municipality_id, field_id, target, required, visibility,
                                personal_data)
values ('019a0b6e-0000-7000-8000-000000000114', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000104', 'BOOKING', false, 'OWNER', true),
       ('019a0b6e-0000-7000-8000-000000000115', '019a0b6e-0000-7000-8000-000000000001',
        '019a0b6e-0000-7000-8000-000000000105', 'BOOKING', false, 'OWNER', false)
on conflict do nothing;
