-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Fotos da instalación (D1-13; plan do dominio 1 §1.2.9 e §2.1). O metadato (orde, portada, texto
-- alternativo en gl e es, medidas) vai en facility_photo; os bytes, nunha táboa aparte para que
-- ler a ficha non os traia. Cada foto garda dúas variantes xa reencodadas e sen EXIF: LARGE (1600
-- px no lado longo) e THUMB (480 px). Máximo 12 por instalación: compróbao o dominio baixo a
-- versión da instalación (If-Match).

create table facility_photo (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    facility_id     uuid not null,
    position        int not null,
    cover           boolean not null default false,
    alt             jsonb not null,
    width           int not null,
    height          int not null,
    content_type    text not null,
    created_at      timestamptz not null,
    constraint facility_photo_tenant_uk unique (municipality_id, id),
    constraint facility_photo_facility_fk foreign key (municipality_id, facility_id)
        references facility (municipality_id, id) on delete cascade,
    constraint facility_photo_position_ck check (position between 0 and 11),
    constraint facility_photo_alt_ck check (alt ? 'gl' and alt ? 'es'),
    constraint facility_photo_size_ck check (width between 1 and 1600 and height between 1 and 1600),
    constraint facility_photo_content_type_ck check (content_type in ('image/jpeg', 'image/png'))
);

create index facility_photo_facility_idx on facility_photo (municipality_id, facility_id, position);

-- Unha soa portada por instalación. O cambio de portada quita primeiro a vella e fai flush antes de
-- poñer a nova (Hibernate non ordena os UPDATE; ADR-0022, punto 11).
create unique index facility_photo_cover_uk on facility_photo (facility_id) where cover;

create table facility_photo_content (
    municipality_id uuid not null references municipality (id),
    photo_id        uuid not null,
    variant         text not null,
    bytes           bytea not null,
    primary key (photo_id, variant),
    constraint facility_photo_content_photo_fk foreign key (municipality_id, photo_id)
        references facility_photo (municipality_id, id) on delete cascade,
    constraint facility_photo_content_variant_ck check (variant in ('LARGE', 'THUMB')),
    constraint facility_photo_content_bytes_ck check (length(bytes) between 1 and 8388608)
);

-- Os bytes xa van comprimidos (JPEG): fóra de liña e sen intentar comprimilos outra vez.
alter table facility_photo_content alter column bytes set storage external;

create index facility_photo_content_tenant_idx on facility_photo_content (municipality_id, photo_id);

-- Row Level Security, como V202610231100 --------------------------------------------------------
alter table facility_photo enable row level security;
create policy facility_photo_tenant on facility_photo
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

alter table facility_photo_content enable row level security;
create policy facility_photo_content_tenant on facility_photo_content
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
