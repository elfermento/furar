-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Claves de reintento de «reservar» (ADR-0025, punto 19; ADR-0009; borrador IETF
-- draft-ietf-httpapi-idempotency-key-header): unha fila por reserva creada cunha cabeceira
-- Idempotency-Key, coa persoa (holder_subject), a clave, a pegada da petición (SHA-256 en hex do
-- que se pediu) e a reserva que creou. Escríbese na mesma transacción ca a reserva e baixo o
-- bloqueo da instalación: un reintento coa mesma clave e a mesma pegada devolve esa reserva sen
-- crear outra; con outra pegada, 422. A clave é única por concello e persoa: a doutra persoa non se
-- ve. Táboa do concello (ADR-0006), con RLS; a tarefa IdempotencyKeyRetentionService borra cada
-- hora as filas de máis de 24 horas.

create table booking_idempotency_key (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    holder_subject  text not null,
    idempotency_key text not null,
    fingerprint     text not null,
    booking_id      uuid not null,
    created_at      timestamptz not null,
    constraint booking_idempotency_key_tenant_uk unique (municipality_id, id),
    constraint booking_idempotency_key_uk unique (municipality_id, holder_subject, idempotency_key),
    constraint booking_idempotency_key_booking_fk foreign key (municipality_id, booking_id)
        references booking (municipality_id, id),
    constraint booking_idempotency_key_subject_ck check (length(holder_subject) between 1 and 255),
    constraint booking_idempotency_key_key_ck check (idempotency_key ~ '^[!#-\[\]-~]{1,255}$'),
    constraint booking_idempotency_key_fingerprint_ck check (fingerprint ~ '^[0-9a-f]{64}$')
);

-- A limpeza horaria busca por data dentro de cada concello.
create index booking_idempotency_key_created_idx
    on booking_idempotency_key (municipality_id, created_at);

alter table booking_idempotency_key enable row level security;

create policy booking_idempotency_key_tenant on booking_idempotency_key
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
