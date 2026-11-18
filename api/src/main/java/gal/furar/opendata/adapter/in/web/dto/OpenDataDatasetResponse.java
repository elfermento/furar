// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Un conxunto: a clave, o día do último cambio (nulo antes da primeira noite) e os formatos. */
public record OpenDataDatasetResponse(
    @Schema(allowableValues = {"FACILITIES", "CLOSURES", "OCCUPANCY"}) String key,
    @Nullable LocalDate updatedOn,
    @ArraySchema(schema = @Schema(allowableValues = {"csv", "json", "geojson"}))
        List<String> formats) {}
