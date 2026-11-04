// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Cambio de estado (con {@code If-Match}). Primeiro paso sen {@code decisions}; se volve 409 {@code
 * facility.affected-bookings}, a mesma petición cunha decisión por reserva de {@code affected[]}.
 *
 * @param status {@code ACTIVE}, {@code TEMPORARILY_CLOSED} ou {@code WITHDRAWN}
 */
public record ChangeFacilityStatusRequest(
    @NotNull FacilityStatusValue status,
    @Nullable @Size(max = 10000) List<@Valid @NotNull BookingDecisionRequest> decisions) {}
