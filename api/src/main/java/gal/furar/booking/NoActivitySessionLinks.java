// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * {@link ActivitySessionLinks} sen o módulo {@code event}: ningunha sesión. Non é un bean: úsase
 * cando non hai ningún (ADR-0005, «un bean, implementación por defecto»).
 */
public final class NoActivitySessionLinks implements ActivitySessionLinks {

  @Override
  public Map<UUID, UUID> sessionsByBooking(Collection<UUID> bookingIds) {
    return Map.of();
  }
}
