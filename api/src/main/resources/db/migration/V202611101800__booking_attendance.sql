-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Asistencia, á parte do estado (ADR-0032, puntos 1 e 2): unha reserva confirmada pode quedar
-- validada (USED: leuse o seu QR ou tecleouse o código) ou como falta (NO_SHOW). O estado non
-- cambia: segue ocupando a súa hora e contando no límite por persoa e nos indicadores.
--
-- attendance_at e attendance_by gardan o último cambio da marca, tamén un «Desfacer»: o sub do
-- persoal ou 'furar:no-show' (a tarefa). Por iso a tarefa só marca as nunca tocadas
-- (attendance_by is null). Ao anonimizar a reserva, attendance_by queda: é o persoal ou a tarefa,
-- coma booked_by.

alter table booking
    add column attendance text not null default 'NONE',
    add column attendance_at timestamptz,
    add column attendance_by text;

alter table booking
    add constraint booking_attendance_ck check (attendance in ('NONE', 'USED', 'NO_SHOW')),
    -- Só unha reserva confirmada leva marca: cancelala bórraa na mesma sentenza.
    add constraint booking_attendance_status_ck
        check (attendance = 'NONE' or status = 'CONFIRMED'),
    add constraint booking_attendance_actor_ck check (
        (attendance_at is null) = (attendance_by is null)
        and (attendance = 'NONE' or attendance_at is not null)),
    add constraint booking_attendance_by_ck check (length(attendance_by) between 1 and 255);

-- As candidatas da tarefa das faltas: confirmadas, sen marca e nunca tocadas, pola fin da franxa.
create index booking_no_show_candidates_idx on booking (municipality_id, facility_id, upper(period))
    where status = 'CONFIRMED' and attendance = 'NONE' and attendance_by is null;

-- As faltas dun correo, para a conta da penalización.
create index booking_no_show_email_idx on booking (municipality_id, holder_email_key, upper(period))
    where attendance = 'NO_SHOW';

alter table booking_event drop constraint booking_event_kind_ck;
alter table booking_event
    add constraint booking_event_kind_ck check (kind in (
        'CREATED', 'CHANGED', 'CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY', 'KEPT', 'PAID',
        'REFUNDED', 'PAYMENT_UNDONE', 'LINKED', 'HOLDER_CHANGED', 'VALIDATED', 'NO_SHOW',
        'ATTENDANCE_UNDONE'));
