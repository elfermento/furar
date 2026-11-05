-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Reserva por outra persoa (especificación, bloque 5, decidido o 2026-11-04): o persoal do
-- mostrador reserva no nome dunha persoa sen conta. A reserva garda quen a fixo (booked_by, o sub
-- do persoal, coma cancelled_by) e, da persoa, o nome e, se os dá, o correo (para a confirmación) e
-- o teléfono. Sen conta non hai holder_subject: a reserva non sae en «as miñas reservas» de
-- ninguén. Unha reserva coa conta da persoa segue sen teléfono (P7) e sen booked_by.
--
-- A regra de booking_anonymized_ck cámbiase: sen anonimizar, o nome sempre está; a conta e o
-- correo, sempre que reserva a persoa (sen booked_by). Anonimizada, sen conta, nome, correo nin
-- teléfono. booked_by queda ao anonimizar, coma o persoal nas filas da historia.

alter table booking add column holder_phone text;
alter table booking add column booked_by text;

alter table booking drop constraint booking_anonymized_ck;

alter table booking
    add constraint booking_anonymized_ck check (
        (anonymized_at is null) = (holder_name is not null)
        and (anonymized_at is null
             or (holder_subject is null and holder_email is null and holder_phone is null))
        and (anonymized_at is not null or booked_by is not null
             or (holder_subject is not null and holder_email is not null))),
    add constraint booking_booked_by_ck
        check (booked_by is null or length(booked_by) between 1 and 255),
    -- O persoal reserva para persoas sen conta; coa conta reserva a propia persoa.
    add constraint booking_booked_by_holder_ck check (booked_by is null or holder_subject is null),
    add constraint booking_holder_phone_ck check (
        holder_phone is null
        or (booked_by is not null and length(holder_phone) between 3 and 30
            and holder_phone ~ '^[0-9+() .-]+$'));
