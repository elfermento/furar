// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Reservar un oco (plan do dominio 2, §2.2).
 *
 * @param start inicio da primeira franxa, un {@code start} da dispoñibilidade
 * @param slots franxas seguidas (1–16)
 * @param partId a parte; sen ela nun uso por partes, «tanto me ten» (a primeira libre)
 * @param customFields campos propios da reserva, por código (ADR-0011); valídaos o caso de uso
 */
public record CreateMyBookingRequest(
    @NotNull UUID facilityId,
    @NotNull UUID useId,
    @NotNull Instant start,
    @NotNull @Min(1) @Max(16) Integer slots,
    @Nullable UUID partId,
    @Nullable @Schema(description = "Valores dos campos propios da reserva, por código")
        Map<String, Object> customFields) {}
