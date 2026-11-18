-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Aviso para instalar Furar coma unha app no móbil, na portada do concello. Aceso por defecto,
-- tamén nos concellos que xa existen; a xestión apágao en Concello › Xeral. A táboa dos concellos
-- non leva RLS (TenancyAllowlist).

alter table municipality
    add column install_hint boolean not null default true;
