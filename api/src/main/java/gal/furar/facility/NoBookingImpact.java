// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import java.util.List;
import java.util.UUID;

/**
 * {@link BookingImpact} do dominio 1: sen reservas non hai afectadas. Non é un bean: úsase cando
 * non hai ningún (ADR-0005, «un bean, implementación por defecto»).
 */
public final class NoBookingImpact implements BookingImpact {

  @Override
  public List<AffectedBooking> affectedBy(FacilityChange change) {
    return List.of();
  }

  @Override
  public void apply(FacilityChange change, List<BookingDecision> decisions) {
    // Sen reservas, nada que aplicar.
  }

  @Override
  public boolean hasBookings(UUID facilityId) {
    return false;
  }
}
