// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** Edición do concello (con {@code If-Match}). {@code customFields} substitúe os valores. */
public record UpdateMunicipalityRequest(
    @NotBlank @Size(max = 100) String name,
    @NotEmpty @Size(max = 2) @ArraySchema(schema = @Schema(allowableValues = {"gl", "es"}))
        List<@Pattern(regexp = "^(gl|es)$") String> languages,
    @NotBlank @Pattern(regexp = "^(gl|es)$") @Schema(allowableValues = {"gl", "es"})
        String defaultLanguage,
    @NotNull @Valid ThemeRequest theme,
    @Schema(description = "Valores dos campos personalizados, por código")
        Map<String, Object> customFields,
    @Valid @Schema(description = "Sen este obxecto, os datos legais non cambian")
        LegalDetailsRequest legal) {}
