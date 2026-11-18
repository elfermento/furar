// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Os ocos libres dun uso nun día (ADR-0025, punto 7). {@code today} e {@code now} son os do
 * servidor, para que a web non dependa do reloxo do navegador. Só saen os ocos que a antelación
 * permite; unha franxa xa empezada nunca.
 *
 * @param bookable falso se a instalación só amosa os ocos libres (sen reserva por internet): os
 *     ocos saen sen botón
 * @param busy as horas ocupadas, en orde (ADR-0033, punto 2): só se {@code bookable}; nada máis ca
 *     a hora
 */
public record AvailabilityResponse(
    LocalDate date,
    LocalDate today,
    Instant now,
    boolean bookable,
    List<AvailableSlotResponse> slots,
    List<BusySlotResponse> busy) {}
