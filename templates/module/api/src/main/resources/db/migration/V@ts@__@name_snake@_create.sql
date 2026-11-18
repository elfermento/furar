-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- @Name@: táboa do concello (ADR-0006: municipality_id con FK e índice, unique (municipality_id, id);
-- SchemaTenancyIT). Engade aquí as columnas núcleo tipadas e as restricións de negocio (check,
-- unique, exclude).
-- Referencias a outra táboa do concello: SEMPRE FK composta, para que a base impida apuntar a un
-- rexistro doutro concello (SchemaTenancyIT falla se non):
--   space_id uuid not null,
--   constraint @name_snake@_space_fk foreign key (municipality_id, space_id)
--       references space (municipality_id, id),
-- Row Level Security (ADR-0006): activa e coa política única na mesma migración. SchemaTenancyIT
-- falla se falta ou se outra política permisiva non filtra por concello.

create table @name_snake@ (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    name            text not null,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    version         bigint not null,
    constraint @name_snake@_tenant_uk unique (municipality_id, id),
    constraint @name_snake@_name_ck check (length(name) between 1 and 100)
);

create index @name_snake@_municipality_idx on @name_snake@ (municipality_id, created_at);
-- Campos personalizados (ADR-0011): sen columna aquí; os valores van nas táboas tipadas de -- @cf
-- customfields (custom_field_value), que garda o adaptador de persistencia. -- @cf

alter table @name_snake@ enable row level security;

create policy @name_snake@_tenant on @name_snake@
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
