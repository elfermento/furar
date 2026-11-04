// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un uso tal como o mira a grella dos ocos: como se aluga, canto dura cada franxa, as datas e os
 * días da semana nos que vale, se bloquea os demais e as súas normas. Vén de {@code facility}
 * ({@code FacilityQueries.use}); aquí só se le.
 *
 * @param capacity prazas, só con {@link RentalMode#SEATS}
 * @param parts as partes vivas na súa orde, só con {@link RentalMode#PARTS}
 * @param periods os rangos de datas nos que vale; baleira = sempre
 * @param weekdays máscara de días da semana (luns = 1 … domingo = 64)
 */
public record BookableUse(
    UUID id,
    RentalMode rentalMode,
    int slotMinutes,
    @Nullable Integer capacity,
    List<UUID> parts,
    List<Period> periods,
    int weekdays,
    boolean archived,
    boolean blocksOthers,
    BookingRules rules) {

  public BookableUse {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(rentalMode, "rentalMode");
    Objects.requireNonNull(rules, "rules");
    parts = List.copyOf(parts);
    periods = List.copyOf(periods);
    if (slotMinutes < 1) {
      throw new IllegalArgumentException("Slot minutes: " + slotMinutes);
    }
    if ((rentalMode == RentalMode.SEATS) != (capacity != null)) {
      throw new IllegalArgumentException("Only a use by seats has a capacity");
    }
  }

  /** Se vale ese día: vivo, nas súas datas e nun dos seus días da semana. */
  public boolean availableOn(LocalDate date) {
    if (archived || (weekdays & (1 << (date.getDayOfWeek().getValue() - 1))) == 0) {
      return false;
    }
    return periods.isEmpty() || periods.stream().anyMatch(p -> p.contains(date));
  }

  /** Rango de datas cos dous extremos incluídos. */
  public record Period(LocalDate from, LocalDate to) {

    public Period {
      Objects.requireNonNull(from, "from");
      Objects.requireNonNull(to, "to");
    }

    boolean contains(LocalDate date) {
      return !date.isBefore(from) && !date.isAfter(to);
    }
  }
}
