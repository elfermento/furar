// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Parte dun uso por partes. Sen {@code id}, parte nova; co {@code id} dunha que xa ten o uso,
 * consérvase (pódese renomear ou mover).
 */
public record UsePartRequest(@Nullable UUID id, @NotBlank @Size(max = 100) String name) {}
