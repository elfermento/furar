-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Baixa dunha conta (docs/datos-persoais.md, «Dereitos»): close_account tamén borra os
-- responsables de entidades co correo da conta e quita a conta e o correo das súas solicitudes de
-- tempada (ADR-0039, punto 18), antes de borrar account_email. O resto, igual ca en
-- V202611121030__booking_open_match_close_account.sql (a última versión anterior): esta ten que ir
-- despois.

create or replace function close_account(account text) returns void
    language sql
    set search_path = pg_catalog, public, pg_temp
as $$
    delete from public.booking_organization_manager m
     using public.account_email a
     where a.account_subject = account and m.email_key = lower(btrim(a.email));
    update public.booking_series_request
       set requested_by_subject = null, requested_by_email = null,
           updated_at = now(), version = version + 1
     where requested_by_subject = account;
    delete from public.booking_open_match where organizer_subject = account;
    delete from public.booking_open_match_player where account_subject = account;
    delete from public.event_registration r
     using public.event e
     where e.municipality_id = r.municipality_id and e.id = r.event_id
       and r.person_subject = account and e.starts_at > now();
    update public.event_registration
       set person_subject = null, person_name = null, person_email = null,
           anonymized_at = now(), updated_at = now(), version = version + 1
     where person_subject = account;
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
    delete from public.incident_photo p
     using public.incident i
     where p.municipality_id = i.municipality_id and p.incident_id = i.id
       and i.reporter_subject = account;
    update public.incident
       set reporter_subject = null, reporter_name = null, reporter_email = null,
           photos_removed_at = coalesce(photos_removed_at, now()),
           anonymized_at = now(), updated_at = now(), version = version + 1
     where reporter_subject = account;
$$;

revoke all on function close_account(text) from public;
