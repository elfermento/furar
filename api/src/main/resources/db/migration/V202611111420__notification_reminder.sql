-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Dous correos novos: o recordatorio da reserva (REMINDER, ADR-0035; destino, a reserva) e o
-- aviso de espera cancelada (WAIT_CANCELLED, revisión do 2026-11-11 de ADR-0033; destino, a
-- primeira das esperas canceladas desa persoa, e event_id, a cancelación). O único parcial de
-- wait_id e event_id segue impedindo escribir dúas veces pola mesma cancelación.

alter table notification_delivery drop constraint notification_delivery_kind_ck;

alter table notification_delivery add constraint notification_delivery_kind_ck
    check (kind in ('CONFIRMED', 'CHANGED', 'CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY',
                    'STAFF_INVITATION', 'NO_SHOW', 'SLOT_RELEASED', 'REMINDER',
                    'WAIT_CANCELLED'));

alter table notification_delivery drop constraint notification_delivery_wait_kind_ck;

alter table notification_delivery add constraint notification_delivery_wait_kind_ck
    check ((kind in ('SLOT_RELEASED', 'WAIT_CANCELLED')) = (wait_id is not null));
