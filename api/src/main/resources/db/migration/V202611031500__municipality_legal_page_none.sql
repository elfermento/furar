-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Cuarto modo das páxinas legais (ADR-0017 punto 8, revisión do 2026-11-03): NONE = sen páxina.
-- Non hai páxina pública, o pé non a liga e os correos non levan a ligazón. A obriga de publicala é
-- do concello: a xestión avisa, pero deixa gardar. O enderezo e o texto propio quedan gardados.

alter table municipality
    drop constraint municipality_legal_mode_ck,
    add constraint municipality_legal_mode_ck check (
        privacy_mode in ('FURAR', 'TEXT', 'URL', 'NONE')
        and accessibility_mode in ('FURAR', 'TEXT', 'URL', 'NONE')
        and legal_notice_mode in ('FURAR', 'TEXT', 'URL', 'NONE'));
