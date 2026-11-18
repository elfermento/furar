-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Tempadas das entidades (ADR-0039, puntos 4 e 5): unha serie é un horario fixo dunha entidade
-- nunha instalación, un uso e unha parte (días da semana ISO, luns = 1; minutos de reloxo de
-- parede de 15 en 15; datas, como moito un ano). As sesións son reservas normais da entidade, cun
-- vínculo á serie (booking.series_id): ocupan coa mesma booking_occupancy e as mesmas exclusións.
-- A clave de reintento gárdase na serie coa pegada SHA-256 da entrada (coma
-- booking_idempotency_key). request_id: a solicitude da que saíu, sen FK (a solicitude apunta á
-- serie).
--
-- booking_series_request: a tempada que pide unha entidade. As decididas bórranse un ano despois
-- (furar.booking.series-request-retention); a conta e o correo de quen a pide quedan a nulo coa
-- baixa da conta (close_account).
--
-- booking: organization_id (a reserva é da entidade: sen conta, correo nin teléfono de titular;
-- booked_by é quen a fixo) e series_id (unha sesión: sen prezo).

create table booking_series (
    id                      uuid        primary key,
    municipality_id         uuid        not null references municipality (id),
    organization_id         uuid        not null,
    facility_id             uuid        not null,
    use_id                  uuid        not null,
    part_id                 uuid,
    rental_mode             text        not null,
    weekdays                smallint[]  not null,
    start_minute            int         not null,
    end_minute              int         not null,
    first_date              date        not null,
    last_date               date        not null,
    ended_from              date,
    payment_status          text        not null default 'FREE',
    request_id              uuid,
    idempotency_key         text        not null,
    idempotency_fingerprint text        not null,
    created_by              text        not null,
    created_at              timestamptz not null,
    updated_at              timestamptz not null,
    version                 bigint      not null,
    constraint booking_series_tenant_uk unique (municipality_id, id),
    constraint booking_series_org_uk unique (municipality_id, id, organization_id),
    constraint booking_series_idempotency_uk unique (municipality_id, idempotency_key),
    constraint booking_series_organization_fk foreign key (municipality_id, organization_id)
        references booking_organization (municipality_id, id),
    constraint booking_series_use_fk foreign key (municipality_id, facility_id, use_id)
        references facility_use (municipality_id, facility_id, id),
    constraint booking_series_part_fk foreign key (municipality_id, use_id, part_id)
        references facility_use_part (municipality_id, use_id, id),
    constraint booking_series_mode_fk foreign key (municipality_id, use_id, rental_mode)
        references facility_use (municipality_id, id, rental_mode),
    constraint booking_series_rental_mode_ck check (rental_mode in ('WHOLE', 'PARTS')),
    constraint booking_series_part_ck check ((rental_mode = 'PARTS') = (part_id is not null)),
    constraint booking_series_weekdays_ck
        check (cardinality(weekdays) between 1 and 7 and weekdays <@ '{1,2,3,4,5,6,7}'),
    constraint booking_series_start_ck
        check (start_minute between 0 and 1425 and start_minute % 15 = 0),
    constraint booking_series_end_ck check (end_minute between 15 and 1440 and end_minute % 15 = 0),
    constraint booking_series_time_ck check (end_minute > start_minute),
    constraint booking_series_dates_ck
        check (last_date >= first_date and last_date <= first_date + 365),
    constraint booking_series_ended_ck
        check (ended_from is null or ended_from between first_date and last_date + 1),
    constraint booking_series_payment_ck check (payment_status in ('FREE', 'PENDING', 'PAID')),
    constraint booking_series_key_ck check (idempotency_key ~ '^[!#-\[\]-~]{1,255}$'),
    constraint booking_series_fingerprint_ck check (idempotency_fingerprint ~ '^[0-9a-f]{64}$'),
    constraint booking_series_created_by_ck check (length(created_by) between 1 and 255)
);

create index booking_series_org_idx on booking_series (municipality_id, organization_id);
create index booking_series_facility_idx on booking_series (municipality_id, facility_id);

alter table booking_series enable row level security;

create policy booking_series_tenant on booking_series
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

create table booking_series_request (
    id                   uuid        primary key,
    municipality_id      uuid        not null references municipality (id),
    organization_id      uuid        not null,
    facility_id          uuid        not null,
    use_id               uuid        not null,
    part_id              uuid,
    rental_mode          text        not null,
    weekdays             smallint[]  not null,
    start_minute         int         not null,
    end_minute           int         not null,
    first_date           date        not null,
    last_date            date        not null,
    note                 text,
    requested_by_subject text,
    requested_by_email   text,
    status               text        not null default 'PENDING',
    rejection_message    text,
    series_id            uuid,
    decided_at           timestamptz,
    decided_by           text,
    created_at           timestamptz not null,
    updated_at           timestamptz not null,
    version              bigint      not null,
    constraint booking_series_request_tenant_uk unique (municipality_id, id),
    constraint booking_series_request_organization_fk foreign key (municipality_id, organization_id)
        references booking_organization (municipality_id, id) on delete cascade,
    constraint booking_series_request_use_fk foreign key (municipality_id, facility_id, use_id)
        references facility_use (municipality_id, facility_id, id),
    constraint booking_series_request_part_fk foreign key (municipality_id, use_id, part_id)
        references facility_use_part (municipality_id, use_id, id),
    -- Só a columna da serie a nulo: municipality_id é not null.
    constraint booking_series_request_series_fk foreign key (municipality_id, series_id)
        references booking_series (municipality_id, id) on delete set null (series_id),
    constraint booking_series_request_rental_mode_ck check (rental_mode in ('WHOLE', 'PARTS')),
    constraint booking_series_request_part_ck
        check ((rental_mode = 'PARTS') = (part_id is not null)),
    constraint booking_series_request_weekdays_ck
        check (cardinality(weekdays) between 1 and 7 and weekdays <@ '{1,2,3,4,5,6,7}'),
    constraint booking_series_request_start_ck
        check (start_minute between 0 and 1425 and start_minute % 15 = 0),
    constraint booking_series_request_end_ck
        check (end_minute between 15 and 1440 and end_minute % 15 = 0),
    constraint booking_series_request_time_ck check (end_minute > start_minute),
    constraint booking_series_request_dates_ck
        check (last_date >= first_date and last_date <= first_date + 365),
    constraint booking_series_request_note_ck check (length(note) between 1 and 500),
    constraint booking_series_request_subject_ck
        check (length(requested_by_subject) between 1 and 255),
    constraint booking_series_request_email_ck check (length(requested_by_email) between 3 and 254),
    constraint booking_series_request_status_ck
        check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    constraint booking_series_request_message_ck check (length(rejection_message) between 1 and 1000),
    constraint booking_series_request_decided_ck check ((status = 'PENDING') = (decided_at is null)),
    constraint booking_series_request_decided_by_ck
        check ((decided_at is null) = (decided_by is null) and length(decided_by) between 1 and 255),
    constraint booking_series_request_rejected_ck
        check ((status = 'REJECTED') = (rejection_message is not null)),
    constraint booking_series_request_series_ck check (series_id is null or status = 'APPROVED')
);

create index booking_series_request_pending_idx
    on booking_series_request (municipality_id, created_at) where status = 'PENDING';
create index booking_series_request_org_idx
    on booking_series_request (municipality_id, organization_id);
create index booking_series_request_facility_idx
    on booking_series_request (municipality_id, facility_id);

alter table booking_series_request enable row level security;

create policy booking_series_request_tenant on booking_series_request
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

alter table booking
    add column organization_id uuid,
    add column series_id uuid,
    add constraint booking_organization_fk foreign key (municipality_id, organization_id)
        references booking_organization (municipality_id, id),
    add constraint booking_series_fk foreign key (municipality_id, series_id, organization_id)
        references booking_series (municipality_id, id, organization_id),
    add constraint booking_series_org_ck check (series_id is null or organization_id is not null),
    add constraint booking_organization_holder_ck check (
        organization_id is null
        or (holder_subject is null and holder_email is null and holder_phone is null
            and booked_by is not null)),
    add constraint booking_series_price_ck check (series_id is null or price_kind = 'NONE');

create index booking_series_session_idx on booking (municipality_id, series_id, lower(period))
    where series_id is not null;
create index booking_organization_idx on booking (municipality_id, organization_id, lower(period))
    where organization_id is not null;
