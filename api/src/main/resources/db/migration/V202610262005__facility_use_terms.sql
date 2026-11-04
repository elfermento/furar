-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Condicións do uso e normas da instalación (plan do dominio 2, §2.1, D2-02; ADR-0025, punto 1).
-- Os días da semana son unha máscara (luns = 1 … domingo = 64; 127 = todos). O prezo é por franxa
-- (nas prazas, por persoa e franxa): só o de pago leva importe. As catro normas, nulas = non se
-- aplican; o límite por persoa leva número e período xuntos. O dominio comproba o mesmo e devolve
-- o erro por campo; a base é a última garda. As normas da instalación, texto libre gl/es como a
-- descrición. Sen RLS nova: as columnas van en táboas que xa a teñen.

alter table facility_use
    add column weekdays               smallint not null default 127,
    add column price_kind             text not null default 'NONE',
    add column price_cents            int,
    add column rule_min_advance_hours int,
    add column rule_max_advance_days  int,
    add column rule_max_slots         int,
    add column rule_cap_count         int,
    add column rule_cap_period        text,
    add column rule_cancel_hours      int,
    add constraint facility_use_weekdays_ck check (weekdays between 1 and 127),
    add constraint facility_use_price_kind_ck check (price_kind in ('NONE', 'FREE', 'PAID')),
    add constraint facility_use_price_cents_ck
        check ((price_kind = 'PAID') = (price_cents is not null)),
    add constraint facility_use_price_range_ck check (price_cents between 1 and 100000),
    add constraint facility_use_rule_min_advance_ck check (rule_min_advance_hours between 0 and 720),
    add constraint facility_use_rule_max_advance_ck check (rule_max_advance_days between 1 and 365),
    add constraint facility_use_rule_max_slots_ck check (rule_max_slots between 1 and 16),
    add constraint facility_use_rule_cap_count_ck check (rule_cap_count between 1 and 50),
    add constraint facility_use_rule_cap_period_ck
        check (rule_cap_period in ('DAY', 'WEEK', 'MONTH')),
    add constraint facility_use_rule_cap_ck
        check ((rule_cap_count is null) = (rule_cap_period is null)),
    add constraint facility_use_rule_cancel_ck check (rule_cancel_hours between 0 and 720);

alter table facility
    add column house_rules jsonb,
    add constraint facility_house_rules_ck check (
        house_rules is null
        or (jsonb_typeof(house_rules) = 'object' and house_rules ? 'gl' and house_rules ? 'es'));
