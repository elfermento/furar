// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Onde se usa un campo, co seu obrigatorio e visibilidade nese sitio. */
public record FieldUsageRequest(
    @NotNull
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$")
        @Schema(
            description =
                "Entidade, entre as rexistradas polos módulos (MUNICIPALITY, FACILITY, BOOKING)",
            example = "MUNICIPALITY")
        String target,
    @Schema(description = "Por defecto, non") Boolean required,
    @NotNull
        @Pattern(regexp = "^(PUBLIC|OWNER|STAFF)$")
        @Schema(
            allowableValues = {"PUBLIC", "OWNER", "STAFF"},
            description = "BOOKING non admite PUBLIC")
        String visibility) {}
