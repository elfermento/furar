-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Os correos das tempadas das entidades (ADR-0039, punto 12) no mesmo rexistro de envíos. Parte das
-- restricións de V202611121020__notification_open_match.sql (a última que as redefine): a mesma
-- lista de tipos máis os tres das tempadas, e dous destinos máis, sen FK (a serie e a solicitude
-- bórranse coa súa conservación).
--
-- Á entidade: SEASON_CREATED e SEASON_ENDED pola serie (series_id), SEASON_REQUEST_REJECTED pola
-- solicitude (series_request_id). O único parcial impide escribir dúas veces o mesmo correo.

alter table notification_delivery
    add column series_id uuid,
    add column series_request_id uuid;

alter table notification_delivery drop constraint notification_delivery_kind_ck;

alter table notification_delivery add constraint notification_delivery_kind_ck
    check (kind in ('CONFIRMED', 'CHANGED', 'CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY',
                    'STAFF_INVITATION', 'NO_SHOW', 'SLOT_RELEASED', 'REMINDER',
                    'WAIT_CANCELLED', 'INCIDENT_REPORTED', 'INCIDENT_RESOLVED',
                    'EVENT_ACCEPTED', 'EVENT_REJECTED', 'EVENT_CHANGED', 'EVENT_CANCELLED',
                    'MATCH_CANCELLED', 'MATCH_CHANGED', 'MATCH_REQUESTED', 'MATCH_ACCEPTED',
                    'MATCH_REJECTED', 'MATCH_HIDDEN',
                    'SEASON_CREATED', 'SEASON_ENDED', 'SEASON_REQUEST_REJECTED'));

alter table notification_delivery drop constraint notification_delivery_target_ck;

alter table notification_delivery add constraint notification_delivery_target_ck
    check (num_nonnulls(booking_id, invitation_id, wait_id, incident_id, municipal_event_id,
                        open_match_player_id, series_id, series_request_id) = 1);

alter table notification_delivery add constraint notification_delivery_series_kind_ck
    check ((kind in ('SEASON_CREATED', 'SEASON_ENDED')) = (series_id is not null));

alter table notification_delivery add constraint notification_delivery_series_request_kind_ck
    check ((kind = 'SEASON_REQUEST_REJECTED') = (series_request_id is not null));

create unique index notification_delivery_series_uk
    on notification_delivery (municipality_id, series_id, kind, event_id)
    where series_id is not null;

create unique index notification_delivery_series_request_uk
    on notification_delivery (municipality_id, series_request_id, kind, event_id)
    where series_request_id is not null;
