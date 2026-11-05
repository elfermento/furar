// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O persoal reserva no nome dunha persoa sen conta: o mesmo oco ca {@link CreateMyBookingRequest},
 * para quen é e, se o uso ten prezo, se xa pagou.
 *
 * @param holder para quen é
 * @param paid se xa pagou no mostrador (só nos usos de pago); sen el, pendente
 * @param language o idioma dos correos á persoa; sen el, o da petición ({@code Accept-Language})
 * @param customFields campos propios da reserva, por código (ADR-0011); valídaos o caso de uso
 */
public record BookForRequest(
    @NotNull UUID facilityId,
    @NotNull UUID useId,
    @NotNull Instant start,
    @NotNull @Min(1) @Max(16) Integer slots,
    @Nullable UUID partId,
    @NotNull @Valid BookingHolderRequest holder,
    @Nullable Boolean paid,
    @Nullable @Pattern(regexp = "^(gl|es)$") @Schema(allowableValues = {"gl", "es"})
        String language,
    @Nullable @Schema(description = "Valores dos campos propios da reserva, por código")
        Map<String, Object> customFields) {}
