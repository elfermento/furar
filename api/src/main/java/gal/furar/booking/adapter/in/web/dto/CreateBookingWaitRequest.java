// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** Esperar por unha hora ocupada: o uso e o inicio da franxa (ADR-0033, punto 6). */
public record CreateBookingWaitRequest(
    @NotNull UUID facilityId, @NotNull UUID useId, @NotNull Instant start) {}
