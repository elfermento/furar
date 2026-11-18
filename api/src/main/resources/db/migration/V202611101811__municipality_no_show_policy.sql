-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Penalización das faltas, por concello e apagada por defecto (ADR-0032, puntos 2 e 7).
-- no_show_penalty_since nulo = apagada; só contan as faltas posteriores a acendela. A táboa dos
-- concellos non leva RLS (TenancyAllowlist).

alter table municipality
    add column no_show_penalty_since timestamptz,
    add column no_show_threshold int not null default 3,
    add column no_show_window_days int not null default 30,
    add column no_show_block_days int not null default 15;

alter table municipality
    add constraint municipality_no_show_threshold_ck check (no_show_threshold between 1 and 20),
    add constraint municipality_no_show_window_ck check (no_show_window_days between 1 and 365),
    add constraint municipality_no_show_block_ck check (no_show_block_days between 1 and 365);
