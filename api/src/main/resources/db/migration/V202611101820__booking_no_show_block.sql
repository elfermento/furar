-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Bloqueos por faltas (ADR-0032, puntos 2 e 7): ao chegar ao límite, o correo non pode facer
-- reservas novas por internet neste concello ata ends_at. O correo vai normalizado pola
-- aplicación (EmailKey.of), coma booking.holder_email_key. Un bloqueo levantado (lifted_at) deixa
-- de valer e non pon a conta a cero; un cumprido si.
--
-- booking_no_show_block_fault: que reservas causaron cada bloqueo. Unha táboa filla e non un
-- uuid[]: a base garante que cada falta é unha reserva do concello e a conta faise cun not exists.

create table booking_no_show_block (
    id               uuid primary key,
    municipality_id  uuid not null references municipality (id),
    holder_email_key text not null,
    starts_at        timestamptz not null,
    ends_at          timestamptz not null,
    lifted_at        timestamptz,
    lifted_by        text,
    created_at       timestamptz not null,
    constraint booking_no_show_block_tenant_uk unique (municipality_id, id),
    constraint booking_no_show_block_email_ck check (length(holder_email_key) between 3 and 254),
    constraint booking_no_show_block_period_ck check (ends_at > starts_at),
    constraint booking_no_show_block_lifted_ck check ((lifted_at is null) = (lifted_by is null)),
    constraint booking_no_show_block_lifted_by_ck check (length(lifted_by) between 1 and 255)
);

create index booking_no_show_block_email_idx
    on booking_no_show_block (municipality_id, holder_email_key, ends_at);

alter table booking_no_show_block enable row level security;

create policy booking_no_show_block_tenant on booking_no_show_block
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

create table booking_no_show_block_fault (
    municipality_id uuid not null references municipality (id),
    block_id        uuid not null,
    booking_id      uuid not null,
    constraint booking_no_show_block_fault_pk primary key (municipality_id, block_id, booking_id),
    constraint booking_no_show_block_fault_block_fk foreign key (municipality_id, block_id)
        references booking_no_show_block (municipality_id, id),
    constraint booking_no_show_block_fault_booking_fk foreign key (municipality_id, booking_id)
        references booking (municipality_id, id)
);

create index booking_no_show_block_fault_booking_idx
    on booking_no_show_block_fault (municipality_id, booking_id);

alter table booking_no_show_block_fault enable row level security;

create policy booking_no_show_block_fault_tenant on booking_no_show_block_fault
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
