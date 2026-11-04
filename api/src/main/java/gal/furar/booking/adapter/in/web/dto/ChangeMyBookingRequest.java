// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Cambiar unha reserva a outro oco da mesma instalación (plan do dominio 2, §2.2).
 *
 * @param useId o uso (o mesmo ou outro da mesma instalación)
 * @param start inicio da primeira franxa, un {@code start} da dispoñibilidade
 * @param slots franxas seguidas (1–16)
 * @param partId a parte; sen ela nun uso por partes, «tanto me ten» (a primeira libre)
 */
public record ChangeMyBookingRequest(
    @NotNull UUID useId,
    @NotNull Instant start,
    @NotNull @Min(1) @Max(16) Integer slots,
    @Nullable UUID partId) {}
