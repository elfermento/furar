-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Historia da reserva (ADR-0025, punto 12): unha fila por transición (creada, cambiada, cancelada,
-- mantida ante un cambio da instalación, pagada, devolta, cobro desfeito), con quen e cando, o
-- período de antes e o de despois ao cambiar e as canles avisadas cando decide o concello. Táboa do
-- concello (ADR-0006) e só de engadir: o rol da API (furar_app) só insire e le, como audit_event.

create table booking_event (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    booking_id      uuid not null,
    kind            text not null,
    at              timestamptz not null,
    actor_subject   text not null,
    from_period     tstzrange,
    to_period       tstzrange,
    channels        text[] not null default '{}',
    constraint booking_event_tenant_uk unique (municipality_id, id),
    constraint booking_event_booking_fk foreign key (municipality_id, booking_id)
        references booking (municipality_id, id),
    constraint booking_event_kind_ck check (kind in (
        'CREATED', 'CHANGED', 'CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY', 'KEPT', 'PAID',
        'REFUNDED', 'PAYMENT_UNDONE')),
    constraint booking_event_actor_ck check (length(actor_subject) between 1 and 255),
    -- Un cambio garda o período de antes e o de despois.
    constraint booking_event_change_ck
        check (kind <> 'CHANGED' or (from_period is not null and to_period is not null)),
    -- As canles só cando decide o concello (cancelar ou manter ante un cambio da instalación).
    constraint booking_event_channels_ck check (
        channels <@ array['EMAIL', 'WEB', 'PHONE']::text[]
        and (cardinality(channels) = 0 or kind in ('CANCELLED_BY_MUNICIPALITY', 'KEPT')))
);

create index booking_event_booking_idx on booking_event (municipality_id, booking_id, at);

alter table booking_event enable row level security;

create policy booking_event_tenant on booking_event
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

do $$
begin
  if exists (select 1 from pg_roles where rolname = '${approle}') and '${approle}' <> current_user then
    execute format('revoke update, delete, truncate on booking_event from %I', '${approle}');
  end if;
end
$$;
