-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Rexistro de actividade (ENS op.exp.8, ADR-0020): exportacións, importacións e, despois, toda
-- acción sensible. É da plataforma (TenancyAllowlist): recolle accións sen concello e sobrevive ao
-- concello, por iso municipality_id non ten FK. Sen datos persoais alén do sub de Keycloak.
-- O rol da API (furar_app) só engade e le: unha inxección SQL non pode borrar nin cambiar o rastro.

create table audit_event
(
  id                uuid        not null primary key,
  occurred_at       timestamptz not null,
  actor_subject     text        not null,
  action            text        not null,
  outcome           text        not null check (outcome in ('SUCCESS', 'FAILURE')),
  municipality_id   uuid,
  municipality_slug text,
  request_id        text,
  details           jsonb       not null default '{}'::jsonb
);

create index audit_event_municipality_idx on audit_event (municipality_id, occurred_at desc);
create index audit_event_actor_idx on audit_event (actor_subject, occurred_at desc);

do $$
begin
  if exists (select 1 from pg_roles where rolname = '${approle}') and '${approle}' <> current_user then
    execute format('revoke update, delete, truncate on audit_event from %I', '${approle}');
  end if;
end
$$;
