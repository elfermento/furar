-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Pago e devolución (D2-11, revisión do 2026-11-03): ALL = os textos do concello valen para todas
-- as instalacións; FACILITY = cada instalación leva os seus, na súa ficha, sen herdar os do
-- concello (sen texto, o de Furar). Os textos do concello quedan gardados ao cambiar de modo.
-- Columna da táboa dos concellos, sen RLS (TenancyAllowlist).

alter table municipality
    add column payment_notes_scope text not null default 'ALL',
    add constraint municipality_payment_notes_scope_ck check (payment_notes_scope in ('ALL', 'FACILITY'));
