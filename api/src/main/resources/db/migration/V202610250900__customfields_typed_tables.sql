-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Campos personalizados en táboas tipadas (especificación, bloque 1b; plan do dominio 1, D1-10a):
-- a definición deixa de pertencer a un só destino e reutilízase en varios (custom_field_usage, co
-- seu obrigatorio e visibilidade en cada sitio); as opcións e os valores pasan de jsonb a filas, e
-- a base garante o tipo de cada valor. Fóra a orde manual, os grupos e applies_when (P28).
--
-- Copia as definicións de custom_field_definition e os valores de municipality.custom_fields,
-- compara os recontos nun bloque DO antes de borrar o vello e, se non casan, a migración falla
-- enteira (Flyway corre nunha transacción): nunca se perde un valor en silencio.

-- 1. Definicións -------------------------------------------------------------------------------

create table custom_field (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    code            text not null,
    type            text not null,
    label           jsonb not null,
    help            jsonb,
    personal_data   boolean not null,
    min_length      int,
    max_length      int,
    min_number      numeric,
    max_number      numeric,
    decimals        int,
    min_date        date,
    max_date        date,
    text_format     text,
    min_choices     int,
    max_choices     int,
    status          text not null,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    version         bigint not null,
    constraint custom_field_tenant_uk unique (municipality_id, id),
    constraint custom_field_code_uk unique (municipality_id, code),
    -- Destinos das FK que copian o tipo e a marca de dato persoal (valores e usos).
    constraint custom_field_type_uk unique (municipality_id, id, type),
    constraint custom_field_personal_data_uk unique (municipality_id, id, personal_data),
    constraint custom_field_code_ck check (code ~ '^[a-z][a-z0-9_]{1,39}$'),
    constraint custom_field_type_ck
        check (type in ('TEXT', 'LONG_TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT', 'MULTI_SELECT')),
    constraint custom_field_status_ck check (status in ('ACTIVE', 'ARCHIVED')),
    constraint custom_field_label_ck check (label ? 'gl' and label ? 'es'),
    constraint custom_field_help_ck check (help is null or (help ? 'gl' and help ? 'es')),
    -- Cada regra só no seu tipo.
    constraint custom_field_length_ck
        check ((min_length is null and max_length is null) or type in ('TEXT', 'LONG_TEXT')),
    constraint custom_field_number_ck
        check ((min_number is null and max_number is null and decimals is null) or type = 'NUMBER'),
    constraint custom_field_date_ck check ((min_date is null and max_date is null) or type = 'DATE'),
    constraint custom_field_format_ck
        check (text_format is null or (type = 'TEXT' and text_format in ('EMAIL', 'PHONE', 'URL'))),
    constraint custom_field_choices_ck
        check ((min_choices is null and max_choices is null) or type = 'MULTI_SELECT'),
    constraint custom_field_length_range_ck
        check ((min_length is null or min_length >= 0) and (max_length is null or max_length between 1 and 5000)
               and (min_length is null or max_length is null or min_length <= max_length)),
    constraint custom_field_number_range_ck
        check ((decimals is null or decimals between 0 and 6)
               and (min_number is null or max_number is null or min_number <= max_number)),
    constraint custom_field_date_range_ck
        check (min_date is null or max_date is null or min_date <= max_date),
    constraint custom_field_choices_range_ck
        check ((min_choices is null or min_choices >= 0) and (max_choices is null or max_choices >= 1)
               and (min_choices is null or max_choices is null or min_choices <= max_choices))
);

create table custom_field_option (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    field_id        uuid not null,
    field_type      text not null,
    code            text not null,
    label           jsonb not null,
    archived        boolean not null default false,
    constraint custom_field_option_tenant_uk unique (municipality_id, id),
    constraint custom_field_option_code_uk unique (municipality_id, field_id, code),
    constraint custom_field_option_field_uk unique (municipality_id, field_id, id),
    -- Só os campos de selección teñen opcións.
    constraint custom_field_option_field_fk foreign key (municipality_id, field_id, field_type)
        references custom_field (municipality_id, id, type),
    constraint custom_field_option_type_ck check (field_type in ('SELECT', 'MULTI_SELECT')),
    constraint custom_field_option_code_ck check (code ~ '^[a-z0-9][a-z0-9_]{0,39}$'),
    constraint custom_field_option_label_ck check (label ? 'gl' and label ? 'es')
);

create table custom_field_usage (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    field_id        uuid not null,
    target          text not null,
    required        boolean not null,
    visibility      text not null,
    -- Copia da marca do campo, que segue os seus cambios: a base impide un dato persoal público.
    personal_data   boolean not null,
    constraint custom_field_usage_tenant_uk unique (municipality_id, id),
    constraint custom_field_usage_target_uk unique (municipality_id, field_id, target),
    constraint custom_field_usage_field_fk foreign key (municipality_id, field_id, personal_data)
        references custom_field (municipality_id, id, personal_data) on update cascade,
    constraint custom_field_usage_target_ck check (target ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    constraint custom_field_usage_visibility_ck check (visibility in ('PUBLIC', 'OWNER', 'STAFF')),
    constraint custom_field_usage_personal_data_ck check (not personal_data or visibility <> 'PUBLIC')
);

create index custom_field_usage_target_idx on custom_field_usage (municipality_id, target);

-- 2. Valores -----------------------------------------------------------------------------------

-- entity_id é polimórfico e sen FK (ADR-0011): RLS, comprobación no almacén e borrado vía o
-- módulo dono. A columna do tipo é a única con valor; MULTI_SELECT vai en custom_field_value_option.
create table custom_field_value (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    field_id        uuid not null,
    field_type      text not null,
    target          text not null,
    entity_id       uuid not null,
    text_value      text,
    number_value    numeric,
    date_value      date,
    bool_value      boolean,
    option_id       uuid,
    constraint custom_field_value_tenant_uk unique (municipality_id, id),
    constraint custom_field_value_entity_uk unique (municipality_id, target, entity_id, field_id),
    constraint custom_field_value_field_uk unique (municipality_id, id, field_id),
    constraint custom_field_value_type_fk foreign key (municipality_id, field_id, field_type)
        references custom_field (municipality_id, id, type),
    constraint custom_field_value_usage_fk foreign key (municipality_id, field_id, target)
        references custom_field_usage (municipality_id, field_id, target),
    constraint custom_field_value_option_fk foreign key (municipality_id, field_id, option_id)
        references custom_field_option (municipality_id, field_id, id),
    constraint custom_field_value_column_ck check (
        (text_value is not null) = (field_type in ('TEXT', 'LONG_TEXT'))
        and (number_value is not null) = (field_type = 'NUMBER')
        and (date_value is not null) = (field_type = 'DATE')
        and (bool_value is not null) = (field_type = 'BOOLEAN')
        and (option_id is not null) = (field_type = 'SELECT'))
);

create table custom_field_value_option (
    municipality_id uuid not null references municipality (id),
    value_id        uuid not null,
    field_id        uuid not null,
    option_id       uuid not null,
    primary key (value_id, option_id),
    constraint custom_field_value_option_value_fk foreign key (municipality_id, value_id, field_id)
        references custom_field_value (municipality_id, id, field_id) on delete cascade,
    constraint custom_field_value_option_option_fk foreign key (municipality_id, field_id, option_id)
        references custom_field_option (municipality_id, field_id, id)
);

create index custom_field_value_option_tenant_idx on custom_field_value_option (municipality_id, value_id);

-- 3. Row Level Security, como V202610231100 ------------------------------------------------------

alter table custom_field enable row level security;
create policy custom_field_tenant on custom_field
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

alter table custom_field_option enable row level security;
create policy custom_field_option_tenant on custom_field_option
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

alter table custom_field_usage enable row level security;
create policy custom_field_usage_tenant on custom_field_usage
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

alter table custom_field_value enable row level security;
create policy custom_field_value_tenant on custom_field_value
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

alter table custom_field_value_option enable row level security;
create policy custom_field_value_option_tenant on custom_field_value_option
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

-- 4. Copia do modelo anterior ------------------------------------------------------------------

do $$
begin
    if exists (select 1 from custom_field_definition
               group by municipality_id, code having count(*) > 1) then
        raise exception 'custom_field_definition: o mesmo código en dous destinos dun concello; '
            'renomea un antes de migrar';
    end if;
end
$$;

insert into custom_field (id, municipality_id, code, type, label, help, personal_data,
                          min_length, max_length, min_number, max_number, decimals, min_date,
                          max_date, text_format, min_choices, max_choices, status, created_at,
                          updated_at, version)
select id, municipality_id, code, type, label, help, personal_data,
       (rules ->> 'minLength')::int, (rules ->> 'maxLength')::int,
       (rules ->> 'min')::numeric, (rules ->> 'max')::numeric, (rules ->> 'decimals')::int,
       (rules ->> 'minDate')::date, (rules ->> 'maxDate')::date, rules ->> 'format',
       (rules ->> 'minItems')::int, (rules ->> 'maxItems')::int,
       status, created_at, updated_at, version
from custom_field_definition;

insert into custom_field_option (id, municipality_id, field_id, field_type, code, label, archived)
select uuidv7(), d.municipality_id, d.id, d.type, o ->> 'code', o -> 'label',
       coalesce((o ->> 'archived')::boolean, false)
from custom_field_definition d
cross join lateral jsonb_array_elements(d.options) as o;

insert into custom_field_usage (id, municipality_id, field_id, target, required, visibility,
                                personal_data)
select uuidv7(), municipality_id, id, target, required, visibility, personal_data
from custom_field_definition;

-- Valores do concello (o único destino con valores ata agora). Os nulos JSON son «sen valor».
create temporary table custom_field_migrated_value on commit drop as
select uuidv7() as id, m.id as municipality_id, f.id as field_id, f.type as field_type,
       m.id as entity_id, v.value as raw
from municipality m
cross join lateral jsonb_each(m.custom_fields) as v
join custom_field f on f.municipality_id = m.id and f.code = v.key
join custom_field_usage u on u.field_id = f.id and u.target = 'MUNICIPALITY'
where jsonb_typeof(v.value) <> 'null';

insert into custom_field_value (id, municipality_id, field_id, field_type, target, entity_id,
                                text_value, number_value, date_value, bool_value, option_id)
select mv.id, mv.municipality_id, mv.field_id, mv.field_type, 'MUNICIPALITY', mv.entity_id,
       case when mv.field_type in ('TEXT', 'LONG_TEXT') then mv.raw #>> '{}' end,
       case when mv.field_type = 'NUMBER' then (mv.raw #>> '{}')::numeric end,
       case when mv.field_type = 'DATE' then (mv.raw #>> '{}')::date end,
       case when mv.field_type = 'BOOLEAN' then (mv.raw #>> '{}')::boolean end,
       case when mv.field_type = 'SELECT' then
           (select o.id from custom_field_option o
            where o.field_id = mv.field_id and o.code = mv.raw #>> '{}') end
from custom_field_migrated_value mv;

insert into custom_field_value_option (municipality_id, value_id, field_id, option_id)
select mv.municipality_id, mv.id, mv.field_id, o.id
from custom_field_migrated_value mv
cross join lateral jsonb_array_elements_text(mv.raw) as chosen (option_code)
join custom_field_option o on o.field_id = mv.field_id and o.code = chosen.option_code
where mv.field_type = 'MULTI_SELECT';

do $$
declare
    expected_fields   bigint;
    expected_options  bigint;
    expected_values   bigint;
    expected_choices  bigint;
    actual_fields     bigint;
    actual_options    bigint;
    actual_usages     bigint;
    actual_values     bigint;
    actual_choices    bigint;
begin
    select count(*), coalesce(sum(jsonb_array_length(options)), 0)
    into expected_fields, expected_options from custom_field_definition;
    select count(*) into expected_values
    from municipality m cross join lateral jsonb_each(m.custom_fields) as v
    where jsonb_typeof(v.value) <> 'null';
    select coalesce(sum(jsonb_array_length(v.value)), 0) into expected_choices
    from municipality m cross join lateral jsonb_each(m.custom_fields) as v
    where jsonb_typeof(v.value) = 'array';

    select count(*) into actual_fields from custom_field;
    select count(*) into actual_options from custom_field_option;
    select count(*) into actual_usages from custom_field_usage;
    select count(*) into actual_values from custom_field_value;
    select count(*) into actual_choices from custom_field_value_option;

    if actual_fields <> expected_fields or actual_usages <> expected_fields
        or actual_options <> expected_options or actual_values <> expected_values
        or actual_choices <> expected_choices then
        raise exception 'customfields: recontos distintos (campos %/%, usos %/%, opcións %/%, '
            'valores %/%, opcións marcadas %/%)',
            actual_fields, expected_fields, actual_usages, expected_fields, actual_options,
            expected_options, actual_values, expected_values, actual_choices, expected_choices;
    end if;
end
$$;

-- 5. Fóra o modelo anterior --------------------------------------------------------------------

drop index municipality_custom_fields_gin;
alter table municipality drop column custom_fields;
drop table custom_field_definition;
