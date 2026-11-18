// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A actividade do concello da que é sesión a reserva (ADR-0041), para ir a ela desde o calendario:
 * o evento e a sesión.
 */
public record BookingActivityResponse(UUID eventId, @Nullable UUID sessionId) {}
