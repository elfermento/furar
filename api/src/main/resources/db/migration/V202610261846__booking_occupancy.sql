-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Ocupación (ADR-0012, en produción desde o dominio 2; ADR-0025): o modelo que probou
-- OccupancyModelIT, agora sobre as táboas reais. A base garante, tamén con peticións simultáneas:
--   - un uso por instalación e franxa (booking_occupancy_one_use);
--   - enteira: unha ocupación por franxa (booking_occupancy_whole);
--   - por partes: unha por parte e franxa (booking_occupancy_part);
--   - por prazas: o contador use_slot co CHECK de capacidade (migración seguinte).
-- Bloqueos, peche, horario e «bloquea os demais» non van aquí: compróbaos booking baixo o bloqueo
-- da instalación (FacilityQueries.lockFacility, ADR-0022 punto 4) antes de escribir.
--
-- Cada fila apunta á súa reserva (FK composta). Cancelar ou cambiar non borra: active = false.
-- Na fase 1 cada reserva ocupa unha praza (seats = 1).

create table booking_occupancy (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    booking_id      uuid not null,
    facility_id     uuid not null,
    use_id          uuid not null,
    part_id         uuid,
    rental_mode     text not null,
    period          tstzrange not null,
    seats           int not null default 1,
    active          boolean not null default true,
    constraint booking_occupancy_tenant_uk unique (municipality_id, id),
    constraint booking_occupancy_booking_fk foreign key (municipality_id, booking_id)
        references booking (municipality_id, id),
    -- O uso é desta instalación: a exclusión por instalación non se pode enganar.
    constraint booking_occupancy_use_fk foreign key (municipality_id, facility_id, use_id)
        references facility_use (municipality_id, facility_id, id),
    -- A parte é deste uso.
    constraint booking_occupancy_part_fk foreign key (municipality_id, use_id, part_id)
        references facility_use_part (municipality_id, use_id, id),
    -- O modo é o do uso: unha ocupación WHOLE non pode colarse nun uso por partes.
    constraint booking_occupancy_mode_fk foreign key (municipality_id, use_id, rental_mode)
        references facility_use (municipality_id, id, rental_mode),
    constraint booking_occupancy_rental_mode_ck check (rental_mode in ('WHOLE', 'PARTS', 'SEATS')),
    constraint booking_occupancy_part_ck check ((rental_mode = 'PARTS') = (part_id is not null)),
    constraint booking_occupancy_seats_ck check (seats = 1),
    constraint booking_occupancy_period_ck check (
        not isempty(period) and lower(period) is not null and upper(period) is not null
        and lower_inc(period) and not upper_inc(period)),
    -- Un uso por instalación e franxa: dúas ocupacións activas solapadas da mesma instalación
    -- teñen que ser do mesmo uso.
    constraint booking_occupancy_one_use
        exclude using gist (facility_id with =, use_id with <>, period with &&)
        where (active),
    -- Enteira: unha ocupación por franxa.
    constraint booking_occupancy_whole
        exclude using gist (use_id with =, period with &&)
        where (active and rental_mode = 'WHOLE'),
    -- Por partes: unha ocupación por parte e franxa.
    constraint booking_occupancy_part
        exclude using gist (part_id with =, period with &&)
        where (active and rental_mode = 'PARTS')
);

create index booking_occupancy_use_idx on booking_occupancy (municipality_id, use_id);
create index booking_occupancy_booking_idx on booking_occupancy (municipality_id, booking_id);

alter table booking_occupancy enable row level security;

create policy booking_occupancy_tenant on booking_occupancy
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
