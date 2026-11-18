-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Os datos legais do concello deixan de levar o código INE (decisión do 2026-11-10): non ten uso.
-- A busca de enderezos filtra polo nome do concello.

alter table municipality
    drop constraint municipality_ine_code_ck,
    drop column ine_code;
