// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Opción dun campo de selección. Unha opción gardada que non vén na lista dunha edición bórrase se
 * non ten valores ({@code customfield.option-has-values} se os ten: desactívase con {@code
 * inactive}).
 */
public record FieldOptionRequest(
    @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9_]{0,39}$") String code,
    @NotNull @Valid LocalizedTextRequest label,
    @Schema(description = "Desactivada: non se pode escoller nun valor novo. Por defecto, non")
        Boolean inactive) {}
