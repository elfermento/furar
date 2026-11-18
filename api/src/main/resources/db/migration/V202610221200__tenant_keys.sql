-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Reforzos na base (ADR-0006, ADR-0011):
-- 1. Clave (municipality_id, id) en toda táboa do concello: as FK de negocio son compostas e a base
--    impide apuntar a un rexistro doutro concello (SchemaTenancyIT).
-- 2. Un campo marcado como dato persoal nunca é público (RGPD), tamén para quen escriba por SQL.

alter table custom_field_definition
    add constraint custom_field_definition_tenant_uk unique (municipality_id, id);

alter table custom_field_definition
    add constraint custom_field_definition_personal_data_ck
        check (not (personal_data and visibility = 'PUBLIC'));
