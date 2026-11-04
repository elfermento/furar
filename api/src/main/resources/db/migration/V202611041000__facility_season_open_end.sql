-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Tempada sen data de fin (decisión do 2026-11-04 sobre a maqueta 54): «Ata» é opcional e unha
-- tempada sen fin cobre todos os días desde o seu inicio. «Todo o ano» do asistente nace así, para
-- que unha instalación nova non deixe de reservarse o 1 de xaneiro.
--
-- season_dates_ck segue valendo (cun nulo non se comproba) e a exclusión season_overlap_ex tamén:
-- daterange(start_date, null, '[]') non ten límite superior, así que choca con calquera tempada
-- posterior. A táboa xa ten RLS e a política season_tenant.

alter table season alter column end_date drop not null;
