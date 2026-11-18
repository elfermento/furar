// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Edición dun campo. Unha opción quitada da lista bórrase se non ten valores; o sitio só se pode
 * cambiar mentres o campo non ten valores.
 */
public record UpdateCustomFieldDefinitionRequest(
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
