-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Notification (plan do dominio 2, §2.1, D2-09; ADR-0025, punto 15): que correo dunha reserva se
-- enviou, a quen, cando e co que resultado. Sen FK a booking (outro módulo, fóra da lista pechada
-- CrossModuleForeignKeys). A clave única (concello, reserva, tipo, evento) fai que un reintento do
-- rexistro de publicacións non envíe dúas veces o mesmo correo. Datos persoais (recipient):
-- conservación de ADR-0017, como a reserva.

create table notification_delivery (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    booking_id      uuid not null,
    kind            text not null,
    event_id        text not null,
    recipient       text not null,
    language        text not null,
    status          text not null,
    attempts        int not null,
    error           text,
    sent_at         timestamptz,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    constraint notification_delivery_tenant_uk unique (municipality_id, id),
    constraint notification_delivery_event_uk unique (municipality_id, booking_id, kind, event_id),
    constraint notification_delivery_kind_ck
        check (kind in ('CONFIRMED', 'CHANGED', 'CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY')),
    constraint notification_delivery_event_ck check (length(event_id) between 1 and 40),
    constraint notification_delivery_recipient_ck check (length(recipient) between 3 and 254),
    constraint notification_delivery_language_ck check (language in ('gl', 'es')),
    constraint notification_delivery_status_ck check (status in ('SENT', 'FAILED')),
    constraint notification_delivery_attempts_ck check (attempts between 1 and 10),
    constraint notification_delivery_error_ck check (error is null or length(error) <= 500),
    constraint notification_delivery_sent_ck check ((status = 'SENT') = (sent_at is not null))
);

create index notification_delivery_booking_idx on notification_delivery (municipality_id, booking_id);

alter table notification_delivery enable row level security;

create policy notification_delivery_tenant on notification_delivery
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
