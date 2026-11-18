// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Os ocos libres dun día nas instalacións dunha páxina de concellos da portada común (ADR-0030).
 *
 * @param today hoxe en Europe/Madrid
 * @param now o instante do cálculo
 * @param hasNext se quedan concellos por ler despois desta páxina
 * @param items as instalacións que se reservan por internet dos concellos da páxina
 */
public record CatalogAvailability(
    LocalDate date,
    LocalDate today,
    Instant now,
    int page,
    int size,
    boolean hasNext,
    List<FacilitiesAvailability.Item> items) {

  public CatalogAvailability {
    items = List.copyOf(items);
  }
}
