-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Páxinas legais do concello (ADR-0017 punto 8, revisión do 2026-10-29): Privacidade,
-- Accesibilidade e Aviso legal, cada unha con tres modos. FURAR = a páxina que xera Furar cos datos
-- do concello; TEXT = un texto propio en gl e es (HTML xa saneado no servidor cunha lista branca);
-- URL = a páxina do concello, á que levan as ligazóns de Furar. O modo, o enderezo e o texto
-- gárdanse por separado: cambiar de modo non borra nada.
--
-- Datos novos do Aviso legal (LSSI art. 10): NIF, domicilio e correo xeral. O DPD do concello pasa
-- de texto libre (dpo_contact) a correo e teléfono: o correo e o teléfono que haxa no texto pasan
-- aos campos novos e o texto deixa de existir.
--
-- Columnas da táboa dos concellos, non unha táboa aparte: son un dato por concello e páxina, e a
-- táboa dos concellos non leva RLS (TenancyAllowlist). O dominio valida o formato; a base, o resto.

alter table municipality
    add column nif                text,
    add column address            text,
    add column general_email      text,
    add column dpo_email          text,
    add column dpo_phone          text,
    add column privacy_mode       text not null default 'FURAR',
    add column accessibility_mode text not null default 'FURAR',
    add column legal_notice_mode  text not null default 'FURAR',
    add column accessibility_url  text,
    add column legal_notice_url   text,
    add column privacy_text       jsonb,
    add column accessibility_text jsonb,
    add column legal_notice_text  jsonb;

update municipality
   set dpo_email = substring(dpo_contact from '[^@[:space:],;:<>()]+@[^@[:space:],;:<>()]+\.[^@[:space:],;:<>()]+'),
       dpo_phone = substring(dpo_contact from '\+?[0-9][0-9 ]{7,13}[0-9]')
 where dpo_contact is not null;

-- A restrición de tamaños levaba dpo_contact: vólvese crear cos campos de agora.
alter table municipality
    drop constraint municipality_legal_text_ck,
    drop column dpo_contact,
    add constraint municipality_legal_text_ck check (
        length(legal_entity_name) <= 200 and length(accessibility_email) <= 200
        and length(accessibility_unit) <= 200 and length(address) <= 200
        and length(general_email) <= 200 and length(dpo_email) <= 200),
    add constraint municipality_legal_nif_ck check (nif ~ '^[A-Z][0-9]{7}[A-Z0-9]$'),
    add constraint municipality_legal_email_ck check (
        general_email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' and dpo_email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    add constraint municipality_legal_phone_ck check (dpo_phone ~ '^\+?[0-9 ]{9,15}$'),
    add constraint municipality_legal_mode_ck check (
        privacy_mode in ('FURAR', 'TEXT', 'URL')
        and accessibility_mode in ('FURAR', 'TEXT', 'URL')
        and legal_notice_mode in ('FURAR', 'TEXT', 'URL')),
    add constraint municipality_legal_page_url_ck check (
        accessibility_url ~ '^https://' and length(accessibility_url) <= 500
        and legal_notice_url ~ '^https://' and length(legal_notice_url) <= 500),
    -- Páxina propia sen enderezo non leva a ningures.
    add constraint municipality_legal_url_mode_ck check (
        (privacy_mode <> 'URL' or privacy_url is not null)
        and (accessibility_mode <> 'URL' or accessibility_url is not null)
        and (legal_notice_mode <> 'URL' or legal_notice_url is not null)),
    add constraint municipality_legal_page_text_ck check (
        (privacy_text is null or (jsonb_typeof(privacy_text) = 'object'
            and coalesce(length(privacy_text ->> 'gl'), 0) <= 40000
            and coalesce(length(privacy_text ->> 'es'), 0) <= 40000))
        and (accessibility_text is null or (jsonb_typeof(accessibility_text) = 'object'
            and coalesce(length(accessibility_text ->> 'gl'), 0) <= 40000
            and coalesce(length(accessibility_text ->> 'es'), 0) <= 40000))
        and (legal_notice_text is null or (jsonb_typeof(legal_notice_text) = 'object'
            and coalesce(length(legal_notice_text ->> 'gl'), 0) <= 40000
            and coalesce(length(legal_notice_text ->> 'es'), 0) <= 40000)));
