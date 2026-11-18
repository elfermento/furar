// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A tempada dunha sesión (ADR-0039).
 *
 * @param weekdays días da semana ISO (luns = 1)
 * @param startMinute inicio, en minutos de reloxo de parede en Europe/Madrid
 * @param endMinute fin; 1440 é a medianoite do día seguinte
 * @param sessions sesións confirmadas
 */
public record BookingSeasonResponse(
    UUID id,
    @Schema(description = "Días da semana ISO (luns = 1)") List<Integer> weekdays,
    int startMinute,
    int endMinute,
    LocalDate firstDate,
    LocalDate lastDate,
    int sessions,
    SeriesPaymentStatusValue paymentStatus) {}
