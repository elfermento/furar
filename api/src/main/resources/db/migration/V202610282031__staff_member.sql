-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Pertenza do persoal a un concello (ADR-0027; plan do dominio 4, §1.2.1, §1.2.8 e §2.1): o
-- correo invitado, o rol (MANAGER ou STAFF) e o estado (INVITED, ACTIVE, REVOKED), con quen
-- invitou e cando, cando aceptou e con que versión das obrigas, quen deu de baixa e cando, e os
-- dous últimos accesos. Os roles do persoal saen desta táboa en cada petición, non do token.
--
-- Táboa do concello (ADR-0006): municipality_id con FK, unique (municipality_id, id) e RLS. SQL
-- nativo, sen JPA (StaffMemberSql): non é contido portable, porque o subject só vale no Keycloak
-- de cada despregue, e non entra en furar-export (ADR-0018).
--
-- invited_by e revoked_by son o subject de quen actuou (tamén pode ser a plataforma, que non ten
-- fila aquí). Datos persoais (email, display_name, subject): datos-persoais.md, «Persoal do
-- concello»; a fila revogada quédase e anonimízase ao pasar FURAR_AUDIT_RETENTION (§1.2.14), así
-- que furar_app non pode borrar filas.

create table staff_member (
    id                  uuid primary key,
    municipality_id     uuid not null references municipality (id),
    email               text not null,
    display_name        text,
    subject             text,
    role                text not null,
    status              text not null,
    language            text not null,
    invited_by          text not null,
    invited_at          timestamptz not null,
    accepted_at         timestamptz,
    obligations_version text,
    revoked_by          text,
    revoked_at          timestamptz,
    last_sign_in_at     timestamptz,
    previous_sign_in_at timestamptz,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    version             bigint not null,
    constraint staff_member_tenant_uk unique (municipality_id, id),
    -- O correo gárdase normalizado (EmailAddress): minúsculas, sen espazos, 3–254.
    constraint staff_member_email_ck check (
        email = lower(email) and email !~ '\s' and length(email) between 3 and 254
        and position('@' in email) > 1),
    constraint staff_member_display_name_ck
        check (display_name is null or length(display_name) between 1 and 200),
    constraint staff_member_subject_ck check (subject is null or length(subject) between 1 and 255),
    constraint staff_member_role_ck check (role in ('MANAGER', 'STAFF')),
    constraint staff_member_status_ck check (status in ('INVITED', 'ACTIVE', 'REVOKED')),
    constraint staff_member_language_ck check (language in ('gl', 'es')),
    constraint staff_member_invited_by_ck check (length(invited_by) between 1 and 255),
    constraint staff_member_revoked_by_ck check (revoked_by is null or length(revoked_by) between 1 and 255),
    constraint staff_member_obligations_ck
        check (obligations_version is null or length(obligations_version) between 1 and 20),
    -- Por estado (§2.1): activa = con persoa, aceptación e obrigas; invitada = sen persoa nin
    -- aceptación; revogada ⇔ quen e cando.
    constraint staff_member_active_ck check (
        status <> 'ACTIVE'
        or (subject is not null and accepted_at is not null and obligations_version is not null)),
    constraint staff_member_invited_ck
        check (status <> 'INVITED' or (subject is null and accepted_at is null)),
    constraint staff_member_revoked_ck check (
        (status = 'REVOKED') = (revoked_at is not null)
        and (revoked_at is null) = (revoked_by is null)),
    -- Aceptar garda a persoa e as obrigas á vez.
    constraint staff_member_accepted_ck check (
        (accepted_at is null) = (subject is null)
        and (accepted_at is null) = (obligations_version is null)),
    constraint staff_member_accepted_after_ck check (accepted_at is null or accepted_at >= invited_at),
    -- O acceso anterior só existe se hai un último, e nunca é posterior a el.
    constraint staff_member_sign_in_ck check (
        previous_sign_in_at is null
        or (last_sign_in_at is not null and previous_sign_in_at <= last_sign_in_at)),
    constraint staff_member_version_ck check (version >= 0)
);

-- Unha pertenza non revogada por concello e correo (volver invitar a alguén revogado crea outra
-- fila e o historial queda) e unha activa por concello e persoa.
create unique index staff_member_email_uk on staff_member (municipality_id, email)
    where status <> 'REVOKED';
create unique index staff_member_subject_uk on staff_member (municipality_id, subject)
    where status = 'ACTIVE';
-- Roles efectivos en cada petición (§1.2.2) e nomes no rexistro de actividade (tamén revogadas).
create index staff_member_subject_idx on staff_member (municipality_id, subject);

alter table staff_member enable row level security;

create policy staff_member_tenant on staff_member
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

do $$
begin
  if exists (select 1 from pg_roles where rolname = '${approle}') and '${approle}' <> current_user then
    execute format('revoke delete, truncate on staff_member from %I', '${approle}');
  end if;
end
$$;
