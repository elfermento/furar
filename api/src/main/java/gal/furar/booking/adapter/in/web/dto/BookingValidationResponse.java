// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * O resultado de ler o código dunha reserva, sempre cos seus datos (ADR-0032, punto 4).
 *
 * @param attendanceAt cando se validou (agora ou antes); nulo se non está validada
 * @param attendanceBy o nome de quen a validou; nulo se non está validada ou xa non se sabe
 */
public record BookingValidationResponse(
    BookingValidationOutcomeValue outcome,
    ManagedBookingDetailResponse booking,
    @Nullable Instant attendanceAt,
    @Nullable String attendanceBy) {}
