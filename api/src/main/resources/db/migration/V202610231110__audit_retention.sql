-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Conservación do rexistro de actividade (ADR-0020, RGPD art. 5.1.e): o sub é un dato persoal.
-- furar_app segue sen DELETE nin TRUNCATE en audit_event (unha inxección SQL non borra o rastro);
-- só pode chamar a esta función, que se executa como o dono (furar_owner) e nunca borra nada
-- con menos de 90 días, pida o que pida. A API chámaa unha vez ao día con furar.audit.retention.

create function purge_audit_events(older_than timestamptz) returns bigint
    language sql
    security definer
    set search_path = pg_catalog, public, pg_temp
as $$
    with purged as (
        delete from public.audit_event
        where occurred_at < least(older_than, now() - interval '90 days')
        returning 1
    )
    select count(*) from purged;
$$;

revoke all on function purge_audit_events(timestamptz) from public;

do $$
begin
  if exists (select 1 from pg_roles where rolname = '${approle}') and '${approle}' <> current_user then
    execute format('grant execute on function purge_audit_events(timestamptz) to %I', '${approle}');
  end if;
end
$$;
