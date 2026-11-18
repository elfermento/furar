-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- O quinto correo das reservas (ADR-0032, punto 10): o aviso de cada falta.

alter table notification_delivery drop constraint notification_delivery_kind_ck;

alter table notification_delivery add constraint notification_delivery_kind_ck
    check (kind in ('CONFIRMED', 'CHANGED', 'CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY',
                    'STAFF_INVITATION', 'NO_SHOW'));
