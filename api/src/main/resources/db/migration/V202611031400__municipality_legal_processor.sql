-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Encargado do tratamento de cada concello (RGPD art. 28; ADR-0017, revisión do 2026-11-03): quen
-- aloxa e opera o servizo, se non o fai o propio concello. Opcional, como o resto dos datos legais:
-- a páxina «Privacidade» xa non le datos do despregue (FURAR_PRIVACY_*), senón os do concello.
-- Columna da táboa dos concellos, sen RLS (TenancyAllowlist). O dominio valida; a base, o tamaño.

alter table municipality
    add column processor text,
    add constraint municipality_legal_processor_ck check (length(processor) <= 200);
