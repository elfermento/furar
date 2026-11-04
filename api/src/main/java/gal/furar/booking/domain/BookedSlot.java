// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O que ocupa unha reserva: instalación, uso, parte (só por partes) e o intervalo de 1 a N franxas
 * seguidas ({@code [start, end)}, §1.2.6 e §1.2.7). Que o intervalo estea aliñado e dentro dun
 * tramo aberto compróbao quen reserva baixo o bloqueo da instalación.
 */
public record BookedSlot(
    UUID facilityId,
    UUID useId,
    @Nullable UUID partId,
    RentalMode rentalMode,
    Instant start,
    Instant end,
    int slots) {

  public BookedSlot {
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(useId, "useId");
    Objects.requireNonNull(rentalMode, "rentalMode");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    if ((rentalMode == RentalMode.PARTS) != (partId != null)) {
      throw new IllegalArgumentException("Only a booking by parts has a part");
    }
    if (!end.isAfter(start)) {
      throw new IllegalArgumentException("Empty interval: " + start + " - " + end);
    }
    if (slots < 1) {
      throw new IllegalArgumentException("At least one slot: " + slots);
    }
  }

  public TimeWindow period() {
    return new TimeWindow(start, end);
  }
}
