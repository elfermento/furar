-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Os correos dos eventos do concello (ADR-0038) no mesmo rexistro de envíos: á persoa cando o
-- concello acepta ou rexeita a súa solicitude, e a cada persoa apuntada cando o evento se cancela
-- ou cambia de data, hora ou lugar. O destino é o evento (municipal_event_id, sen FK, coma
-- incident_id); event_id é a clave de cada correo (a inscrición, ou unha por cambio e inscrición).
-- O único parcial impide escribir dúas veces o mesmo correo á mesma persoa.

alter table notification_delivery add column municipal_event_id uuid;

alter table notification_delivery drop constraint notification_delivery_kind_ck;

alter table notification_delivery add constraint notification_delivery_kind_ck
    check (kind in ('CONFIRMED', 'CHANGED', 'CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY',
                    'STAFF_INVITATION', 'NO_SHOW', 'SLOT_RELEASED', 'REMINDER',
                    'WAIT_CANCELLED', 'INCIDENT_REPORTED', 'INCIDENT_RESOLVED',
                    'EVENT_ACCEPTED', 'EVENT_REJECTED', 'EVENT_CHANGED', 'EVENT_CANCELLED'));

alter table notification_delivery drop constraint notification_delivery_target_ck;

alter table notification_delivery add constraint notification_delivery_target_ck
    check (num_nonnulls(booking_id, invitation_id, wait_id, incident_id, municipal_event_id) = 1);

alter table notification_delivery add constraint notification_delivery_event_kind_ck
    check ((kind in ('EVENT_ACCEPTED', 'EVENT_REJECTED', 'EVENT_CHANGED', 'EVENT_CANCELLED'))
        = (municipal_event_id is not null));

create unique index notification_delivery_municipal_event_uk
    on notification_delivery (municipality_id, municipal_event_id, kind, event_id)
    where municipal_event_id is not null;
