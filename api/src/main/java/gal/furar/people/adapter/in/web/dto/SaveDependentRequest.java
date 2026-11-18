// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Os datos dunha persoa a cargo, na alta e na edición (con {@code If-Match}).
 *
 * @param name nome e apelidos
 * @param birthYear ano de nacemento (do actual menos 120 ao actual; valídao o caso de uso)
 * @param inMyCare a declaración «Esta persoa está ao meu cargo»: ten que vir a {@code true}
 */
public record SaveDependentRequest(
    @NotBlank @Size(max = 200) String name,
    @NotNull Integer birthYear,
    @Nullable Boolean inMyCare) {}
