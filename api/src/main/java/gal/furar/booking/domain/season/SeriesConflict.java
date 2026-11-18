// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain.season;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un día da tempada que xa ten algo e non se crea (ADR-0039, punto 8).
 *
 * @param start inicio do que o pisa (nun choque con outro uso ou fóra de horario, o da sesión)
 * @param bookingId a reserva ou a sesión que o pisa, para cancelala desde a lista
 * @param name de quen é a reserva ou de que entidade a sesión
 * @param label o motivo do bloqueo ou o nome do uso exclusivo
 */
public record SeriesConflict(
    LocalDate date,
    Instant start,
    Instant end,
    Kind kind,
    @Nullable UUID bookingId,
    @Nullable String name,
    @Nullable String label) {

  public SeriesConflict {
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    Objects.requireNonNull(kind, "kind");
  }

  /** Que hai ese día. */
  public enum Kind {
    OTHER_USE_EXCLUSIVE,
    OUT_OF_HOURS,
    BLOCK,
    BOOKING,
    SESSION
  }
}
