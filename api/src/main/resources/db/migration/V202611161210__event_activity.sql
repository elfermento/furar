-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Actividades con horario (ADR-0041): o evento gaña un tipo (dun día, o de sempre, ou con
-- horario), as sesións, a asistencia, a lista de espera automática, as persoas a cargo, o límite de
-- idade e cada canto se paga o prezo. Todas as táboas novas son do concello, coa súa RLS e a
-- política <táboa>_tenant (modelo: V202610231100__row_level_security.sql).
--
--   * event: kind ('ONE_DAY' | 'SCHEDULED'); ends_at (nun evento dun día, igual a starts_at; nunha
--     actividade, o fin da última sesión); o horario (uso e parte da instalación, sen FK coma
--     facility_id; os días da semana nunha máscara de bits coma os usos; minutos de 15 en 15;
--     datas como moito un ano), ended_from (rematada antes), o límite de idade polo ano de
--     nacemento e price_period (só informativo).
--   * event_session: unha fila por día da actividade; nunha instalación, a reserva que a ocupa
--     (booking_id, sen FK: a reserva é de booking e a súa vida vai polas súas regras).
--   * event_attendance: presente ou ausente por sesión e inscrición; sen fila, sen marcar.
--   * event_registration: a persoa a cargo (dependent_id e o seu nome copiado); nun evento con
--     límite de idade, a data de nacemento que dá a propia persoa (birth_date) ou o ano da ficha
--     da persoa a cargo (birth_year), só nesta inscrición e nunca na conta; os estados da lista de
--     espera (WAITING, NOTIFIED), o aviso dunha praza libre (cando empeza a contar, ata cando é só
--     súa e cando saíu o correo) e cando outra persoa cubriu a praza da que se avisara.
--     Unha persoa, unha inscrición por evento: a conta para si e cada persoa a cargo unha vez.

alter table event
    add column kind text not null default 'ONE_DAY',
    add column ends_at timestamptz,
    add column use_id uuid,
    add column part_id uuid,
    add column weekdays smallint,
    add column start_minute int,
    add column end_minute int,
    add column first_date date,
    add column last_date date,
    add column ended_from date,
    add column birth_year_from smallint,
    add column birth_year_to smallint,
    add column price_period text not null default 'ONCE';

update event set ends_at = starts_at;

alter table event alter column ends_at set not null;

alter table event drop constraint event_deadline_ck;

alter table event
    add constraint event_kind_ck check (kind in ('ONE_DAY', 'SCHEDULED')),
    add constraint event_price_period_ck check (price_period in ('ONCE', 'MONTH', 'QUARTER', 'SEASON')),
    add constraint event_ends_ck check (ends_at >= starts_at and (kind = 'SCHEDULED' or ends_at = starts_at)),
    add constraint event_deadline_ck check (registration_deadline is null or registration_deadline <= ends_at),
    -- O horario vai enteiro nunha actividade e non vai nun evento dun día.
    add constraint event_schedule_ck check (
        (kind = 'SCHEDULED') = (num_nonnulls(weekdays, start_minute, end_minute, first_date, last_date) = 5)
        and (kind = 'SCHEDULED' or num_nonnulls(use_id, part_id, ended_from) = 0)),
    -- Días da semana como os dos usos de facility: un bit por día, luns = 1 … domingo = 64.
    add constraint event_weekdays_ck check (weekdays is null or weekdays between 1 and 127),
    add constraint event_minutes_ck check (start_minute is null
        or (start_minute between 0 and 1425 and start_minute % 15 = 0
            and end_minute between 15 and 1440 and end_minute % 15 = 0 and end_minute > start_minute)),
    add constraint event_dates_ck check (first_date is null
        or (last_date >= first_date and last_date <= first_date + 365)),
    add constraint event_ended_from_ck check (ended_from is null or ended_from between first_date and last_date),
    -- Nunha instalación a actividade leva o uso; noutro sitio, ningún. A parte, só cun uso.
    add constraint event_use_ck check ((kind <> 'SCHEDULED' or (facility_id is null) = (use_id is null))
        and (part_id is null or use_id is not null)),
    add constraint event_birth_years_ck check (
        (birth_year_from is null or birth_year_from between 1900 and 2200)
        and (birth_year_to is null or birth_year_to between 1900 and 2200)
        and (birth_year_from is null or birth_year_to is null or birth_year_from <= birth_year_to));

create index event_ends_idx on event (municipality_id, ends_at);

create table event_session (
    id                  uuid primary key,
    municipality_id     uuid not null references municipality (id),
    event_id            uuid not null,
    session_date        date not null,
    starts_at           timestamptz not null,
    ends_at             timestamptz not null,
    booking_id          uuid,
    cancelled_at        timestamptz,
    attendance_taken_at timestamptz,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    version             bigint not null,
    constraint event_session_tenant_uk unique (municipality_id, id),
    constraint event_session_day_uk unique (municipality_id, event_id, session_date),
    constraint event_session_event_fk foreign key (municipality_id, event_id)
        references event (municipality_id, id) on delete cascade,
    constraint event_session_time_ck check (ends_at > starts_at)
);

create index event_session_event_idx on event_session (municipality_id, event_id, starts_at);
create index event_session_booking_idx on event_session (municipality_id, booking_id)
    where booking_id is not null;

alter table event_session enable row level security;

create policy event_session_tenant on event_session
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

alter table event_registration
    add column dependent_id uuid,
    add column dependent_name text,
    add column birth_year smallint,
    add column birth_date date,
    add column notified_at timestamptz,
    add column exclusive_until timestamptz,
    add column notice_mailed_at timestamptz,
    add column covered_at timestamptz;

alter table event_registration drop constraint event_registration_status_ck;
alter table event_registration drop constraint event_registration_anonymized_ck;

alter table event_registration
    add constraint event_registration_status_ck
        check (status in ('PENDING', 'CONFIRMED', 'REJECTED', 'WAITING', 'NOTIFIED')),
    -- O aviso: só NOTIFIED, coas dúas datas e a exclusividade rematando despois de empezar.
    add constraint event_registration_notice_ck check (
        (status = 'NOTIFIED') = (notified_at is not null)
        and (notified_at is null) = (exclusive_until is null)
        and (exclusive_until is null or exclusive_until > notified_at)
        and (notice_mailed_at is null or notified_at is not null)
        and (covered_at is null or status = 'WAITING')),
    -- Na lista de espera aínda non hai pago.
    add constraint event_registration_waiting_payment_ck
        check (status not in ('WAITING', 'NOTIFIED') or payment_status is null),
    add constraint event_registration_dependent_ck check ((dependent_id is null) = (dependent_name is null)
        and (dependent_name is null or length(dependent_name) between 1 and 200)),
    add constraint event_registration_birth_year_ck check (birth_year is null or birth_year between 1900 and 2200),
    -- A data, só da propia persoa; o ano, só da persoa a cargo.
    add constraint event_registration_birth_date_ck check ((birth_date is null or birth_date >= date '1900-01-01')
        and (birth_date is null or dependent_id is null) and (birth_year is null or dependent_id is not null)),
    -- Unha anonimizada xa non ten persoa (nin a persoa a cargo nin o ano ou a data de nacemento).
    add constraint event_registration_anonymized_ck check (anonymized_at is null
        or (person_subject is null and person_name is null and person_email is null
            and dependent_id is null and dependent_name is null and birth_year is null
            and birth_date is null));

-- A conta, unha vez para si e unha vez por cada persoa a cargo.
drop index event_registration_person_uk;
create unique index event_registration_person_uk
    on event_registration (municipality_id, event_id, person_subject)
    where person_subject is not null and dependent_id is null;
create unique index event_registration_dependent_uk
    on event_registration (municipality_id, event_id, dependent_id)
    where dependent_id is not null;
-- A orde da lista de espera e os avisos cuxo correo aínda non saíu.
create index event_registration_waiting_idx
    on event_registration (municipality_id, event_id, created_at, id)
    where status in ('WAITING', 'NOTIFIED');
create index event_registration_notice_idx
    on event_registration (municipality_id, notified_at)
    where status = 'NOTIFIED' and notice_mailed_at is null;

create table event_attendance (
    municipality_id uuid not null references municipality (id),
    session_id      uuid not null,
    registration_id uuid not null,
    present         boolean not null,
    marked_at       timestamptz not null,
    primary key (municipality_id, session_id, registration_id),
    constraint event_attendance_session_fk foreign key (municipality_id, session_id)
        references event_session (municipality_id, id) on delete cascade,
    constraint event_attendance_registration_fk foreign key (municipality_id, registration_id)
        references event_registration (municipality_id, id) on delete cascade
);

create index event_attendance_registration_idx on event_attendance (municipality_id, registration_id);

alter table event_attendance enable row level security;

create policy event_attendance_tenant on event_attendance
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
