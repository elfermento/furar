// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Alta dun campo personalizado. {@code code} e {@code type} non se poden cambiar. */
public record CustomFieldDefinitionRequest(
    @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{1,39}$") String code,
    @NotNull
        @Pattern(regexp = "^(TEXT|LONG_TEXT|NUMBER|DATE|BOOLEAN|SELECT|MULTI_SELECT)$")
        @Schema(
            allowableValues = {
              "TEXT",
              "LONG_TEXT",
              "NUMBER",
              "DATE",
              "BOOLEAN",
              "SELECT",
              "MULTI_SELECT"
            })
        String type,
    @NotNull @Valid LocalizedTextRequest label,
    @Valid LocalizedTextRequest help,
    @Valid FieldRulesRequest rules,
    @Size(max = 100) List<@Valid FieldOptionRequest> options,
    @Schema(description = "Dato persoal (RGPD); por defecto, non") Boolean personalData,
    @NotNull
        @Size(min = 1, max = 1)
        @Schema(
            description =
                "Onde se usa, co obrigatorio e a visibilidade: un só sitio (un campo, un sitio)")
        List<@Valid FieldUsageRequest> usages) {}
