-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
--
-- Actividades con horario (ADR-0041): cada sesión dunha actividade do concello nunha instalación é
-- unha reserva, coma as sesións das tempadas (ADR-0039, punto 5), para que a dispoñibilidade, as
-- esperas, o calendario, os choques e os indicadores a vexan sen un segundo ocupante e a base
-- impida que pise nada. A reserva leva a actividade en activity_id, sen FK: a actividade é do
-- módulo event, que depende de booking e non ao revés (fóra da lista pechada CrossModuleForeignKeys).
--
-- Unha sesión dunha actividade non ten persoa nin entidade como titular (só o nome da actividade,
-- en holder_name), nin prezo, e sempre a fixo o persoal (booked_by).

alter table booking
    add column activity_id uuid,
    add constraint booking_activity_ck check (activity_id is null
        or (organization_id is null and series_id is null and booked_by is not null
            and price_kind = 'NONE' and holder_subject is null and holder_email is null
            and holder_phone is null));

create index booking_activity_idx on booking (municipality_id, activity_id, lower(period))
    where activity_id is not null;
