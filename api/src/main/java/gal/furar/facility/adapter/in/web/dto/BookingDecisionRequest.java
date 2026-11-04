// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Decisión sobre unha reserva afectada: {@code CANCEL} con polo menos unha canle, {@code KEEP} sen
 * ningunha (se non, 422 {@code facility.decision-invalid}).
 *
 * @param action {@code CANCEL} ou {@code KEEP}
 * @param channels {@code EMAIL} ou {@code WEB} (a reserva non garda teléfono)
 */
public record BookingDecisionRequest(
    @NotNull UUID bookingId,
    @NotNull DecisionActionValue action,
    @Nullable @Size(max = 2) Set<@NotNull NoticeChannelValue> channels) {}
