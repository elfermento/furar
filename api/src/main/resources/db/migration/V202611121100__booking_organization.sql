-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Entidades do concello (clubs, escolas, asociacións; ADR-0039, punto 4) e as súas persoas
-- responsables. O correo é obrigatorio mentres está dada de alta; unha entidade con historial non
-- se borra: arquívase (archived_at) e só queda o nome, sen contacto nin responsables. O nome non se
-- repite entre as dadas de alta do concello.
--
-- booking_organization_manager: o correo dunha persoa responsable (EmailKey.of en email_key), quen
-- o engadiu e cando. Quen entra cun correo verificado que está aquí ve as sesións da entidade,
-- cancélaas, reserva no seu nome e solicita tempadas.

create table booking_organization (
    id              uuid        primary key,
    municipality_id uuid        not null references municipality (id),
    name            text        not null,
    contact_name    text,
    email           text,
    phone           text,
    archived_at     timestamptz,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    version         bigint      not null,
    constraint booking_organization_tenant_uk unique (municipality_id, id),
    constraint booking_organization_name_ck check (length(name) between 1 and 120),
    constraint booking_organization_contact_ck check (length(contact_name) between 1 and 120),
    constraint booking_organization_email_format_ck
        check (length(email) between 3 and 254 and email like '%_@_%'),
    constraint booking_organization_phone_ck
        check (length(phone) between 3 and 30 and phone ~ '^[0-9+() .-]+$'),
    constraint booking_organization_email_ck check (archived_at is not null or email is not null),
    constraint booking_organization_archived_ck check (
        archived_at is null or (email is null and contact_name is null and phone is null))
);

create unique index booking_organization_name_uk
    on booking_organization (municipality_id, lower(name)) where archived_at is null;

alter table booking_organization enable row level security;

create policy booking_organization_tenant on booking_organization
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

create table booking_organization_manager (
    municipality_id uuid        not null references municipality (id),
    organization_id uuid        not null,
    email_key       text        not null,
    email           text        not null,
    added_at        timestamptz not null,
    added_by        text        not null,
    constraint booking_organization_manager_pk
        primary key (municipality_id, organization_id, email_key),
    constraint booking_organization_manager_organization_fk
        foreign key (municipality_id, organization_id)
        references booking_organization (municipality_id, id) on delete cascade,
    constraint booking_organization_manager_key_ck check (length(email_key) between 3 and 254),
    constraint booking_organization_manager_email_ck check (length(email) between 3 and 254),
    constraint booking_organization_manager_added_by_ck check (length(added_by) between 1 and 255)
);

-- «As miñas entidades»: as entidades dun correo.
create index booking_organization_manager_email_idx
    on booking_organization_manager (municipality_id, email_key);

alter table booking_organization_manager enable row level security;

create policy booking_organization_manager_tenant on booking_organization_manager
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
