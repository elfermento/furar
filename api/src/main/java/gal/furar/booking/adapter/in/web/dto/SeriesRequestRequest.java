// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Solicitar unha tempada (ADR-0039, punto 10), para unha instalación que ve a veciñanza.
 *
 * @param weekdays días da semana ISO (luns = 1)
 * @param note unha nota para o concello
 */
public record SeriesRequestRequest(
    @NotNull UUID facilityId,
    @NotNull UUID useId,
    @Nullable UUID partId,
    @NotNull @Schema(description = "Días da semana ISO (luns = 1)") List<Integer> weekdays,
    @NotNull Integer startMinute,
    @NotNull Integer endMinute,
    @NotNull LocalDate firstDate,
    @NotNull LocalDate lastDate,
    @Nullable @Size(max = 500) String note) {}
