-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Campos personalizados (ADR-0011, revisión do 2026-11-04):
--   1. Un campo, un sitio. Un campo que se usaba en varios destinos pártese: o destino con máis
--      valores (e, se empatan, o primeiro por orde alfabética) queda co campo e o seu código; cada
--      un dos outros pasa a un campo novo, copia do orixinal (textos, regras, opcións, estado), co
--      código «<código>_<destino>», e os seus valores móvense a el sen perda. Despois, a base
--      garante un uso por campo.
--   2. Arquivado pasa a desactivado: o estado ARCHIVED a INACTIVE e a marca das opcións, inactive.
--   3. A reserva non ten campos públicos.
-- Recontos nun bloque DO: se non casan, a migración enteira falla.

-- 2. Desactivado no canto de arquivado.
alter table custom_field drop constraint custom_field_status_ck;
update custom_field set status = 'INACTIVE' where status = 'ARCHIVED';
alter table custom_field
    add constraint custom_field_status_ck check (status in ('ACTIVE', 'INACTIVE'));
alter table custom_field_option rename column archived to inactive;

-- 1. Os usos que pasan a un campo novo: todos os dun campo con varios, menos o que queda.
create temporary table custom_field_split on commit drop as
with ranked as (
    select u.id as usage_id, u.municipality_id, u.field_id, u.target, u.required, u.visibility,
           u.personal_data,
           row_number() over (
               partition by u.municipality_id, u.field_id
               order by (select count(*) from custom_field_value v
                         where v.municipality_id = u.municipality_id
                           and v.field_id = u.field_id and v.target = u.target) desc,
                        u.target) as position
    from custom_field_usage u
)
select r.*, uuidv7() as new_field_id,
       left(f.code, 39 - length(r.target)) || '_' || lower(r.target) as new_code
from ranked r
join custom_field f on f.municipality_id = r.municipality_id and f.id = r.field_id
where r.position > 1;

do $$
begin
    if exists (select 1 from custom_field_split s
               join custom_field f on f.municipality_id = s.municipality_id and f.code = s.new_code)
       or exists (select 1 from custom_field_split
                  group by municipality_id, new_code having count(*) > 1)
       or exists (select 1 from custom_field_split where new_code !~ '^[a-z][a-z0-9_]{1,39}$') then
        raise exception 'custom_field: o código dun campo partido xa existe ou non é válido; '
            'renomea o campo antes de migrar';
    end if;
end
$$;

create temporary table custom_field_split_counts on commit drop as
select (select count(*) from custom_field) as fields,
       (select count(*) from custom_field_usage) as usages,
       (select count(*) from custom_field_value) as field_values,
       (select count(*) from custom_field_value_option) as choices,
       (select count(*) from custom_field_split) as split,
       (select count(*) from custom_field_option o
        join custom_field_split s on s.municipality_id = o.municipality_id
                                 and s.field_id = o.field_id) as copied_options;

insert into custom_field (id, municipality_id, code, type, label, help, personal_data,
                          min_length, max_length, min_number, max_number, decimals, min_date,
                          max_date, text_format, min_choices, max_choices, status, created_at,
                          updated_at, version)
select s.new_field_id, f.municipality_id, s.new_code, f.type, f.label, f.help, f.personal_data,
       f.min_length, f.max_length, f.min_number, f.max_number, f.decimals, f.min_date,
       f.max_date, f.text_format, f.min_choices, f.max_choices, f.status, f.created_at, now(), 0
from custom_field_split s
join custom_field f on f.municipality_id = s.municipality_id and f.id = s.field_id;

create temporary table custom_field_split_option on commit drop as
select s.new_field_id, o.id as old_option_id, uuidv7() as new_option_id, o.municipality_id,
       o.field_type, o.code, o.label, o.inactive
from custom_field_split s
join custom_field_option o on o.municipality_id = s.municipality_id and o.field_id = s.field_id;

insert into custom_field_option (id, municipality_id, field_id, field_type, code, label, inactive)
select new_option_id, municipality_id, new_field_id, field_type, code, label, inactive
from custom_field_split_option;

insert into custom_field_usage (id, municipality_id, field_id, target, required, visibility,
                                personal_data)
select uuidv7(), municipality_id, new_field_id, target, required, visibility, personal_data
from custom_field_split;

create temporary table custom_field_split_value on commit drop as
select v.id as old_value_id, uuidv7() as new_value_id, s.new_field_id, v.municipality_id,
       v.field_type, v.target, v.entity_id, v.text_value, v.number_value, v.date_value,
       v.bool_value, so.new_option_id
from custom_field_split s
join custom_field_value v on v.municipality_id = s.municipality_id and v.field_id = s.field_id
                         and v.target = s.target
left join custom_field_split_option so on so.new_field_id = s.new_field_id
                                       and so.old_option_id = v.option_id;

insert into custom_field_value (id, municipality_id, field_id, field_type, target, entity_id,
                                text_value, number_value, date_value, bool_value, option_id)
select new_value_id, municipality_id, new_field_id, field_type, target, entity_id, text_value,
       number_value, date_value, bool_value, new_option_id
from custom_field_split_value;

insert into custom_field_value_option (municipality_id, value_id, field_id, option_id)
select sv.municipality_id, sv.new_value_id, sv.new_field_id, so.new_option_id
from custom_field_split_value sv
join custom_field_value_option c on c.municipality_id = sv.municipality_id
                                and c.value_id = sv.old_value_id
join custom_field_split_option so on so.new_field_id = sv.new_field_id
                                  and so.old_option_id = c.option_id;

-- Os valores vellos (e, en cascada, as súas opcións marcadas) e os usos xa movidos.
delete from custom_field_value v
using custom_field_split_value sv
where v.municipality_id = sv.municipality_id and v.id = sv.old_value_id;

delete from custom_field_usage u
using custom_field_split s
where u.municipality_id = s.municipality_id and u.id = s.usage_id;

do $$
declare
    before custom_field_split_counts%rowtype;
begin
    select * into before from custom_field_split_counts;
    if (select count(*) from custom_field) <> before.fields + before.split
       or (select count(*) from custom_field_usage) <> before.usages
       or (select count(*) from custom_field_value) <> before.field_values
       or (select count(*) from custom_field_value_option) <> before.choices
       or (select count(*) from custom_field_option o
           join custom_field_split s on s.municipality_id = o.municipality_id
                                    and s.new_field_id = o.field_id) <> before.copied_options
       or exists (select 1 from custom_field f
                  where not exists (select 1 from custom_field_usage u
                                    where u.municipality_id = f.municipality_id
                                      and u.field_id = f.id)) then
        raise exception 'custom_field: os recontos non casan despois de partir os campos';
    end if;
end
$$;

alter table custom_field_usage
    add constraint custom_field_usage_field_uk unique (municipality_id, field_id);

-- 3. Unha reserva é dunha persoa: os seus campos non son públicos.
alter table custom_field_usage
    add constraint custom_field_usage_booking_visibility_ck
        check (target <> 'BOOKING' or visibility <> 'PUBLIC');
