-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Notification (plan do dominio 4, §2.1, D4-06; ADR-0027, punto 6): o rexistro de correos acepta
-- tamén o da invitación ao persoal. Cada fila é dunha reserva ou dunha invitación, nunca das dúas;
-- o tipo STAFF_INVITATION vai só coa invitación. Sen FK a staff_invitation (outro módulo, fóra da
-- lista pechada CrossModuleForeignKeys), como booking_id. Cada invitación envíase unha vez (un
-- reenvío é outra invitación): o único parcial impide rexistrala dúas veces. Nunca se garda a
-- ligazón nin o token, só o destinatario, o idioma e o resultado.

alter table notification_delivery add column invitation_id uuid;

alter table notification_delivery alter column booking_id drop not null;

alter table notification_delivery drop constraint notification_delivery_kind_ck;

alter table notification_delivery add constraint notification_delivery_kind_ck
    check (kind in ('CONFIRMED', 'CHANGED', 'CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY',
                    'STAFF_INVITATION'));

alter table notification_delivery add constraint notification_delivery_target_ck
    check (num_nonnulls(booking_id, invitation_id) = 1);

alter table notification_delivery add constraint notification_delivery_invitation_kind_ck
    check ((kind = 'STAFF_INVITATION') = (invitation_id is not null));

create unique index notification_delivery_invitation_uk
    on notification_delivery (municipality_id, invitation_id, event_id)
    where invitation_id is not null;
