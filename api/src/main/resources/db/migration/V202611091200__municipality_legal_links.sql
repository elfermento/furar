-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Tres datos legais novos, opcionais (ADR-0017 punto 8, revisión do 2026-11-09): o teléfono xeral
-- da entidade (aviso legal, privacidade e canle complementaria da declaración de accesibilidade,
-- RD 1112/2018 art. 11), a ligazón ao rexistro de actividades de tratamento (LOPDGDD art. 31.2) e
-- a ligazón á normativa de uso das instalacións (condicións de uso do aviso legal). Sen valor, o
-- parágrafo que os usa non sae. O dominio valida o formato; a base, o mesmo ao gardar.

alter table municipality
    add column general_phone           text,
    add column processing_register_url text,
    add column use_regulation_url      text,
    add constraint municipality_legal_general_phone_ck check (general_phone ~ '^\+?[0-9 ]{9,15}$'),
    add constraint municipality_legal_links_ck check (
        processing_register_url ~ '^https://' and length(processing_register_url) <= 500
        and use_regulation_url ~ '^https://' and length(use_regulation_url) <= 500);
