-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Preferencias de accesibilidade da conta (revisión do 2026-11-11 de ADR-0031): tamaño do texto,
-- alto contraste e menos movemento. Poden revelar unha discapacidade, así que só se garda o
-- mínimo: os tres valores e cando cambiaron, sen historial. A conta é común a todos os concellos
-- (táboa de plataforma, sen municipality_id, coma account_dependent): toda sentenza da API leva
-- account_subject = :s. Nulo nun interruptor = segue o que pida o sistema.

create table account_display_preference (
    account_subject text        primary key,
    text_size       text        not null,
    high_contrast   boolean,
    reduced_motion  boolean,
    updated_at      timestamptz not null,
    constraint account_display_preference_subject_ck check (length(account_subject) between 1 and 255),
    constraint account_display_preference_size_ck check (text_size in ('normal', 'large', 'x-large'))
);

-- Baixa dunha conta (ADR-0031, punto 6): bórranse tamén as súas preferencias. Execútaa o dono da
-- base, que non pasa pola RLS.
create or replace function close_account(account text) returns void
    language sql
    set search_path = pg_catalog, public, pg_temp
as $$
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
