// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.FreeSlot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Os ocos libres dun uso nun día.
 *
 * @param today hoxe en Europe/Madrid
 * @param now o instante do cálculo
 * @param bookable se se pode reservar por internet (activa e con reserva por internet); falso cando
 *     a instalación só amosa os ocos libres
 */
public record UseAvailability(
    LocalDate date, LocalDate today, Instant now, boolean bookable, List<FreeSlot> slots) {

  public UseAvailability {
    slots = List.copyOf(slots);
  }
}
