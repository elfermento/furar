-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Administración da plataforma na base de Furar (ADR-0028): o rol PLATFORM_ADMIN deixa de vir de
-- Keycloak (grupo plataforma, claim furar_roles) e sae desta táboa en cada petición, como o
-- persoal sae de staff_member (ADR-0027). Keycloak só autentica.
--
-- Táboa da plataforma, sen concello (TenancyAllowlist, como municipality e audit_event): a
-- administración é de toda a instalación. Sen RLS. Só o subject de Keycloak, cando e por que vía;
-- nin correo nin nome (datos-persoais.md, «Administración da plataforma»).
--
-- granted_by: o subject de quen deu o rol ou unha conta de sistema (furar:…). A primeira
-- administración dáa a API á conta co correo verificado igual a FURAR_ADMIN_EMAIL mentres non hai
-- ningunha (furar:first-run); o índice único parcial fai que iso pase unha soa vez, aínda con
-- peticións ao mesmo tempo. A demo dáa con furar:demo (db/demo/R__demo_platform_admin.sql).
--
-- furar_app só le e engade: ningunha inxección SQL pode cambiar nin borrar quen administra.

create table platform_admin (
    id         uuid primary key,
    subject    text not null,
    granted_at timestamptz not null,
    granted_by text not null,
    constraint platform_admin_subject_uk unique (subject),
    constraint platform_admin_subject_ck check (length(subject) between 1 and 255),
    constraint platform_admin_granted_by_ck check (length(granted_by) between 1 and 255)
);

create unique index platform_admin_first_run_uk on platform_admin (granted_by)
    where granted_by = 'furar:first-run';

do $$
begin
  if exists (select 1 from pg_roles where rolname = '${approle}') and '${approle}' <> current_user then
    execute format('revoke update, delete, truncate on platform_admin from %I', '${approle}');
  end if;
end
$$;
