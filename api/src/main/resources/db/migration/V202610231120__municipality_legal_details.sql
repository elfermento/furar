-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Datos legais de cada concello (ADR-0017 punto 8, AP4-01), todos opcionais: DPD propio (RGPD art.
-- 13.1.b; unha Deputación aloxa varios concellos) e contacto, procedemento de reclamación e
-- unidade responsable da declaración de accesibilidade (RD 1112/2018, arts. 13 e 16). Sen valor,
-- a web usa os FURAR_PRIVACY_* do despregue. O dominio valida o formato; a base, o tamaño.

alter table municipality
    add column ine_code                    text,
    add column dir3_code                   text,
    add column legal_entity_name           text,
    add column e_office_url                text,
    add column privacy_url                 text,
    add column dpo_contact                 text,
    add column accessibility_email         text,
    add column accessibility_complaint_url text,
    add column accessibility_unit          text,
    add constraint municipality_ine_code_ck check (ine_code ~ '^[0-9]{5}$'),
    add constraint municipality_dir3_code_ck check (dir3_code ~ '^[A-Z][A-Z0-9]{8}$'),
    add constraint municipality_legal_text_ck check (
        length(legal_entity_name) <= 200 and length(dpo_contact) <= 200
        and length(accessibility_email) <= 200 and length(accessibility_unit) <= 200),
    add constraint municipality_legal_url_ck check (
        e_office_url ~ '^https://' and length(e_office_url) <= 500
        and privacy_url ~ '^https://' and length(privacy_url) <= 500
        and accessibility_complaint_url ~ '^https://' and length(accessibility_complaint_url) <= 500);
