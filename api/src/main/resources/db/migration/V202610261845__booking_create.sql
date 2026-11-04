-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Reserva (ADR-0025; plan do dominio 2, §2.1): un intervalo real dun uso dunha instalación (e a súa
-- parte, se se aluga por partes), coa persoa que reserva copiada do token, o prezo e as normas
-- aplicadas conxelados e dous estados separados: o da reserva e o do pago. Táboa do concello
-- (ADR-0006): municipality_id con FK, unique (municipality_id, id), FK compostas e RLS.
--
-- As FK cara a facility_use e facility_use_part son as únicas de booking a outro módulo: táboas
-- publicadas de facility na lista pechada CrossModuleForeignKeys (ADR-0005, ADR-0012). Garanten que
-- o uso é desa instalación, a parte é dese uso e o modo é o do uso.
--
-- Unha persoa = unha praza (ADR-0012, revisión do 2026-10-26): nos usos por prazas a mesma persoa
-- non pode ter dúas reservas confirmadas que se pisen no mesmo uso. btree_gist (platform_base)
-- dá o operador = para uuid e text dentro da exclusión.

create table booking (
    id                     uuid primary key,
    municipality_id        uuid not null references municipality (id),
    code                   text not null,
    facility_id            uuid not null,
    use_id                 uuid not null,
    part_id                uuid,
    rental_mode            text not null,
    period                 tstzrange not null,
    slots                  int not null,
    status                 text not null,
    payment_status         text not null,
    price_kind             text not null,
    amount_cents           int,
    cancel_until           timestamptz not null,
    rule_min_advance_hours int,
    rule_max_advance_days  int,
    rule_max_slots         int,
    rule_cap_count         int,
    rule_cap_period        text,
    rule_cancel_hours      int,
    holder_subject         text not null,
    holder_name            text not null,
    holder_email           text not null,
    holder_language        text not null,
    cancelled_at           timestamptz,
    cancelled_by           text,
    created_at             timestamptz not null,
    updated_at             timestamptz not null,
    version                bigint not null,
    constraint booking_tenant_uk unique (municipality_id, id),
    -- O código vai no QR e no mostrador: único no concello (o dominio reintenta ao chocar).
    constraint booking_code_uk unique (municipality_id, code),
    constraint booking_code_ck check (code ~ '^FUR-[A-HJ-NP-Z2-9]{4}$'),
    constraint booking_use_fk foreign key (municipality_id, facility_id, use_id)
        references facility_use (municipality_id, facility_id, id),
    constraint booking_part_fk foreign key (municipality_id, use_id, part_id)
        references facility_use_part (municipality_id, use_id, id),
    constraint booking_mode_fk foreign key (municipality_id, use_id, rental_mode)
        references facility_use (municipality_id, id, rental_mode),
    constraint booking_rental_mode_ck check (rental_mode in ('WHOLE', 'PARTS', 'SEATS')),
    constraint booking_part_ck check ((rental_mode = 'PARTS') = (part_id is not null)),
    -- Intervalo [inicio, fin) con extremos finitos.
    constraint booking_period_ck check (
        not isempty(period) and lower(period) is not null and upper(period) is not null
        and lower_inc(period) and not upper_inc(period)),
    constraint booking_slots_ck check (slots >= 1),
    constraint booking_status_ck
        check (status in ('CONFIRMED', 'CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY')),
    constraint booking_payment_status_ck
        check (payment_status in ('NONE', 'PENDING', 'PAID', 'TO_REFUND', 'REFUNDED')),
    constraint booking_price_kind_ck check (price_kind in ('NONE', 'FREE', 'PAID')),
    constraint booking_amount_ck
        check ((price_kind = 'PAID') = (amount_cents is not null) and amount_cents >= 1),
    -- Sen prezo ou gratis: nada que cobrar. De pago: sempre un estado de cobro.
    constraint booking_payment_kind_ck check ((payment_status = 'NONE') = (price_kind <> 'PAID')),
    -- Devolver só ten sentido nunha reserva cancelada.
    constraint booking_refund_ck
        check (payment_status not in ('TO_REFUND', 'REFUNDED') or status <> 'CONFIRMED'),
    constraint booking_cancelled_ck check (
        (status = 'CONFIRMED') = (cancelled_at is null)
        and (cancelled_at is null) = (cancelled_by is null)),
    constraint booking_cancel_until_ck check (cancel_until <= lower(period)),
    -- Normas aplicadas: os mesmos rangos ca no uso; baleira = non se aplicou.
    constraint booking_rule_min_advance_ck check (rule_min_advance_hours between 0 and 720),
    constraint booking_rule_max_advance_ck check (rule_max_advance_days between 1 and 365),
    constraint booking_rule_max_slots_ck check (rule_max_slots between 1 and 16),
    constraint booking_rule_cap_count_ck check (rule_cap_count between 1 and 50),
    constraint booking_rule_cap_period_ck check (rule_cap_period in ('DAY', 'WEEK', 'MONTH')),
    constraint booking_rule_cap_ck check ((rule_cap_count is null) = (rule_cap_period is null)),
    constraint booking_rule_cancel_ck check (rule_cancel_hours between 0 and 720),
    constraint booking_holder_subject_ck check (length(holder_subject) between 1 and 255),
    constraint booking_holder_name_ck check (length(holder_name) between 1 and 200),
    constraint booking_holder_email_ck
        check (length(holder_email) between 3 and 254 and holder_email like '%_@_%'),
    constraint booking_holder_language_ck check (holder_language in ('gl', 'es')),
    -- Unha persoa = unha praza no mesmo uso e franxa (só prazas e só confirmadas).
    constraint booking_one_seat_per_person
        exclude using gist (use_id with =, holder_subject with =, period with &&)
        where (status = 'CONFIRMED' and rental_mode = 'SEATS')
);

-- As miñas reservas, a lista de xestión por instalación e día, e os cobros pendentes.
create index booking_holder_idx on booking (municipality_id, holder_subject, lower(period));
create index booking_facility_idx on booking (municipality_id, facility_id, lower(period));
create index booking_payment_idx on booking (municipality_id, payment_status)
    where payment_status in ('PENDING', 'TO_REFUND');

alter table booking enable row level security;

create policy booking_tenant on booking
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
