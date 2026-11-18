// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Alta dun concello. O slug non se pode cambiar despois. */
public record CreateMunicipalityRequest(
    @NotBlank @Size(min = 2, max = 40) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$") String slug,
    @NotBlank @Size(max = 100) String name,
    @NotEmpty @Size(max = 2) @ArraySchema(schema = @Schema(allowableValues = {"gl", "es"}))
        List<@Pattern(regexp = "^(gl|es)$") String> languages,
    @NotBlank @Pattern(regexp = "^(gl|es)$") @Schema(allowableValues = {"gl", "es"})
        String defaultLanguage,
    @Valid @Schema(description = "Sen tema = tema por defecto") ThemeRequest theme,
    @Valid @Schema(description = "Opcional") LegalDetailsRequest legal) {}
