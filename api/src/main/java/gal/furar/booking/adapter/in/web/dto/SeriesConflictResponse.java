// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un día da tempada que xa ten algo (ADR-0039, punto 8).
 *
 * @param start inicio do que o pisa (en {@code OTHER_USE_EXCLUSIVE} e {@code OUT_OF_HOURS}, o da
 *     sesión)
 * @param bookingId en {@code BOOKING} e {@code SESSION}: a reserva que se pode cancelar
 * @param name de quen é a reserva ou de que entidade a sesión
 * @param label o motivo do bloqueo ou o nome do uso exclusivo
 */
public record SeriesConflictResponse(
    LocalDate date,
    Instant start,
    Instant end,
    SeriesConflictKindValue kind,
    @Nullable UUID bookingId,
    @Nullable String name,
    @Nullable String label) {}
