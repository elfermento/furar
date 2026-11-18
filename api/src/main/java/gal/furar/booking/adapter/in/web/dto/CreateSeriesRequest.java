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
 * Crear unha tempada (ou aprobar unha solicitude cos datos axustados; ADR-0039, puntos 8 e 11): a
 * entrada da vista previa e as datas con choque que viu o xestor.
 *
 * @param skip as datas de {@code conflicts} da vista previa: se baixo o bloqueo non son estas, 409
 *     {@code booking.series-conflicts-changed} coa vista previa nova
 */
public record CreateSeriesRequest(
    @NotNull UUID organizationId,
    @NotNull UUID facilityId,
    @NotNull UUID useId,
    @Nullable UUID partId,
    @NotNull @Schema(description = "Días da semana ISO (luns = 1)") List<Integer> weekdays,
    @NotNull Integer startMinute,
    @NotNull Integer endMinute,
    @NotNull LocalDate firstDate,
    @NotNull LocalDate lastDate,
    @Nullable List<LocalDate> skip) {}
