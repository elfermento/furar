-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Esperas canceladas porque a instalación deixou de reservarse por internet, se pechou ou se deu
-- de baixa (revisión do 2026-11-11 de ADR-0033): a espera bórrase coma antes e, na mesma
-- transacción, queda aquí o mínimo para escribirlle á persoa despois do commit (o correo, o
-- idioma e a franxa). O evento non leva ningún dato de persoa: só cancellation_id. Unha persoa
-- con varias esperas na mesma cancelación recibe un só correo. Bórrase aos 7 días
-- (WaitExpiryService), coma o envío do aviso.

create table booking_wait_cancelled (
    id              uuid        primary key,
    municipality_id uuid        not null references municipality (id),
    cancellation_id uuid        not null,
    facility_id     uuid        not null,
    email_key       text        not null,
    language        text        not null,
    slot_start      timestamptz not null,
    slot_end        timestamptz not null,
    cancelled_at    timestamptz not null,
    constraint booking_wait_cancelled_tenant_uk unique (municipality_id, id),
    constraint booking_wait_cancelled_email_ck check (length(email_key) between 3 and 254),
    constraint booking_wait_cancelled_language_ck check (language in ('gl', 'es')),
    constraint booking_wait_cancelled_slot_ck check (slot_end > slot_start)
);

create index booking_wait_cancelled_idx on booking_wait_cancelled (municipality_id, cancellation_id);
create index booking_wait_cancelled_at_idx on booking_wait_cancelled (municipality_id, cancelled_at);

alter table booking_wait_cancelled enable row level security;

create policy booking_wait_cancelled_tenant on booking_wait_cancelled
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
