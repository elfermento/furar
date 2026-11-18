-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Os correos das partidas abertas (ADR-0040, punto 12) no mesmo rexistro de envíos. Parte das
-- restricións de V202611112210__notification_event.sql (a última que as redefine): a mesma lista
-- de tipos máis os seis das partidas, e un destino máis.
--
-- Á persoa que se une (open_match_player_id, sen FK: a fila bórrase coa súa conservación):
-- MATCH_ACCEPTED, MATCH_REJECTED, MATCH_CHANGED e MATCH_CANCELLED. A quen organiza, pola súa
-- reserva (booking_id): MATCH_REQUESTED, MATCH_HIDDEN e MATCH_CANCELLED cando non se chegou ao
-- mínimo. event_id: a solicitude en MATCH_REQUESTED; a publicación nos demais. O único parcial
-- impide escribir dúas veces o mesmo correo á mesma persoa.

alter table notification_delivery add column open_match_player_id uuid;

alter table notification_delivery drop constraint notification_delivery_kind_ck;

alter table notification_delivery add constraint notification_delivery_kind_ck
    check (kind in ('CONFIRMED', 'CHANGED', 'CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY',
                    'STAFF_INVITATION', 'NO_SHOW', 'SLOT_RELEASED', 'REMINDER',
                    'WAIT_CANCELLED', 'INCIDENT_REPORTED', 'INCIDENT_RESOLVED',
                    'EVENT_ACCEPTED', 'EVENT_REJECTED', 'EVENT_CHANGED', 'EVENT_CANCELLED',
                    'MATCH_CANCELLED', 'MATCH_CHANGED', 'MATCH_REQUESTED', 'MATCH_ACCEPTED',
                    'MATCH_REJECTED', 'MATCH_HIDDEN'));

alter table notification_delivery drop constraint notification_delivery_target_ck;

alter table notification_delivery add constraint notification_delivery_target_ck
    check (num_nonnulls(booking_id, invitation_id, wait_id, incident_id, municipal_event_id,
                        open_match_player_id) = 1);

alter table notification_delivery add constraint notification_delivery_match_player_kind_ck
    check (kind not in ('MATCH_ACCEPTED', 'MATCH_REJECTED', 'MATCH_CHANGED')
           or open_match_player_id is not null);

alter table notification_delivery add constraint notification_delivery_match_organizer_kind_ck
    check (kind not in ('MATCH_REQUESTED', 'MATCH_HIDDEN') or booking_id is not null);

alter table notification_delivery add constraint notification_delivery_match_player_target_ck
    check (open_match_player_id is null
           or kind in ('MATCH_ACCEPTED', 'MATCH_REJECTED', 'MATCH_CHANGED', 'MATCH_CANCELLED'));

create unique index notification_delivery_open_match_player_uk
    on notification_delivery (municipality_id, open_match_player_id, kind, event_id)
    where open_match_player_id is not null;
