-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Datos abertos (ADR-0034, puntos 3 e 6): a ocupación publicada, precalculada cada noite (un día
-- por fila, un valor por tramo de 15 minutos), e a data de cambio de cada conxunto. Sen persoa,
-- reserva, conta nin texto libre (OpenDataSchemaIT). RLS coa política única (ADR-0006).

create table open_data_occupancy (
    municipality_id uuid not null references municipality (id),
    facility_id     uuid not null,
    day             date not null,
    -- Un valor por tramo de 15 minutos desde a medianoite de Europe/Madrid, en tempo real (96;
    -- 92 e 100 os días do cambio de hora): 0–100 aberta, null pechada ou bloqueada.
    quarters        smallint[] not null,
    -- Os usos distintos que ocuparon a instalación ese día.
    uses            uuid[] not null,
    -- Por tramo, a posición (desde 1) en uses do uso que o ocupaba; null se estaba libre.
    quarter_uses    smallint[] not null,
    constraint open_data_occupancy_pk primary key (municipality_id, facility_id, day),
    constraint open_data_occupancy_facility_fk foreign key (municipality_id, facility_id)
        references facility (municipality_id, id) on delete cascade,
    constraint open_data_occupancy_quarters_ck
        check (cardinality(quarters) between 92 and 100
               and cardinality(quarter_uses) = cardinality(quarters))
);

create index open_data_occupancy_day_idx on open_data_occupancy (municipality_id, day);

alter table open_data_occupancy enable row level security;

create policy open_data_occupancy_tenant on open_data_occupancy
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

create table open_data_dataset (
    municipality_id    uuid not null references municipality (id),
    dataset            text not null,
    checksum           text not null,
    changed_on         date not null,
    -- Só en OCCUPANCY: o último día calculado.
    calculated_through date,
    constraint open_data_dataset_pk primary key (municipality_id, dataset),
    constraint open_data_dataset_ck check (dataset in ('FACILITIES', 'CLOSURES', 'OCCUPANCY')),
    constraint open_data_dataset_through_ck
        check (calculated_through is null or dataset = 'OCCUPANCY')
);

alter table open_data_dataset enable row level security;

create policy open_data_dataset_tenant on open_data_dataset
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
