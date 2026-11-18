// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Un ficheiro {@code furar-export} tal como o descarga outra instalación (ADR-0018): a versión
 * actual ou unha anterior que o importador aínda le.
 */
public record MunicipalityImportRequest(
    @NotNull @Pattern(regexp = "^furar-export$") @Schema(allowableValues = {"furar-export"})
        String format,
    @NotNull
        @Min(MunicipalityExportResponse.OLDEST_READABLE_VERSION)
        @Max(MunicipalityExportResponse.VERSION)
        Integer version,
    Instant exportedAt,
    @NotNull @Valid MunicipalityDataRequest municipality,
    Map<String, List<Object>> sections) {}
