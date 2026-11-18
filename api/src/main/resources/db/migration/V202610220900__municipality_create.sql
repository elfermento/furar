-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Concellos (ADR-0006, ADR-0010). A infraestrutura da plataforma (extensións, rexistro de eventos
-- de Spring Modulith) vive en V202610220850__platform_base.sql.

create table municipality (
    id               uuid primary key,
    slug             text not null,
    name             text not null,
    languages        text[] not null,
    default_language text not null,
    theme            jsonb not null,
    custom_fields    jsonb not null default '{}'::jsonb,
    created_at       timestamptz not null,
    updated_at       timestamptz not null,
    version          bigint not null,
    constraint municipality_slug_uk unique (slug),
    constraint municipality_slug_format_ck
        check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' and length(slug) between 2 and 40),
    constraint municipality_name_ck check (length(btrim(name)) between 1 and 100),
    constraint municipality_languages_ck
        check (cardinality(languages) >= 1 and languages <@ array['gl', 'es']::text[]),
    constraint municipality_default_language_ck check (default_language = any (languages)),
    constraint municipality_custom_fields_ck check (jsonb_typeof(custom_fields) = 'object')
);

create index municipality_custom_fields_gin on municipality using gin (custom_fields jsonb_path_ops);
