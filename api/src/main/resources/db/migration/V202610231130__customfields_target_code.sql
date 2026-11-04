-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- O destino dun campo personalizado é un código, non unha lista pechada cos módulos futuros
-- (ADR-0005 ARQ3-2, ARQ4-2). A base só comproba o formato; que estea rexistrado
-- (CustomFieldTargetProvider dun módulo) compróbao o caso de uso. Os valores gardados non cambian.

alter table custom_field_definition drop constraint custom_field_definition_target_ck;

alter table custom_field_definition
    add constraint custom_field_definition_target_ck check (target ~ '^[A-Z][A-Z0-9_]{1,39}$');
