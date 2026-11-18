-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Datos abertos, por concello e acesos por defecto (ADR-0034, punto 2). open_data_since nulo =
-- apagado; a data é a da publicación (dct:issued da ficha de catálogo) e marca desde que día se
-- publica o motivo dos peches. Sen default permanente: a alta e a importación póñena co reloxo da
-- aplicación. A táboa dos concellos non leva RLS (TenancyAllowlist).

alter table municipality
    add column open_data_since timestamptz;

update municipality set open_data_since = now();
