// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Concello. {@code customFields} leva só os campos que pode ver quen pregunta. */
public record MunicipalityResponse(
    UUID id,
    String slug,
    String name,
    @ArraySchema(schema = @Schema(allowableValues = {"gl", "es"})) List<String> languages,
    @Schema(allowableValues = {"gl", "es"}) String defaultLanguage,
    ThemeResponse theme,
    LegalDetailsResponse legal,
    Map<String, Object> customFields,
    long version,
    Instant createdAt,
    Instant updatedAt) {}
