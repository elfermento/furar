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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** O concello dun ficheiro {@code furar-export} que se importa (mesma forma ca a exportación). */
public record MunicipalityDataRequest(
    @NotNull UUID id,
    @NotBlank @Size(min = 2, max = 40) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$") String slug,
    @NotBlank @Size(max = 100) String name,
    @NotEmpty @Size(max = 2) @ArraySchema(schema = @Schema(allowableValues = {"gl", "es"}))
        List<@Pattern(regexp = "^(gl|es)$") String> languages,
    @NotBlank @Pattern(regexp = "^(gl|es)$") @Schema(allowableValues = {"gl", "es"})
        String defaultLanguage,
    @NotNull @Valid ThemeRequest theme,
    @Valid @Schema(description = "Sen datos legais nos ficheiros anteriores a AP4-01")
        LegalDetailsRequest legal,
    @Valid
        @Schema(
            description =
                "Textos propios das páxinas legais; o servidor sanéaos outra vez ao importar."
                    + " Sen eles nos ficheiros anteriores ao 2026-10-29")
        LegalTextsRequest legalTexts,
    Map<String, Object> customFields,
    @NotNull Instant createdAt,
    @NotNull Instant updatedAt) {}
