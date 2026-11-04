-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Prazas (ADR-0012): contador por uso e franxa, só nos usos por prazas. Quen reserva, na mesma
-- transacción e en orde (use_id, slot_start): insert … on conflict do nothing, select … for update e
-- taken = taken + 1. O CHECK fai imposible pasarse aínda cun erro no código; cancelar resta.
-- capacity é copia da do uso porque o CHECK atómico a necesita: só a cambia booking, baixo o
-- bloqueo da instalación. rental_mode fixo e a FK impiden un contador nun uso que non é por prazas.
-- Sen id: ninguén apunta a unha fila; a clave empeza por municipality_id (SchemaTenancyIT).

create table use_slot (
    municipality_id uuid not null references municipality (id),
    use_id          uuid not null,
    slot_start      timestamptz not null,
    rental_mode     text not null default 'SEATS',
    capacity        int not null,
    taken           int not null default 0,
    constraint use_slot_pk primary key (municipality_id, use_id, slot_start),
    constraint use_slot_use_fk foreign key (municipality_id, use_id, rental_mode)
        references facility_use (municipality_id, id, rental_mode),
    constraint use_slot_rental_mode_ck check (rental_mode = 'SEATS'),
    constraint use_slot_capacity_ck check (capacity between 2 and 10000),
    constraint use_slot_taken_ck check (taken between 0 and capacity)
);

alter table use_slot enable row level security;

create policy use_slot_tenant on use_slot
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
