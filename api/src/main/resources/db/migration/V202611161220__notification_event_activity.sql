-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Os correos das actividades con horario e da lista de espera dos eventos (ADR-0041) no mesmo
-- rexistro de envíos, co evento en municipal_event_id coma os de ADR-0038. Parte das restricións
-- de V202611121120__notification_series.sql (a última que redefine notification_delivery_kind_ck)
-- e de V202611112210__notification_event.sql (a última de notification_delivery_event_kind_ck):
-- as mesmas listas máis tres tipos.
--
--   EVENT_WAITLIST_NOTIFIED hai unha praza libre para quen espera (clave: a inscrición e o aviso)
--   EVENT_SESSION_CANCELLED cancelouse unha sesión (clave: a sesión e a inscrición)
--   EVENT_ENDED_EARLY       a actividade rematou antes (clave: o cambio e a inscrición)

alter table notification_delivery drop constraint notification_delivery_kind_ck;

alter table notification_delivery add constraint notification_delivery_kind_ck
    check (kind in ('CONFIRMED', 'CHANGED', 'CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY',
                    'STAFF_INVITATION', 'NO_SHOW', 'SLOT_RELEASED', 'REMINDER',
                    'WAIT_CANCELLED', 'INCIDENT_REPORTED', 'INCIDENT_RESOLVED',
                    'EVENT_ACCEPTED', 'EVENT_REJECTED', 'EVENT_CHANGED', 'EVENT_CANCELLED',
                    'MATCH_CANCELLED', 'MATCH_CHANGED', 'MATCH_REQUESTED', 'MATCH_ACCEPTED',
                    'MATCH_REJECTED', 'MATCH_HIDDEN',
                    'SEASON_CREATED', 'SEASON_ENDED', 'SEASON_REQUEST_REJECTED',
                    'EVENT_WAITLIST_NOTIFIED', 'EVENT_SESSION_CANCELLED', 'EVENT_ENDED_EARLY'));

alter table notification_delivery drop constraint notification_delivery_event_kind_ck;

alter table notification_delivery add constraint notification_delivery_event_kind_ck
    check ((kind in ('EVENT_ACCEPTED', 'EVENT_REJECTED', 'EVENT_CHANGED', 'EVENT_CANCELLED',
                     'EVENT_WAITLIST_NOTIFIED', 'EVENT_SESSION_CANCELLED', 'EVENT_ENDED_EARLY'))
        = (municipal_event_id is not null));
