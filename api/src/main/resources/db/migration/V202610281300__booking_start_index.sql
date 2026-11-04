-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Indicadores (ADR-0026; plan do dominio 3, §2.1): os agregados len as reservas do concello que
-- empezan nun período, sen filtrar por instalación nin por persoa. booking_facility_idx e
-- booking_holder_idx levan outra columna diante do inicio; este índice sérvelles a todas as
-- lecturas do panel.

create index booking_start_idx on booking (municipality_id, lower(period));
