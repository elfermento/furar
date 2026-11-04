-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Horario da instalación, terceira parte (D1-16; especificación, bloque 2; plan do dominio 1 §2.1):
-- bloqueos. Un bloqueo é un día cun tramo de reloxo de parede en Europe/Madrid (P13: todo o día =
-- 0:00–24:00) e un motivo opcional. O servidor converte o tramo aos instantes reais (starts_at,
-- ends_at) xa co cambio de hora aplicado, e period é o mesmo como tstzrange para as consultas do
-- dominio 2. Os bloqueos non van na exclusión da ocupación (ADR-0022, punto 5): compróbaos booking
-- baixo o bloqueo da instalación.
--
-- Táboa do concello (ADR-0006): municipality_id con FK, unique (municipality_id, id), FK composta
-- con municipality_id nos dous lados e RLS coa política única.
create table block (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    facility_id     uuid not null,
    date            date not null,
    start_minute    int not null,
    end_minute      int not null,
    starts_at       timestamptz not null,
    ends_at         timestamptz not null,
    -- Calculado polo servidor desde date e os minutos (en Europe/Madrid); a base só o garda xunto.
    period          tstzrange generated always as (tstzrange(starts_at, ends_at, '[)')) stored,
    reason          text,
    -- Quen o creou (claim sub da conta de xestión), como no rexistro de actividade.
    created_by      text not null,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    version         bigint not null,
    constraint block_tenant_uk unique (municipality_id, id),
    constraint block_facility_fk foreign key (municipality_id, facility_id)
        references facility (municipality_id, id),
    constraint block_interval_ck check (
        start_minute >= 0 and start_minute < end_minute and end_minute <= 1440
        and start_minute % 15 = 0 and end_minute % 15 = 0),
    -- Un tramo que o cambio de hora deixa baleiro (29 de marzo, 2:00–2:30) non é un bloqueo.
    constraint block_period_ck check (starts_at < ends_at),
    constraint block_reason_ck check (
        reason is null or (length(reason) between 1 and 200 and reason = btrim(reason))),
    constraint block_created_by_ck check (length(created_by) between 1 and 255),
    -- Dous bloqueos da instalación non se pisan (P7). O servizo xa o comproba baixo o bloqueo da
    -- instalación para dicir con cal choca; isto é a garantía → 409 facility.block-overlap.
    constraint block_overlap_ex
        exclude using gist (facility_id with =, period with &&)
);

create index block_facility_idx on block (municipality_id, facility_id, date);

alter table block enable row level security;

create policy block_tenant on block
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
