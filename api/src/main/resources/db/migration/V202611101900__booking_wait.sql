-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Lista de espera (ADR-0033, puntos 3, 4, 8 e 12).
--
-- booking_wait: unha conta pide que se lle avise se queda libre unha franxa dun uso. Garda a
-- conta, a clave do seu correo verificado (shared.domain.EmailKey.of, a mesma ca
-- booking.holder_email_key; a base non normaliza) e o idioma do aviso. O límite de 3 esperas
-- activas por conta e concello vai na base: cada espera leva un ordinal de 1 a 3 e
-- booking_wait_limit_uk non deixa repetilo. Bórrase cando a persoa deixa de esperar, o seu correo
-- reserva esa hora, a hora deixa de existir ou a hora empeza.
--
-- booking_wait_tally: o reconto anónimo que queda ao pasar a hora (demanda non atendida): uso,
-- franxa e cantas persoas esperaban. Sen ningún dato de persoa.

create table booking_wait (
    id              uuid        primary key,
    municipality_id uuid        not null references municipality (id),
    facility_id     uuid        not null,
    use_id          uuid        not null,
    slot_start      timestamptz not null,
    slot_end        timestamptz not null,
    account_subject text        not null,
    email_key       text        not null,
    language        text        not null,
    ordinal         smallint    not null,
    created_at      timestamptz not null,
    constraint booking_wait_tenant_uk unique (municipality_id, id),
    constraint booking_wait_once_uk unique (municipality_id, account_subject, use_id, slot_start),
    constraint booking_wait_limit_uk unique (municipality_id, account_subject, ordinal),
    constraint booking_wait_use_fk foreign key (municipality_id, facility_id, use_id)
        references facility_use (municipality_id, facility_id, id),
    constraint booking_wait_subject_ck check (length(account_subject) between 1 and 255),
    constraint booking_wait_email_ck check (length(email_key) between 3 and 254),
    constraint booking_wait_language_ck check (language in ('gl', 'es')),
    constraint booking_wait_ordinal_ck check (ordinal between 1 and 3),
    constraint booking_wait_slot_ck check (slot_end > slot_start)
);

create index booking_wait_facility_idx on booking_wait (municipality_id, facility_id, slot_start);
create index booking_wait_start_idx on booking_wait (municipality_id, slot_start);
create index booking_wait_email_idx on booking_wait (municipality_id, email_key, use_id, slot_start);

alter table booking_wait enable row level security;

create policy booking_wait_tenant on booking_wait
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

create table booking_wait_tally (
    municipality_id uuid        not null references municipality (id),
    facility_id     uuid        not null,
    use_id          uuid        not null,
    slot_start      timestamptz not null,
    waits           int         not null,
    constraint booking_wait_tally_pk primary key (municipality_id, use_id, slot_start),
    constraint booking_wait_tally_use_fk foreign key (municipality_id, facility_id, use_id)
        references facility_use (municipality_id, facility_id, id),
    constraint booking_wait_tally_waits_ck check (waits >= 1)
);

alter table booking_wait_tally enable row level security;

create policy booking_wait_tally_tenant on booking_wait_tally
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

-- Baixa dunha conta (ADR-0031, punto 6; ADR-0033, punto 12): bórranse tamén as súas esperas, en
-- todos os concellos. Execútaa o dono da base, que non pasa pola RLS.
create or replace function close_account(account text) returns void
    language sql
    set search_path = pg_catalog, public, pg_temp
as $$
    delete from public.booking_wait where account_subject = account;
    delete from public.account_email where account_subject = account;
    delete from public.account_dependent d
     where d.account_subject = account
       and not exists (select 1 from public.booking_participant p where p.dependent_id = d.id);
    update public.account_dependent
       set deactivated_at = now(), updated_at = now(), version = version + 1
     where account_subject = account and deactivated_at is null;
$$;

revoke all on function close_account(text) from public;
