// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule.dto;

import gal.furar.facility.adapter.in.web.dto.BookingDecisionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Tempada enteira, con {@code If-Match} da tempada: nome, datas e a semana completa, que substitúe
 * a anterior nun só gardado (as filas que non veñen quedan pechadas). Sen {@code endDate}, a
 * tempada non ten fin.
 *
 * @param decisions primeiro paso sen elas; se volve 409 {@code facility.affected-bookings}, a mesma
 *     petición cunha decisión por reserva de {@code affected[]}
 */
public record UpdateSeasonRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull LocalDate startDate,
    @Nullable LocalDate endDate,
    @NotNull @Size(max = 8) List<@Valid @NotNull DayHoursRequest> week,
    @Nullable @Size(max = 10000) List<@Valid @NotNull BookingDecisionRequest> decisions) {}
