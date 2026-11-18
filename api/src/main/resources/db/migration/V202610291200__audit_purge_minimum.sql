-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Rexistro de actividade, revisión do 2026-10-29 (ADR-0020; LOPDGDD art. 32): o evento vese un ano,
-- queda bloqueado ata FURAR_AUDIT_RETENTION (3 anos por defecto) e despois bórrase. O mínimo da
-- conservación sube de 90 días a un ano: a función nunca borra nada con menos dun ano, pida o que
-- pida furar_app. create or replace mantén o dono, security definer e os permisos.

create or replace function purge_audit_events(older_than timestamptz) returns bigint
    language sql
    security definer
    set search_path = pg_catalog, public, pg_temp
as $$
    with purged as (
        delete from public.audit_event
        where occurred_at < least(older_than, now() - interval '1 year')
        returning 1
    )
    select count(*) from purged;
$$;
