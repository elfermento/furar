-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Quen gardou a lista dunha sesión por última vez e cando (ADR-0041), para «Pasada ás 17:10 por
-- …» na xestión. O nome cópiase da conta do persoal ao gardar, coma o nome de quen se apunta; vai
-- coa sesión (bórrase con ela). attendance_taken_at segue sendo a primeira vez.

alter table event_session
    add column attendance_saved_at timestamptz,
    add column attendance_saved_by_name text;

update event_session set attendance_saved_at = attendance_taken_at
 where attendance_taken_at is not null;

alter table event_session
    add constraint event_session_attendance_saved_ck check (
        (attendance_saved_at is null) = (attendance_taken_at is null)
        and (attendance_saved_by_name is null or attendance_saved_at is not null)
        and (attendance_saved_by_name is null or length(attendance_saved_by_name) between 1 and 200));
