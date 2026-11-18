// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Cambiar unha reserva a outro oco da mesma instalación.
 *
 * @param useId o uso (o mesmo ou outro da mesma instalación)
 * @param start inicio da primeira franxa, un {@code start} da dispoñibilidade
 * @param slots franxas seguidas (1–16)
 * @param partId a parte; sen ela nun uso por partes, «tanto me ten» (a primeira libre)
 * @param participants para quen é despois do cambio, só nos usos por prazas (ADR-0031): así se
 *     quita ou se engade unha persoa; sen el, as mesmas
 */
public record ChangeMyBookingRequest(
    @NotNull UUID useId,
    @NotNull Instant start,
    @NotNull @Min(1) @Max(16) Integer slots,
    @Nullable UUID partId,
    @Nullable @Valid BookingParticipantsRequest participants) {}
