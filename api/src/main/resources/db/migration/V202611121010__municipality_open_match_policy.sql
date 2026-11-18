-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- «Buscar xente para xogar» por concello, apagado por defecto (ADR-0040, Concello › Xeral).
-- open_match_since nulo = apagado; open_match_cancel_hours: «Cancelar se non hai mínimo», horas
-- antes do inicio; open_match_policy_changed_at: o último cambio do interruptor ou das horas (a
-- tarefa do prazo só mira as partidas cuxo prazo cae despois). A táboa dos concellos non leva RLS
-- (TenancyAllowlist).

alter table municipality
    add column open_match_since timestamptz,
    add column open_match_cancel_hours int not null default 12,
    add column open_match_policy_changed_at timestamptz;

alter table municipality
    add constraint municipality_open_match_hours_ck check (open_match_cancel_hours between 1 and 72);
