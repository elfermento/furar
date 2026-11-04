-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Instalación (ADR-0022, plan do dominio 1 §2.1): o que se reserva. Táboa do concello (ADR-0006:
-- municipality_id con FK, unique (municipality_id, id), RLS coa política única).
--
-- Créase enteira, coas columnas da tarxeta Datos e da descrición (D1-11b): enderezo e coordenadas
-- son obrigatorios desde a alta; o resto pode ir baleiro. O borrador non se garda: calcúlase
-- (ADR-0022, punto 3). O número da vía pode faltar («s/n»).

create table facility (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    name            text not null,
    status          text not null default 'ACTIVE',
    type            text,
    description     jsonb,
    ridega_code     text,
    phone           text,
    email           text,
    online_booking  boolean not null default true,
    show_free_slots boolean not null default false,
    covered         boolean,
    street          text not null,
    street_number   text,
    postal_code     text not null,
    locality        text not null,
    ine_code        text not null,
    latitude        numeric(8, 6) not null,
    longitude       numeric(9, 6) not null,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    version         bigint not null,
    constraint facility_tenant_uk unique (municipality_id, id),
    constraint facility_name_ck check (length(name) between 1 and 100 and name = btrim(name)),
    constraint facility_status_ck check (status in ('ACTIVE', 'TEMPORARILY_CLOSED', 'WITHDRAWN')),
    constraint facility_type_ck
        check (type in ('FIELD', 'SPORTS_HALL', 'PADEL_COURT', 'POOL', 'OUTDOOR_COURT', 'OTHER')),
    constraint facility_description_ck check (description is null or jsonb_typeof(description) = 'object'),
    constraint facility_booking_mode_ck check (not (online_booking and show_free_slots)),
    constraint facility_street_ck check (length(street) between 1 and 200),
    constraint facility_street_number_ck check (length(street_number) between 1 and 20),
    constraint facility_postal_code_ck check (postal_code ~ '^[0-9]{5}$'),
    constraint facility_locality_ck check (length(locality) between 1 and 100),
    constraint facility_ine_code_ck check (ine_code ~ '^[0-9]{5}$'),
    constraint facility_latitude_ck check (latitude between -90 and 90),
    constraint facility_longitude_ck check (longitude between -180 and 180)
);

-- Nome único no concello sen distinguir maiúsculas (P29). Índice e non constraint: unha constraint
-- non admite expresións (ADR-0022, punto 9). A orde do listado (collate ICU) non usa índice.
create unique index facility_name_uk on facility (municipality_id, lower(name));

alter table facility enable row level security;

create policy facility_tenant on facility
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
