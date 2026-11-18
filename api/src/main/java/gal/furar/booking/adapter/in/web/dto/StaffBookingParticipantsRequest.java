// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Para quen é unha reserva que fai ou cambia o persoal nun uso por prazas (ADR-0031, puntos 7 e 9).
 *
 * @param self se a persoa a nome de quen vai a reserva ocupa praza (sen el, si)
 * @param dependentIds persoas a cargo gardadas na conta do correo da reserva
 * @param companions acompañantes escritos só para esta reserva, sen ficha
 */
public record StaffBookingParticipantsRequest(
    @Nullable Boolean self,
    @Nullable @Size(max = 11) List<UUID> dependentIds,
    @Nullable @Size(max = 11) List<@Valid BookingCompanionRequest> companions) {}
