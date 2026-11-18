// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A hora pedida á que se buscan ocos parecidos (ADR-0033, punto 9).
 *
 * @param date o día en Europe/Madrid
 * @param start inicio real
 * @param startMinute o inicio no reloxo de parede de Europe/Madrid (0–1439)
 * @param slotMinutes canto dura unha franxa dese uso: a anterior e a seguinte quedan a esa
 *     distancia
 */
public record SlotTarget(
    UUID facilityId, UUID useId, LocalDate date, Instant start, int startMinute, int slotMinutes) {

  public SlotTarget {
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(useId, "useId");
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(start, "start");
    if (slotMinutes < 1) {
      throw new IllegalArgumentException("Slot minutes: " + slotMinutes);
    }
  }

  /** A hora pedida dese uso, co día e o minuto calculados en Europe/Madrid. */
  public static SlotTarget of(UUID facilityId, UUID useId, Instant start, int slotMinutes) {
    return new SlotTarget(
        facilityId,
        useId,
        start.atZone(SlotGrid.ZONE).toLocalDate(),
        start,
        SlotGrid.wallMinute(start),
        slotMinutes);
  }
}
