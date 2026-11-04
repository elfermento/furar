-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Datos da instalación (D1-11b; plan do dominio 1 §2.1): deportes (catálogo de Furar e os que crea
-- o concello con «+ crear»), deportes de cada instalación e servizos e accesibilidade. As columnas
-- da tarxeta Datos e da descrición xa están en facility (V202610251013); aquí só levan os límites.

-- 1. Límites das columnas da tarxeta Datos -----------------------------------------------------
alter table facility
    add constraint facility_ridega_code_ck check (length(ridega_code) between 1 and 30),
    add constraint facility_phone_ck check (phone ~ '^\+?[0-9 ]{6,20}$'),
    add constraint facility_email_ck check (length(email) between 3 and 254 and email like '%_@_%'),
    add constraint facility_description_texts_ck
        check (description is null or (description ? 'gl' and description ? 'es'));

-- 2. Catálogo de deportes (plataforma, ADR-0006) -------------------------------------------------
-- Igual para todos os concellos e sen municipality_id (TenancyAllowlist). A API só o le: cámbiase
-- por migración, revisada nun PR. Os textos son os da especificación (gl e es).
create table sport_catalog (
    code  text primary key,
    label jsonb not null,
    constraint sport_catalog_code_ck check (code ~ '^[a-z][a-z0-9_]{1,39}$'),
    constraint sport_catalog_label_ck check (label ? 'gl' and label ? 'es')
);

insert into sport_catalog (code, label) values
    ('athletics',         '{"gl": "Atletismo",         "es": "Atletismo"}'),
    ('badminton',         '{"gl": "Bádminton",         "es": "Bádminton"}'),
    ('basketball',        '{"gl": "Baloncesto",        "es": "Baloncesto"}'),
    ('boxing',            '{"gl": "Boxeo",             "es": "Boxeo"}'),
    ('canoeing',          '{"gl": "Piragüismo",        "es": "Piragüismo"}'),
    ('climbing',          '{"gl": "Escalada",          "es": "Escalada"}'),
    ('cycling',           '{"gl": "Ciclismo",          "es": "Ciclismo"}'),
    ('dance',             '{"gl": "Baile",             "es": "Baile"}'),
    ('fitness',           '{"gl": "Fitness",           "es": "Fitness"}'),
    ('football',          '{"gl": "Fútbol",            "es": "Fútbol"}'),
    ('football_7',        '{"gl": "Fútbol 7",          "es": "Fútbol 7"}'),
    ('futsal',            '{"gl": "Fútbol sala",       "es": "Fútbol sala"}'),
    ('fronton',           '{"gl": "Frontón",           "es": "Frontón"}'),
    ('gymnastics',        '{"gl": "Ximnasia",          "es": "Gimnasia"}'),
    ('handball',          '{"gl": "Balonmán",          "es": "Balonmano"}'),
    ('field_hockey',      '{"gl": "Hóckey herba",      "es": "Hockey hierba"}'),
    ('roller_hockey',     '{"gl": "Hóckey patíns",     "es": "Hockey patines"}'),
    ('judo',              '{"gl": "Judo",              "es": "Judo"}'),
    ('karate',            '{"gl": "Karate",            "es": "Kárate"}'),
    ('martial_arts',      '{"gl": "Artes marciais",    "es": "Artes marciales"}'),
    ('padel',             '{"gl": "Pádel",             "es": "Pádel"}'),
    ('pilates',           '{"gl": "Pilates",           "es": "Pilates"}'),
    ('rowing',            '{"gl": "Remo",              "es": "Remo"}'),
    ('rugby',             '{"gl": "Rugby",             "es": "Rugby"}'),
    ('skating',           '{"gl": "Patinaxe",          "es": "Patinaje"}'),
    ('squash',            '{"gl": "Squash",            "es": "Squash"}'),
    ('swimming',          '{"gl": "Natación",          "es": "Natación"}'),
    ('table_tennis',      '{"gl": "Tenis de mesa",     "es": "Tenis de mesa"}'),
    ('tennis',            '{"gl": "Tenis",             "es": "Tenis"}'),
    ('volleyball',        '{"gl": "Voleibol",          "es": "Voleibol"}'),
    ('beach_volleyball',  '{"gl": "Voleibol praia",    "es": "Vóley playa"}'),
    ('water_polo',        '{"gl": "Waterpolo",         "es": "Waterpolo"}'),
    ('wrestling',         '{"gl": "Loita",             "es": "Lucha"}'),
    ('yoga',              '{"gl": "Ioga",              "es": "Yoga"}');

-- Só lectura para a API (como o historial de Flyway): o rol vén do placeholder approle.
do $$
begin
  if exists (select 1 from pg_roles where rolname = '${approle}') and '${approle}' <> current_user then
    execute format('revoke insert, update, delete, truncate on sport_catalog from %I', '${approle}');
  end if;
end
$$;

-- 3. Deportes do concello («+ crear», P26: nome en gl e es) -------------------------------------
create table custom_sport (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    label           jsonb not null,
    created_at      timestamptz not null,
    constraint custom_sport_tenant_uk unique (municipality_id, id),
    constraint custom_sport_label_ck check (label ? 'gl' and label ? 'es')
);

-- Nome único no concello sen maiúsculas (índice: unha constraint non admite expresións).
create unique index custom_sport_label_uk on custom_sport (municipality_id, lower(label ->> 'gl'));

-- 4. Deportes de cada instalación ----------------------------------------------------------------
-- Un do catálogo ou un do concello, nunca os dous, na orde da tarxeta Datos (position). Repetido →
-- índices parciais (409).
create table facility_sport (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    facility_id     uuid not null,
    sport_code      text,
    custom_sport_id uuid,
    position        int not null,
    constraint facility_sport_tenant_uk unique (municipality_id, id),
    constraint facility_sport_catalog_fk foreign key (sport_code) references sport_catalog (code),
    constraint facility_sport_facility_fk foreign key (municipality_id, facility_id)
        references facility (municipality_id, id) on delete cascade,
    constraint facility_sport_custom_fk foreign key (municipality_id, custom_sport_id)
        references custom_sport (municipality_id, id),
    constraint facility_sport_one_ck check ((sport_code is null) <> (custom_sport_id is null)),
    constraint facility_sport_position_ck check (position >= 0)
);

create index facility_sport_facility_idx on facility_sport (municipality_id, facility_id);
create unique index facility_sport_code_uk on facility_sport (facility_id, sport_code)
    where sport_code is not null;
create unique index facility_sport_custom_uk on facility_sport (facility_id, custom_sport_id)
    where custom_sport_id is not null;

-- 5. Servizos e accesibilidade (catálogo fixo: enum + check; textos e iconas na web, ADR-0006) ----
create table facility_amenity (
    municipality_id uuid not null references municipality (id),
    facility_id     uuid not null,
    code            text not null,
    primary key (facility_id, code),
    constraint facility_amenity_facility_fk foreign key (municipality_id, facility_id)
        references facility (municipality_id, id) on delete cascade,
    constraint facility_amenity_code_ck check (code in (
        'CHANGING_ROOMS', 'SHOWERS', 'COVERED_STANDS', 'CAFE', 'PARKING', 'LIGHTING',
        'STEP_FREE_ACCESS', 'ACCESSIBLE_TOILET', 'ACCESSIBLE_CHANGING_ROOM', 'HEARING_LOOP'))
);

create index facility_amenity_facility_idx on facility_amenity (municipality_id, facility_id);

-- 6. Row Level Security, como V202610231100 ------------------------------------------------------
alter table custom_sport enable row level security;
create policy custom_sport_tenant on custom_sport
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

alter table facility_sport enable row level security;
create policy facility_sport_tenant on facility_sport
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

alter table facility_amenity enable row level security;
create policy facility_amenity_tenant on facility_amenity
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
