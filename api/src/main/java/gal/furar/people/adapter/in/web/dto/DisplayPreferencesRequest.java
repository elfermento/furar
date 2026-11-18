// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * As preferencias de accesibilidade da conta (ADR-0031, revisión do 2026-11-11). Nun interruptor,
 * {@code null} = segue o que pida o sistema.
 */
public record DisplayPreferencesRequest(
    @NotNull
        @Pattern(regexp = "normal|large|x-large")
        @Schema(
            type = "string",
            allowableValues = {"normal", "large", "x-large"})
        String textSize,
    @Schema(types = {"boolean", "null"}) @Nullable Boolean highContrast,
    @Schema(types = {"boolean", "null"}) @Nullable Boolean reducedMotion) {}
