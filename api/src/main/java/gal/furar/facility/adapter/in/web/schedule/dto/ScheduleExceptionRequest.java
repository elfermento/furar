// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule.dto;

import gal.furar.facility.adapter.in.web.dto.BookingDecisionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Excepción enteira dun día (alta, ou edición con {@code If-Match} da excepción): pechado, ou
 * aberto cun tramo en minutos de reloxo de parede (0 ≤ inicio < fin ≤ 1440, múltiplos de 15).
 *
 * @param startMinute só se está aberto
 * @param endMinute só se está aberto
 * @param reason motivo opcional («Noiteboa», «Festa local»)
 * @param decisions primeiro paso sen elas; se volve 409 {@code facility.affected-bookings}, a mesma
 *     petición cunha decisión por reserva de {@code affected[]}
 */
public record ScheduleExceptionRequest(
    @NotNull LocalDate date,
    @NotNull Boolean open,
    @Nullable Integer startMinute,
    @Nullable Integer endMinute,
    @Nullable @Size(max = 200) String reason,
    @Nullable @Size(max = 10000) List<@Valid @NotNull BookingDecisionRequest> decisions) {}
