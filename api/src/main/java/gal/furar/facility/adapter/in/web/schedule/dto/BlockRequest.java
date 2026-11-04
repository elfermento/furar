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
 * Bloqueo enteiro (alta, ou edición con {@code If-Match} do bloqueo): un día e un tramo en minutos
 * de reloxo de parede en Europe/Madrid (0 ≤ inicio &lt; fin ≤ 1440, múltiplos de 15; todo o día =
 * 0–1440). O servidor calcula o período real.
 *
 * @param reason motivo opcional («Mantemento do céspede»)
 * @param decisions primeiro paso sen elas; se volve 409 {@code facility.affected-bookings}, a mesma
 *     petición cunha decisión por reserva de {@code affected[]}
 */
public record BlockRequest(
    @NotNull LocalDate date,
    @NotNull Integer startMinute,
    @NotNull Integer endMinute,
    @Nullable @Size(max = 200) String reason,
    @Nullable @Size(max = 10000) List<@Valid @NotNull BookingDecisionRequest> decisions) {}
