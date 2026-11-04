// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Reserva afectada no problema 409 {@code facility.affected-bookings}. Data e minutos de reloxo de
 * parede en Europe/Madrid (ADR-0022, punto 8).
 */
public record AffectedBookingResponse(
    UUID bookingId,
    String holderName,
    boolean hasEmail,
    LocalDate date,
    int startMinute,
    int endMinute,
    String useName) {}
