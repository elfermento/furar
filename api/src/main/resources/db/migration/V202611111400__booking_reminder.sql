-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Recordatorio por correo da reserva (ADR-0035).
--
-- booking.reminded_at: cando a tarefa deu o recordatorio por enviado (a fila queda marcada na
-- mesma transacción que publica o evento): nunca se manda dúas veces. O índice parcial só leva
-- as confirmadas que aínda non o recibiron, ordenadas polo prazo para cancelar de balde, que é
-- o que decide cando toca (24 horas antes).
--
-- booking_reminder_opt_out: o correo (EmailKey.of, a mesma clave ca booking.holder_email_key; a
-- base non normaliza) que non quere recordatorios neste concello. Vale para a reserva feita con
-- conta e para a do mostrador a nome dun correo sen conta. Sen nome nin conta: só a clave e
-- desde cando.

alter table booking add column reminded_at timestamptz;

create index booking_reminder_due_idx on booking (municipality_id, cancel_until)
    where status = 'CONFIRMED' and reminded_at is null and anonymized_at is null;

create table booking_reminder_opt_out (
    municipality_id uuid        not null references municipality (id),
    email_key       text        not null,
    created_at      timestamptz not null,
    constraint booking_reminder_opt_out_pk primary key (municipality_id, email_key),
    constraint booking_reminder_opt_out_email_ck check (length(email_key) between 3 and 254)
);

alter table booking_reminder_opt_out enable row level security;

create policy booking_reminder_opt_out_tenant on booking_reminder_opt_out
    using (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid)
    with check (municipality_id = nullif(current_setting('furar.municipality_id', true), '')::uuid);
