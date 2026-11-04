-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Usos da instalación, as súas partes e os seus períodos (especificación, bloque 1; ADR-0022; plan
-- do dominio 1 §2.1, D1-12). Táboas do concello (ADR-0006): municipality_id con FK, unique
-- (municipality_id, id), FK compostas con municipality_id nos dous lados e RLS coa política única.
--
-- Os usos e as partes arquívanse, non se borran: as reservas do dominio 2 apuntarán a eles con FK
-- (municipality_id, facility_id, use_id), (municipality_id, use_id, part_id) e (municipality_id,
-- use_id, rental_mode), como no modelo de OccupancyModelIT (ADR-0012 revisada). Os períodos si se
-- substitúen enteiros: ningunha reserva apunta a eles.
--
-- Que dous usos «que bloquean os demais» non se solapen compróbase no dominio baixo o bloqueo da
-- instalación (ADR-0022, punto 4; P2), non aquí.

create table facility_use (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    facility_id     uuid not null,
    name            text not null,
    rental_mode     text not null,
    capacity        int,
    slot_minutes    int not null,
    availability    text not null,
    blocks_others   boolean not null default false,
    archived_at     timestamptz,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    version         bigint not null,
    constraint facility_use_tenant_uk unique (municipality_id, id),
    -- Destinos das FK das reservas: o uso é da instalación e o modo casa co uso.
    constraint facility_use_facility_uk unique (municipality_id, facility_id, id),
    constraint facility_use_mode_uk unique (municipality_id, id, rental_mode),
    constraint facility_use_facility_fk foreign key (municipality_id, facility_id)
        references facility (municipality_id, id),
    constraint facility_use_name_ck check (length(name) between 1 and 100 and name = btrim(name)),
    constraint facility_use_rental_mode_ck check (rental_mode in ('WHOLE', 'PARTS', 'SEATS')),
    constraint facility_use_capacity_ck check ((rental_mode = 'SEATS') = (capacity is not null)),
    -- P3: de 2 a 10 000 prazas.
    constraint facility_use_capacity_range_ck check (capacity between 2 and 10000),
    constraint facility_use_slot_minutes_ck check (slot_minutes in (30, 60, 90, 120)),
    constraint facility_use_availability_ck check (availability in ('ALWAYS', 'DATES')),
    constraint facility_use_blocks_others_ck check (not blocks_others or availability = 'DATES')
);

-- Nome único na instalación entre os usos vivos, sen distinguir maiúsculas. Índice e non
-- constraint: unha constraint non admite expresións (ADR-0022).
create unique index facility_use_name_uk on facility_use (facility_id, lower(name))
    where archived_at is null;

create table facility_use_part (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    use_id          uuid not null,
    name            text not null,
    position        int not null,
    archived_at     timestamptz,
    constraint facility_use_part_tenant_uk unique (municipality_id, id),
    constraint facility_use_part_use_uk unique (municipality_id, use_id, id),
    constraint facility_use_part_use_fk foreign key (municipality_id, use_id)
        references facility_use (municipality_id, id),
    constraint facility_use_part_name_ck check (length(name) between 1 and 100 and name = btrim(name)),
    constraint facility_use_part_position_ck check (position >= 0),
    -- Nome único no uso entre as partes vivas. Diferida: intercambiar os nomes de dúas partes nun
    -- só gardado (A↔B) pasa por un estado intermedio repetido (ADR-0022, §1.2.15 do plan). O
    -- adaptador comproba a restrición ao final do seu flush (SET CONSTRAINTS … IMMEDIATE) para
    -- traducir o erro ao código do dominio.
    constraint facility_use_part_name_ex
        exclude using gist (use_id with =, (lower(name)) with =)
        where (archived_at is null)
        deferrable initially deferred
);

create index facility_use_part_use_idx on facility_use_part (municipality_id, use_id);

create table facility_use_period (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    use_id          uuid not null,
    from_date       date not null,
    to_date         date not null,
    constraint facility_use_period_tenant_uk unique (municipality_id, id),
    constraint facility_use_period_use_fk foreign key (municipality_id, use_id)
        references facility_use (municipality_id, id),
    constraint facility_use_period_order_ck check (to_date >= from_date),
    -- Os períodos dun uso non se pisan (P5: rangos; un día = rango dun día).
    constraint facility_use_period_overlap_ex
        exclude using gist (use_id with =, (daterange(from_date, to_date, '[]')) with &&)
);

create index facility_use_period_use_idx on facility_use_period (municipality_id, use_id);

alter table facility_use enable row level security;
alter table facility_use_part enable row level security;
alter table facility_use_period enable row level security;

create policy facility_use_tenant on facility_use
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

create policy facility_use_part_tenant on facility_use_part
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

create policy facility_use_period_tenant on facility_use_period
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
