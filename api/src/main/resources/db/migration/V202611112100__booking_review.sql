-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Valoracións (ADR-0037): quen reservou valora a instalación dunha reserva pasada á que foi, de 1
-- a 5 estrelas e cun comentario opcional de 300 caracteres como moito. Unha por reserva
-- (booking_review_once_uk); non se cambia nin se borra desde a web. A valoración non garda a
-- persoa: chega a ela só pola reserva, que se anonimiza co seu prazo. A instalación e o uso
-- cópianse da reserva ao gardala, para ler a media e a lista pública sen ir á reserva.
--
-- O xestor pode ocultar o comentario (hidden_at e hidden_by, o sub de quen o ocultou): deixa de
-- verse en público, pero a nota segue contando na media.

create table booking_review (
    id              uuid        primary key,
    municipality_id uuid        not null references municipality (id),
    booking_id      uuid        not null,
    facility_id     uuid        not null,
    use_id          uuid        not null,
    stars           smallint    not null,
    comment         text,
    created_at      timestamptz not null,
    hidden_at       timestamptz,
    hidden_by       text,
    constraint booking_review_tenant_uk unique (municipality_id, id),
    constraint booking_review_once_uk unique (municipality_id, booking_id),
    constraint booking_review_booking_fk foreign key (municipality_id, booking_id)
        references booking (municipality_id, id),
    constraint booking_review_stars_ck check (stars between 1 and 5),
    constraint booking_review_comment_ck check (length(comment) between 1 and 300),
    constraint booking_review_hidden_ck check (
        (hidden_at is null) = (hidden_by is null)
        and (hidden_at is null or comment is not null)),
    constraint booking_review_hidden_by_ck check (length(hidden_by) between 1 and 255)
);

-- A media e a lista pública dunha instalación, das máis novas ás máis vellas.
create index booking_review_facility_idx on booking_review (municipality_id, facility_id, created_at);
-- A táboa do xestor e a cifra dos indicadores, por data.
create index booking_review_created_idx on booking_review (municipality_id, created_at);

alter table booking_review enable row level security;

create policy booking_review_tenant on booking_review
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

-- Baixa dunha conta (ADR-0031, punto 6; ADR-0037): bórranse tamén as valoracións das súas
-- reservas, en todos os concellos. Execútaa o dono da base, que non pasa pola RLS.
create or replace function close_account(account text) returns void
    language sql
    set search_path = pg_catalog, public, pg_temp
as $$
    delete from public.booking_review r
     using public.booking b
     where b.municipality_id = r.municipality_id and b.id = r.booking_id
       and b.holder_subject = account;
    delete from public.account_display_preference where account_subject = account;
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
