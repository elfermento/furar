// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Valorar unha reserva (ADR-0037).
 *
 * @param stars de 1 a 5
 * @param comment opcional, como moito 300 caracteres; baleiro conta como sen comentario
 */
public record ReviewBookingRequest(
    @NotNull @Min(1) @Max(5) Integer stars, @Nullable String comment) {}
