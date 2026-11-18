-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Buscar xente para xogar (ADR-0040): quen ten unha reserva confirmada e futura dunha pista enteira
-- ou por partes ábrea a outras persoas. Unha partida por reserva (booking_open_match_booking_uk);
-- quen se une non ten reserva nin ocupa praza (non vai a booking_participant).
--
-- booking_open_match: «Xa somos» (known_count, con quen organiza), mínimo e máximo de persoas
-- totais, nivel (nulo = sen indicar), título, descrición e «Con aprobación». O nome de quen
-- organiza é o do token e publícase: nunca un correo (booking_open_match_name_ck). Estados: OPEN
-- (busca xente), CLOSED («Deixar de buscar») e CANCELLED (co motivo). deadline_checked_at: o prazo
-- do concello pasou co mínimo cuberto. hidden_at e hidden_by: a xestión ocultouna.
--
-- booking_open_match_player: quen se une ou o pide, co nome, o correo e o idioma do token. Unha
-- persoa, unha vez (booking_open_match_player_once_uk). As pendentes non ocupan sitio.

create table booking_open_match (
    id                  uuid        primary key,
    municipality_id     uuid        not null references municipality (id),
    booking_id          uuid        not null,
    organizer_subject   text        not null,
    organizer_name      text        not null,
    known_count         smallint    not null,
    minimum             smallint    not null,
    maximum             smallint    not null,
    level               text,
    title               text,
    description         text,
    approval            boolean     not null default false,
    status              text        not null default 'OPEN',
    cancel_reason       text,
    deadline_checked_at timestamptz,
    hidden_at           timestamptz,
    hidden_by           text,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    version             bigint      not null,
    constraint booking_open_match_tenant_uk unique (municipality_id, id),
    constraint booking_open_match_booking_uk unique (municipality_id, booking_id),
    constraint booking_open_match_booking_fk foreign key (municipality_id, booking_id)
        references booking (municipality_id, id),
    constraint booking_open_match_subject_ck check (length(organizer_subject) between 1 and 255),
    constraint booking_open_match_name_ck check (
        length(organizer_name) between 1 and 200 and organizer_name not like '%@%'),
    constraint booking_open_match_known_ck check (known_count between 1 and 39),
    constraint booking_open_match_minimum_ck check (minimum between 2 and 40),
    constraint booking_open_match_maximum_ck check (maximum between 2 and 40),
    constraint booking_open_match_counts_ck check (maximum >= minimum and known_count < maximum),
    constraint booking_open_match_level_ck check (level in ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    constraint booking_open_match_title_ck check (length(title) between 1 and 80),
    constraint booking_open_match_description_ck check (length(description) between 1 and 1000),
    constraint booking_open_match_status_ck check (status in ('OPEN', 'CLOSED', 'CANCELLED')),
    constraint booking_open_match_reason_ck check (
        cancel_reason in ('UNFILLED', 'BOOKING_CANCELLED')),
    constraint booking_open_match_cancelled_ck check (
        (status = 'CANCELLED') = (cancel_reason is not null)),
    constraint booking_open_match_hidden_ck check ((hidden_at is null) = (hidden_by is null)),
    constraint booking_open_match_hidden_by_ck check (length(hidden_by) between 1 and 255)
);

-- As listas públicas e a tarefa do prazo: só as abertas.
create index booking_open_match_open_idx on booking_open_match (municipality_id, status)
    where status = 'OPEN';
-- As partidas que organiza unha conta (as súas e a baixa da conta).
create index booking_open_match_organizer_idx
    on booking_open_match (municipality_id, organizer_subject);

alter table booking_open_match enable row level security;

create policy booking_open_match_tenant on booking_open_match
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

create table booking_open_match_player (
    id              uuid        primary key,
    municipality_id uuid        not null references municipality (id),
    match_id        uuid        not null,
    account_subject text        not null,
    email           text        not null,
    name            text        not null,
    language        text        not null,
    status          text        not null,
    created_at      timestamptz not null,
    decided_at      timestamptz,
    constraint booking_open_match_player_tenant_uk unique (municipality_id, id),
    constraint booking_open_match_player_once_uk unique (municipality_id, match_id, account_subject),
    constraint booking_open_match_player_match_fk foreign key (municipality_id, match_id)
        references booking_open_match (municipality_id, id) on delete cascade,
    constraint booking_open_match_player_subject_ck check (length(account_subject) between 1 and 255),
    constraint booking_open_match_player_email_ck check (length(email) between 3 and 254),
    constraint booking_open_match_player_name_ck check (
        length(name) between 1 and 200 and name not like '%@%'),
    constraint booking_open_match_player_language_ck check (language in ('gl', 'es')),
    constraint booking_open_match_player_status_ck check (
        status in ('PENDING', 'CONFIRMED', 'REJECTED')),
    constraint booking_open_match_player_pending_ck check (status <> 'PENDING' or decided_at is null),
    constraint booking_open_match_player_rejected_ck check (
        status <> 'REJECTED' or decided_at is not null)
);

-- As partidas nas que está ou pediu entrar unha conta.
create index booking_open_match_player_account_idx
    on booking_open_match_player (municipality_id, account_subject);

alter table booking_open_match_player enable row level security;

create policy booking_open_match_player_tenant on booking_open_match_player
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
