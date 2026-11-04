// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema; // @cf
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map; // @cf

/** Edición (con {@code If-Match}). {@code customFields} substitúe os valores que ve quen edita. */
public record Update@Name@Request(
    @Schema(description = "Valores dos campos personalizados, por código") // @cf
    Map<String, Object> customFields, // @cf
    @NotBlank @Size(max = 100) String name) {}
