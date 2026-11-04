-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: CC0-1.0
--
-- Datos de demostración (perfil demo): Narón e Ferrol, cun tema propio cada un, e campos
-- personalizados de exemplo en Narón (táboas tipadas, V202610250900); e os oito concellos da
-- comarca de Verín, cos seus datos reais. Idempotente.

insert into municipality (id, slug, name, languages, default_language, theme, created_at,
                          updated_at, version)
values ('019a0b6e-0000-7000-8000-000000000001', 'naron', 'Narón', array['gl', 'es'], 'gl',
        '{"primary": "#0b4f8a", "onPrimary": "#ffffff", "accent": "#b3541e", "background": "#ffffff", "text": "#1a1a1a"}',
        '2026-10-22T08:00:00Z', '2026-10-22T08:00:00Z', 0),
       ('019a0b6e-0000-7000-8000-000000000002', 'ferrol', 'Ferrol', array['gl', 'es'], 'gl',
        '{"primary": "#7a1f3d", "onPrimary": "#ffffff", "accent": "#1f6b4a", "background": "#fdfcf9", "text": "#202020"}',
        '2026-10-22T08:00:00Z', '2026-10-22T08:00:00Z', 0)
on conflict (id) do nothing;

-- Os concellos da comarca de Verín, con datos reais (fontes públicas consultadas o 2026-10-30). Sen
-- tema propio nin escudo: o tema neutro de Furar (Theme.DEFAULT). O que non consta queda baleiro.
insert into municipality (id, slug, name, languages, default_language, theme, created_at,
                          updated_at, version)
select v.id::uuid, v.slug, v.name, array['gl', 'es'], 'gl',
       '{"primary": "#0b4f8a", "onPrimary": "#ffffff", "accent": "#b3541e", "background": "#ffffff", "text": "#1a1a1a"}',
       '2026-10-30T08:00:00Z', '2026-10-30T08:00:00Z', 0
from (values ('019a0b6e-0000-7000-8000-000000000003', 'verin', 'Verín'),
             ('019a0b6e-0000-7000-8000-000000000004', 'vilardevos', 'Vilardevós'),
             ('019a0b6e-0000-7000-8000-000000000005', 'rios', 'Riós'),
             ('019a0b6e-0000-7000-8000-000000000006', 'castrelo-do-val', 'Castrelo do Val'),
             ('019a0b6e-0000-7000-8000-000000000007', 'cualedro', 'Cualedro'),
             ('019a0b6e-0000-7000-8000-000000000008', 'laza', 'Laza'),
             ('019a0b6e-0000-7000-8000-000000000009', 'monterrei', 'Monterrei'),
             ('019a0b6e-0000-7000-8000-00000000000a', 'oimbra', 'Oímbra'))
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
-- abrir as reservas», a xestión entra onde estaba (maqueta 56). Inventados; só os que faltan.
update municipality set nif = 'P1505400J'
where id = '019a0b6e-0000-7000-8000-000000000001' and nif is null;
update municipality
set legal_entity_name = 'Concello de Ferrol (demo)', nif = 'P1503700J',
    address = 'Praza de Armas, s/n, 15402 Ferrol', general_email = 'concello@ferrol.example',
    dpo_email = 'dpd@ferrol.example'
where id = '019a0b6e-0000-7000-8000-000000000002' and legal_entity_name is null and nif is null
  and dpo_email is null;

-- Datos legais e contacto (só os que constan; só se o concello aínda non os ten). Verín ten servizo
-- de deportes propio; nos demais, o contacto para reservar é o do concello.
update municipality m
set ine_code = v.ine, dir3_code = v.dir3, nif = v.nif, legal_entity_name = v.legal_name,
    e_office_url = v.e_office, address = v.address, general_email = v.general_email,
    dpo_email = v.dpo_email, sports_service_name = v.sports_name, sports_address = v.sports_address,
    sports_phone = v.phone, sports_email = v.sports_email
from (values
  ('019a0b6e-0000-7000-8000-000000000003'::uuid, '32085', 'L01320855', 'P3208600A',
   'Concello de Verín', 'https://verin.sedelectronica.gal',
   'Praza do Concello, 1, 32600 Verín (Ourense)', 'notificacions@verin.gal', 'secretario@verin.gal',
   'Servizo Municipal de Deportes', 'Rúa Irmáns Moreno, 13 · 32600 Verín', '988 41 00 00',
   'deportes@verin.gal'),
  ('019a0b6e-0000-7000-8000-000000000004'::uuid, '32091', null, null, 'Concello de Vilardevós',
   'https://vilardevos.sedelectronica.gal', 'Paseo Manuel Núñez, 36, 32616 Vilardevós (Ourense)',
   'concello@vilardevos.gal', null, 'Concello de Vilardevós',
   'Paseo Manuel Núñez, 36 · 32616 Vilardevós', '988 417 004', 'concello@vilardevos.gal'),
  ('019a0b6e-0000-7000-8000-000000000005'::uuid, '32071', null, null, 'Concello de Riós',
   'https://concelloderios.sedelectronica.gal', 'Praza do Cruceiro, 139, 32610 Riós (Ourense)',
   'concello@concelloderios.gal', null, 'Concello de Riós', 'Praza do Cruceiro, 139 · 32610 Riós',
   '988 425 032', 'concello@concelloderios.gal'),
  ('019a0b6e-0000-7000-8000-000000000006'::uuid, '32021', null, null, 'Concello de Castrelo do Val',
   'https://castrelodoval.sedelectronica.gal', null, 'administracion@castrelodoval.gal', null,
   'Concello de Castrelo do Val', null, '988 419 002', 'administracion@castrelodoval.gal'),
  ('019a0b6e-0000-7000-8000-000000000007'::uuid, '32028', null, null, 'Concello de Cualedro',
   'https://cualedro.sedelectronica.es', 'Avenida de Portugal, 8, Cualedro (Ourense)',
   'concello@cualedro.gal', null, 'Concello de Cualedro', 'Avenida de Portugal, 8 · Cualedro',
   '988 424 004', 'concello@cualedro.gal'),
  -- Laza: sen web nin correo publicados; o teléfono é o que se dá para reservar o pádel.
  ('019a0b6e-0000-7000-8000-000000000008'::uuid, '32039', null, null, 'Concello de Laza',
   'https://laza.sedelectronica.gal', null, null, null, 'Concello de Laza', null, '988 422 112',
   null),
  ('019a0b6e-0000-7000-8000-000000000009'::uuid, '32050', null, null, 'Concello de Monterrei',
   'https://monterrei.sedelectronica.es', 'Rúa Fontiñas, 2, 32618 Albarellos, Monterrei (Ourense)',
   'concello@monterrei.gal', null, 'Concello de Monterrei', 'Rúa Fontiñas, 2 · 32618 Albarellos',
   '988 418 002', 'concello@monterrei.gal'),
  ('019a0b6e-0000-7000-8000-00000000000a'::uuid, '32053', null, null, 'Concello de Oímbra',
   'https://oimbra.sedelectronica.gal', 'Avenida do Carregal, 3, 32613 Oímbra (Ourense)',
   'concello@oimbra.gal', null, 'Concello de Oímbra', 'Avenida do Carregal, 3 · 32613 Oímbra',
   '988 426 160', 'concello@oimbra.gal'))
  as v(id, ine, dir3, nif, legal_name, e_office, address, general_email, dpo_email, sports_name,
       sports_address, phone, sports_email)
where m.id = v.id and m.ine_code is null;

-- Contacto do servizo de deportes (dominio 2, D2-02): sae na ficha e no pé dos correos. As notas de
-- pago e devolución quedan baleiras: valen as de Furar («No mostrador, ao chegar»…).
update municipality
set sports_service_name = 'Servizo Municipal de Deportes', sports_address = 'Rúa Buxo, 16 · 15572 Narón',
    sports_phone = '981 38 21 00', sports_email = 'deportes@naron.example'
where id = '019a0b6e-0000-7000-8000-000000000001' and sports_email is null;

update municipality
set sports_service_name = 'Servizo Municipal de Deportes', sports_address = 'Avenida de Esteiro, 10 · 15403 Ferrol',
    sports_phone = '981 94 41 00', sports_email = 'deportes@ferrol.example'
where id = '019a0b6e-0000-7000-8000-000000000002' and sports_email is null;

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
        '019a0b6e-0000-7000-8000-000000000001', 'https://naron.gal'),
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
