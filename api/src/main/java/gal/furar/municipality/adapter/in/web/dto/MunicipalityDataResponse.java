// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O concello no ficheiro {@code furar-export}: todos os campos personalizados (tamén os de persoal)
 * e sen {@code version}, que é do bloqueo optimista desta instalación, non un dato.
 */
public record MunicipalityDataResponse(
    UUID id,
    String slug,
    String name,
    @ArraySchema(schema = @Schema(allowableValues = {"gl", "es"})) List<String> languages,
    @Schema(allowableValues = {"gl", "es"}) String defaultLanguage,
    ThemeResponse theme,
    LegalDetailsResponse legal,
    @Schema(description = "Textos propios das páxinas legais, xa saneados")
        LegalTextsResponse legalTexts,
    Map<String, Object> customFields,
    Instant createdAt,
    Instant updatedAt) {}
