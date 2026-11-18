// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha ocupación activa dunha instalación (ADR-0012): o uso, a parte (só por partes) e o intervalo
 * {@code [start, end)}. Nas prazas, as que ocupa a súa reserva ({@code seats}).
 */
public record Occupied(UUID useId, @Nullable UUID partId, Instant start, Instant end, int seats) {

  /** Unha soa praza. */
  public Occupied(UUID useId, @Nullable UUID partId, Instant start, Instant end) {
    this(useId, partId, start, end, 1);
  }

  public Occupied {
    Objects.requireNonNull(useId, "useId");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
  }

  boolean overlaps(Instant from, Instant to) {
    return start.isBefore(to) && end.isAfter(from);
  }
}
