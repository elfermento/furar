// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule.dto;

import gal.furar.facility.adapter.in.web.dto.BookingDecisionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Corpo opcional do borrado: só no segundo paso do modal de reservas afectadas.
 *
 * @param decisions unha decisión por reserva de {@code affected[]}
 */
public record DeleteSeasonRequest(
    @Nullable @Size(max = 10000) List<@Valid @NotNull BookingDecisionRequest> decisions) {}
