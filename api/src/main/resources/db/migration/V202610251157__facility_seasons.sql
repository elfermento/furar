-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Horario da instalación, primeira parte (D1-15a; especificación, bloque 2; plan do dominio 1 §2.1):
-- tempadas, a semana de cada tempada (días 1–7 de luns a domingo e 8 = festivos) e os festivos de
-- Galicia. Excepcións e bloqueos van noutras migracións (D1-15b, D1-16).
--
-- Táboas do concello (ADR-0006): municipality_id con FK, unique (municipality_id, id), FK compostas
-- con municipality_id nos dous lados e RLS coa política única. public_holiday é catálogo de
-- plataforma (TenancyAllowlist) e a API só o le.

-- 1. Tempadas ------------------------------------------------------------------------------------
create table season (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    facility_id     uuid not null,
    name            text not null,
    start_date      date not null,
    end_date        date not null,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    version         bigint not null,
    constraint season_tenant_uk unique (municipality_id, id),
    constraint season_facility_uk unique (municipality_id, facility_id, id),
    constraint season_facility_fk foreign key (municipality_id, facility_id)
        references facility (municipality_id, id),
    constraint season_name_ck check (length(name) between 1 and 100 and name = btrim(name)),
    constraint season_dates_ck check (end_date >= start_date),
    -- As tempadas dunha instalación non se solapan (especificación, bloque 2). O servizo xa o
    -- comproba baixo o bloqueo da instalación para dicir con cal choca; isto é a garantía.
    constraint season_overlap_ex
        exclude using gist (facility_id with =, (daterange(start_date, end_date, '[]')) with &&)
);

create index season_facility_idx on season (municipality_id, facility_id);

-- 2. Semana de cada tempada ----------------------------------------------------------------------
-- Un tramo por fila, en minutos desde a medianoite (ADR-0022, punto 8): 0 ≤ inicio < fin ≤ 1440
-- (1440 = 24:00, P12), múltiplos de 15 e sen solapes no mesmo día. A semana substitúese enteira en
-- cada gardado: o adaptador borra e fai flush antes de inserir (Hibernate insire antes de borrar,
-- §1.2.15 do plan). A fila 8 (festivos) énchese coa do domingo ao crear ou copiar a tempada.
create table season_hours (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    season_id       uuid not null,
    day             int not null,
    start_minute    int not null,
    end_minute      int not null,
    constraint season_hours_tenant_uk unique (municipality_id, id),
    constraint season_hours_season_fk foreign key (municipality_id, season_id)
        references season (municipality_id, id) on delete cascade,
    constraint season_hours_day_ck check (day between 1 and 8),
    constraint season_hours_interval_ck check (
        start_minute >= 0 and start_minute < end_minute and end_minute <= 1440
        and start_minute % 15 = 0 and end_minute % 15 = 0),
    constraint season_hours_overlap_ex
        exclude using gist (season_id with =, day with =, (int4range(start_minute, end_minute)) with &&)
);

create index season_hours_season_idx on season_hours (municipality_id, season_id);

alter table season enable row level security;
alter table season_hours enable row level security;

create policy season_tenant on season
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

create policy season_hours_tenant on season_hours
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);

-- 3. Festivos de Galicia (plataforma, ADR-0006) --------------------------------------------------
-- Unha soa lista efectiva para Galicia (plan §1.2.12): os nacionais que se gozan en Galicia e os
-- autonómicos, tal como os publican o BOE (columna Galicia) e o DOG. Os locais non van aquí: son
-- excepcións de cada instalación (P10). Nada inventado: cada fila leva a súa fonte. Un ano só entra
-- cando está publicado (P11); os cambios, por migración nova revisada nun PR.
create table public_holiday (
    date   date primary key,
    name   jsonb not null,
    source text not null,
    constraint public_holiday_name_ck check (name ? 'gl' and name ? 'es'),
    constraint public_holiday_source_ck check (length(source) between 1 and 300)
);

-- 2026: Resolución do 17 de outubro de 2025 da Dirección Xeral de Traballo (BOE núm. 259, do 28 de
-- outubro de 2025, BOE-A-2025-21667), columna Galicia, e Decreto 46/2025, do 9 de xuño, polo que se
-- determinan as festas da Comunidade Autónoma de Galicia do calendario laboral para o ano 2026 (DOG
-- núm. 117, do 20 de xuño de 2025). O 1 de novembro e o 6 de decembro caen en domingo: Galicia
-- substitúeos polo 19 de marzo e o 24 de xuño.
insert into public_holiday (date, name, source) values
    ('2026-01-01', '{"gl": "Aninovo", "es": "Año Nuevo"}',
        'BOE-A-2025-21667; Decreto 46/2025 (DOG núm. 117, 20/06/2025)'),
    ('2026-01-06', '{"gl": "Epifanía do Señor", "es": "Epifanía del Señor"}',
        'BOE-A-2025-21667; Decreto 46/2025 (DOG núm. 117, 20/06/2025)'),
    ('2026-03-19', '{"gl": "San Xosé", "es": "San José"}',
        'BOE-A-2025-21667; Decreto 46/2025 (DOG núm. 117, 20/06/2025)'),
    ('2026-04-02', '{"gl": "Xoves Santo", "es": "Jueves Santo"}',
        'BOE-A-2025-21667; Decreto 46/2025 (DOG núm. 117, 20/06/2025)'),
    ('2026-04-03', '{"gl": "Venres Santo", "es": "Viernes Santo"}',
        'BOE-A-2025-21667; Decreto 46/2025 (DOG núm. 117, 20/06/2025)'),
    ('2026-05-01', '{"gl": "Festa do Traballo", "es": "Fiesta del Trabajo"}',
        'BOE-A-2025-21667; Decreto 46/2025 (DOG núm. 117, 20/06/2025)'),
    ('2026-06-24', '{"gl": "San Xoán", "es": "San Juan"}',
        'BOE-A-2025-21667; Decreto 46/2025 (DOG núm. 117, 20/06/2025)'),
    ('2026-07-25', '{"gl": "Día Nacional de Galicia", "es": "Santiago Apóstol / Día Nacional de Galicia"}',
        'BOE-A-2025-21667; Decreto 46/2025 (DOG núm. 117, 20/06/2025)'),
    ('2026-08-15', '{"gl": "Asunción da Virxe", "es": "Asunción de la Virgen"}',
        'BOE-A-2025-21667; Decreto 46/2025 (DOG núm. 117, 20/06/2025)'),
    ('2026-10-12', '{"gl": "Festa Nacional de España", "es": "Fiesta Nacional de España"}',
        'BOE-A-2025-21667; Decreto 46/2025 (DOG núm. 117, 20/06/2025)'),
    ('2026-12-08', '{"gl": "Inmaculada Concepción", "es": "Inmaculada Concepción"}',
        'BOE-A-2025-21667; Decreto 46/2025 (DOG núm. 117, 20/06/2025)'),
    ('2026-12-25', '{"gl": "Natividade do Señor", "es": "Natividad del Señor"}',
        'BOE-A-2025-21667; Decreto 46/2025 (DOG núm. 117, 20/06/2025)');

-- 2027: Resolución do 8 de outubro de 2026 da Dirección Xeral de Traballo (BOE núm. 254, do 13 de
-- outubro de 2026, BOE-A-2026-21254), columna Galicia, e Decreto 68/2026, do 15 de xuño, polo que
-- se determinan as festas da Comunidade Autónoma de Galicia do calendario laboral para o ano 2027
-- (DOG do 2 de xullo de 2026). O 25 de xullo e o 15 de agosto caen en domingo: Galicia opta polo 19
-- de marzo e substitúe o 15 de agosto polo 17 de maio, Día das Letras Galegas.
insert into public_holiday (date, name, source) values
    ('2027-01-01', '{"gl": "Aninovo", "es": "Año Nuevo"}',
        'BOE-A-2026-21254; Decreto 68/2026 (DOG 02/07/2026)'),
    ('2027-01-06', '{"gl": "Epifanía do Señor", "es": "Epifanía del Señor"}',
        'BOE-A-2026-21254; Decreto 68/2026 (DOG 02/07/2026)'),
    ('2027-03-19', '{"gl": "San Xosé", "es": "San José"}',
        'BOE-A-2026-21254; Decreto 68/2026 (DOG 02/07/2026)'),
    ('2027-03-25', '{"gl": "Xoves Santo", "es": "Jueves Santo"}',
        'BOE-A-2026-21254; Decreto 68/2026 (DOG 02/07/2026)'),
    ('2027-03-26', '{"gl": "Venres Santo", "es": "Viernes Santo"}',
        'BOE-A-2026-21254; Decreto 68/2026 (DOG 02/07/2026)'),
    ('2027-05-01', '{"gl": "Festa do Traballo", "es": "Fiesta del Trabajo"}',
        'BOE-A-2026-21254; Decreto 68/2026 (DOG 02/07/2026)'),
    ('2027-05-17', '{"gl": "Día das Letras Galegas", "es": "Día de las Letras Gallegas"}',
        'BOE-A-2026-21254; Decreto 68/2026 (DOG 02/07/2026)'),
    ('2027-10-12', '{"gl": "Festa Nacional de España", "es": "Fiesta Nacional de España"}',
        'BOE-A-2026-21254; Decreto 68/2026 (DOG 02/07/2026)'),
    ('2027-11-01', '{"gl": "Día de Todos os Santos", "es": "Todos los Santos"}',
        'BOE-A-2026-21254; Decreto 68/2026 (DOG 02/07/2026)'),
    ('2027-12-06', '{"gl": "Día da Constitución Española", "es": "Día de la Constitución Española"}',
        'BOE-A-2026-21254; Decreto 68/2026 (DOG 02/07/2026)'),
    ('2027-12-08', '{"gl": "Inmaculada Concepción", "es": "Inmaculada Concepción"}',
        'BOE-A-2026-21254; Decreto 68/2026 (DOG 02/07/2026)'),
    ('2027-12-25', '{"gl": "Natividade do Señor", "es": "Natividad del Señor"}',
        'BOE-A-2026-21254; Decreto 68/2026 (DOG 02/07/2026)');

-- Só lectura para a API (como sport_catalog): o rol vén do placeholder approle.
do $$
begin
  if exists (select 1 from pg_roles where rolname = '${approle}') and '${approle}' <> current_user then
    execute format('revoke insert, update, delete, truncate on public_holiday from %I', '${approle}');
  end if;
end
$$;
