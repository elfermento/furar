// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * «+ Tempada».
 *
 * @param startDate primeiro día (incluído)
 * @param endDate último día (incluído); sen el, a tempada non ten fin
 * @param copyFrom «empezar co horario de»: outra tempada da instalación; sen ela, a semana empeza
 *     pechada. A fila de festivos énchese coa do domingo
 */
public record CreateSeasonRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull LocalDate startDate,
    @Nullable LocalDate endDate,
    @Nullable UUID copyFrom) {}
