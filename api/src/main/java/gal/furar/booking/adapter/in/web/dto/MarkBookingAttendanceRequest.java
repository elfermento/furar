// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Marcar a asistencia dunha reserva xa rematada (ADR-0032, punto 5): só {@code NO_SHOW}, a falta.
 * Validar faise lendo o código e desfacer, co {@code DELETE}.
 */
public record MarkBookingAttendanceRequest(
    @NotNull @Pattern(regexp = "^NO_SHOW$") @Schema(allowableValues = {"NO_SHOW"})
        String attendance) {}
