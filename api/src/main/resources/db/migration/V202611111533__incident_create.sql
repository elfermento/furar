-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Incidencias (ADR-0036): a veciñanza avisa dun problema nunha instalación (ou o persoal o anota) e
-- o concello cambia o estado ata resolvelo. Datos persoais: quen avisou (conta, nome, correo e
-- idioma copiados do token) e as fotos, que poden mostrar persoas; quedan ata que a xestión elimina
-- a incidencia ou unha foto (docs/datos-persoais.md). Sen FK a facility nin a
-- booking (outros módulos, fóra da lista pechada CrossModuleForeignKeys), coma notification_delivery:
-- o caso de uso compróbaas pola súa API pública. Fóra de furar-export, coma as reservas (ADR-0018).
--
--   * incident: o aviso, o seu estado, a nota interna e a mensaxe á persoa ao resolver.
--   * incident_photo: ata tres fotos por incidencia, xa reencodadas sen metadatos.
--   * incident_photo_content: os bytes de cada foto (grande e miniatura), á parte.

create table incident (
    id                 uuid primary key,
    municipality_id    uuid not null references municipality (id),
    facility_id        uuid not null,
    booking_id         uuid,
    type               text not null,
    description        text not null,
    status             text not null,
    origin             text not null,
    reporter_subject   text,
    reporter_name      text,
    reporter_email     text,
    reporter_language  text not null,
    internal_note      text,
    resolution_message text,
    resolved_at        timestamptz,
    photos_removed_at  timestamptz,
    anonymized_at      timestamptz,
    created_at         timestamptz not null,
    updated_at         timestamptz not null,
    version            bigint not null,
    constraint incident_tenant_uk unique (municipality_id, id),
    constraint incident_type_ck check (type in ('BREAKDOWN', 'CLEANING', 'SAFETY', 'OTHER')),
    constraint incident_description_ck check (length(description) between 1 and 500),
    constraint incident_status_ck check (status in ('NEW', 'IN_PROGRESS', 'RESOLVED')),
    constraint incident_origin_ck check (origin in ('RESIDENT', 'STAFF')),
    constraint incident_language_ck check (reporter_language in ('gl', 'es')),
    constraint incident_name_ck check (reporter_name is null or length(reporter_name) <= 200),
    constraint incident_email_ck check (reporter_email is null or length(reporter_email) <= 254),
    constraint incident_note_ck check (internal_note is null or length(internal_note) <= 1000),
    constraint incident_message_ck
        check (resolution_message is null or length(resolution_message) <= 500),
    -- Só unha resolta ten data de resolución.
    constraint incident_resolved_ck check ((status = 'RESOLVED') = (resolved_at is not null)),
    -- Unha anonimizada xa non ten persoa.
    constraint incident_anonymized_ck check (anonymized_at is null
        or (reporter_subject is null and reporter_name is null and reporter_email is null))
);

create index incident_municipality_idx on incident (municipality_id, created_at desc);
create index incident_status_idx on incident (municipality_id, status);
create index incident_reporter_idx on incident (municipality_id, reporter_subject, created_at)
    where reporter_subject is not null;
create index incident_resolved_idx on incident (municipality_id, resolved_at)
    where resolved_at is not null;

alter table incident enable row level security;

create policy incident_tenant on incident
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

create table incident_photo (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    incident_id     uuid not null,
    position        int not null,
    width           int not null,
    height          int not null,
    content_type    text not null,
    created_at      timestamptz not null,
    constraint incident_photo_tenant_uk unique (municipality_id, id),
    constraint incident_photo_incident_fk foreign key (municipality_id, incident_id)
        references incident (municipality_id, id) on delete cascade,
    constraint incident_photo_position_uk unique (municipality_id, incident_id, position),
    constraint incident_photo_position_ck check (position between 0 and 2),
    constraint incident_photo_size_ck check (width between 1 and 1600 and height between 1 and 1600),
    constraint incident_photo_content_type_ck check (content_type = 'image/jpeg')
);

alter table incident_photo enable row level security;

create policy incident_photo_tenant on incident_photo
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

create table incident_photo_content (
    municipality_id uuid not null references municipality (id),
    photo_id        uuid not null,
    variant         text not null,
    bytes           bytea not null,
    primary key (photo_id, variant),
    constraint incident_photo_content_photo_fk foreign key (municipality_id, photo_id)
        references incident_photo (municipality_id, id) on delete cascade,
    constraint incident_photo_content_variant_ck check (variant in ('LARGE', 'THUMB')),
    constraint incident_photo_content_bytes_ck check (length(bytes) between 1 and 8388608)
);

-- Os bytes xa van comprimidos (JPEG): fóra de liña e sen intentar comprimilos outra vez.
alter table incident_photo_content alter column bytes set storage external;

create index incident_photo_content_tenant_idx on incident_photo_content (municipality_id, photo_id);

alter table incident_photo_content enable row level security;

create policy incident_photo_content_tenant on incident_photo_content
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
