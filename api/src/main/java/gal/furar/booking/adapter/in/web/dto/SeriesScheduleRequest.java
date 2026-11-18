// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha tempada para unha entidade (ADR-0039, punto 8): a vista previa.
 *
 * @param weekdays días da semana ISO (luns = 1)
 * @param startMinute inicio en minutos de reloxo de parede (Europe/Madrid), de 15 en 15
 * @param endMinute fin, de 15 en 15; 1440 é a medianoite do día seguinte
 * @param lastDate como moito un ano despois de {@code firstDate}
 */
public record SeriesScheduleRequest(
    @NotNull UUID organizationId,
    @NotNull UUID facilityId,
    @NotNull UUID useId,
    @Nullable UUID partId,
    @NotNull @Schema(description = "Días da semana ISO (luns = 1)") List<Integer> weekdays,
    @NotNull Integer startMinute,
    @NotNull Integer endMinute,
    @NotNull LocalDate firstDate,
    @NotNull LocalDate lastDate) {}
