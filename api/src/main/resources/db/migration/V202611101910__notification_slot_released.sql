-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- O sexto correo das reservas (ADR-0033, puntos 7 e 11): «Quedou libre», o aviso a quen esperaba
-- por unha hora. O seu destino non é unha reserva nin unha invitación, senón unha espera
-- (wait_id, sen FK: a espera é doutro módulo e bórrase antes ca o envío). event_id leva o
-- releaseId do evento: o único parcial impide escribir dúas veces á mesma espera pola mesma
-- liberación (un reintento), e deixa escribir outra vez se a hora se volve liberar.

alter table notification_delivery add column wait_id uuid;

alter table notification_delivery drop constraint notification_delivery_kind_ck;

alter table notification_delivery add constraint notification_delivery_kind_ck
    check (kind in ('CONFIRMED', 'CHANGED', 'CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY',
                    'STAFF_INVITATION', 'NO_SHOW', 'SLOT_RELEASED'));

alter table notification_delivery drop constraint notification_delivery_target_ck;

alter table notification_delivery add constraint notification_delivery_target_ck
    check (num_nonnulls(booking_id, invitation_id, wait_id) = 1);

alter table notification_delivery add constraint notification_delivery_wait_kind_ck
    check ((kind = 'SLOT_RELEASED') = (wait_id is not null));

create unique index notification_delivery_wait_uk
    on notification_delivery (municipality_id, wait_id, event_id)
    where wait_id is not null;
