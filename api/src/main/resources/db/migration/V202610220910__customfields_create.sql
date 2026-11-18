-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Definicións de campos personalizados por concello (ADR-0011). Nunca se borran: arquívanse.

create table custom_field_definition (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    target          text not null,
    code            text not null,
    type            text not null,
    label           jsonb not null,
    help            jsonb,
    required        boolean not null,
    rules           jsonb not null default '{}'::jsonb,
    options         jsonb not null default '[]'::jsonb,
    position        int not null,
    group_code      text,
    group_label     jsonb,
    visibility      text not null,
    applies_when    jsonb not null default '{}'::jsonb,
    personal_data   boolean not null,
    status          text not null,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    version         bigint not null,
    constraint custom_field_definition_code_uk unique (municipality_id, target, code),
    constraint custom_field_definition_target_ck
        check (target in ('MUNICIPALITY', 'FACILITY', 'SPACE', 'BOOKING', 'RESIDENT_PROFILE', 'ORGANIZATION')),
    constraint custom_field_definition_code_ck check (code ~ '^[a-z][a-z0-9_]{1,39}$'),
    constraint custom_field_definition_type_ck
        check (type in ('TEXT', 'LONG_TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT', 'MULTI_SELECT')),
    constraint custom_field_definition_visibility_ck check (visibility in ('PUBLIC', 'OWNER', 'STAFF')),
    constraint custom_field_definition_status_ck check (status in ('ACTIVE', 'ARCHIVED')),
    constraint custom_field_definition_label_ck check (label ? 'gl' and label ? 'es'),
    constraint custom_field_definition_options_ck check (jsonb_typeof(options) = 'array'),
    constraint custom_field_definition_position_ck check (position between 0 and 10000)
);

create index custom_field_definition_target_idx
    on custom_field_definition (municipality_id, target, position);
