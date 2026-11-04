// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.FreeSlot;
import gal.furar.shared.domain.DomainException;
import java.util.List;

/**
 * 409 {@code booking.slot-taken} coas tres horas libres máis próximas do mesmo uso e día (ADR-0025,
 * punto 9): a web ofréceas sen volver consultar.
 */
public class SlotTakenException extends DomainException {

  private static final long serialVersionUID = 1L;

  private final transient List<FreeSlot> alternatives;

  public SlotTakenException(List<FreeSlot> alternatives) {
    super(BookingError.SLOT_TAKEN);
    this.alternatives = List.copyOf(alternatives);
  }

  public List<FreeSlot> alternatives() {
    return alternatives;
  }
}
