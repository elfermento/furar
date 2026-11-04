// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Os ocos libres dunha páxina de instalacións nun día (portada e listado).
 *
 * @param today hoxe en Europe/Madrid
 * @param now o instante do cálculo
 * @param items na orde da páxina; as pechadas temporalmente, sen ocos
 */
public record FacilitiesAvailability(
    LocalDate date, LocalDate today, Instant now, int page, int size, List<Item> items) {

  public FacilitiesAvailability {
    items = List.copyOf(items);
  }

  /**
   * Unha instalación.
   *
   * @param freeCount as horas distintas con algún uso libre
   * @param firstStarts as tres primeiras desas horas
   */
  public record Item(UUID facilityId, int freeCount, List<Start> firstStarts) {

    public Item {
      firstStarts = List.copyOf(firstStarts);
    }
  }

  /** Unha hora de inicio, real e no reloxo de parede de Europe/Madrid. */
  public record Start(Instant start, int startMinute) {}
}
