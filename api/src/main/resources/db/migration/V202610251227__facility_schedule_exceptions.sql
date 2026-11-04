-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Horario da instalación, segunda parte (D1-15b; especificación, bloque 2; plan do dominio 1 §2.1):
-- excepcións. Unha por instalación e día, pechado ou aberto cun só tramo (P9) en minutos desde a
-- medianoite (ADR-0022, punto 8), cun motivo opcional. Mandan sobre o festivo e a semana da
-- tempada. Os festivos locais van aquí (P10).
--
-- Táboa do concello (ADR-0006): municipality_id con FK, unique (municipality_id, id), FK composta
-- con municipality_id nos dous lados e RLS coa política única.
create table schedule_exception (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    facility_id     uuid not null,
    date            date not null,
    open            boolean not null,
    start_minute    int,
    end_minute      int,
    reason          text,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    version         bigint not null,
    constraint schedule_exception_tenant_uk unique (municipality_id, id),
    constraint schedule_exception_facility_fk foreign key (municipality_id, facility_id)
        references facility (municipality_id, id),
    -- Unha excepción por instalación e data (o servizo xa o comproba baixo o bloqueo da
    -- instalación para dicir cal a ten; isto é a garantía) → 409 facility.exception-date-taken.
    constraint schedule_exception_date_uk unique (municipality_id, facility_id, date),
    -- Aberto ⇔ hai tramo; pechado sen tramo.
    constraint schedule_exception_open_ck check (
        open = (start_minute is not null) and (start_minute is null) = (end_minute is null)),
    constraint schedule_exception_interval_ck check (
        start_minute is null or (
            start_minute >= 0 and start_minute < end_minute and end_minute <= 1440
            and start_minute % 15 = 0 and end_minute % 15 = 0)),
    constraint schedule_exception_reason_ck check (
        reason is null or (length(reason) between 1 and 200 and reason = btrim(reason)))
);

alter table schedule_exception enable row level security;

create policy schedule_exception_tenant on schedule_exception
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
