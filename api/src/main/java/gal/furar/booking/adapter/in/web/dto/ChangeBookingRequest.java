// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O persoal cambia unha reserva co mesmo formulario, relleno.
 *
 * @param useId o uso (o mesmo ou outro da mesma instalación)
 * @param start inicio da primeira franxa, un {@code start} da dispoñibilidade
 * @param slots franxas seguidas (1–16)
 * @param partId a parte; sen ela nun uso por partes, «tanto me ten»
 * @param holder para quen é; só nas reservas que fixo o persoal (sen el, non se toca)
 * @param paid se xa pagou (sen el, non se toca)
 * @param notifyPerson se se avisa á persoa do cambio de día ou hora por correo (sen el, si)
 * @param customFields campos propios da reserva, por código (sen eles, non se tocan)
 */
public record ChangeBookingRequest(
    @NotNull UUID useId,
    @NotNull Instant start,
    @NotNull @Min(1) @Max(16) Integer slots,
    @Nullable UUID partId,
    @Nullable @Valid BookingHolderRequest holder,
    @Nullable Boolean paid,
    @Nullable Boolean notifyPerson,
    @Nullable @Schema(description = "Valores dos campos propios da reserva, por código")
        Map<String, Object> customFields) {}
