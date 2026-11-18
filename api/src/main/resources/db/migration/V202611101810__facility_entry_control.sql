-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Control de entrada (ADR-0032, punto 2): nulo = sen control de entrada. O interruptor garda o
-- momento en que se acende: só as reservas que empezan despois poden pasar soas a falta.

alter table facility add column entry_control_since timestamptz;
