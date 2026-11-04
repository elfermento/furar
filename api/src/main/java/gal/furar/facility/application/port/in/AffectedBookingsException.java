// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.AffectedBooking;
import gal.furar.facility.domain.FacilityError;
import gal.furar.shared.domain.DomainException;
import java.util.List;
import java.util.Map;

/**
 * 409 {@code facility.affected-bookings}: hai reservas afectadas sen decisión, ou cambiaron desde o
 * primeiro paso. O adaptador web engade {@code affected[]} ao problema (ADR-0022, punto 6).
 */
public class AffectedBookingsException extends DomainException {

  private static final long serialVersionUID = 1L;

  private final transient List<AffectedBooking> affected;

  public AffectedBookingsException(List<AffectedBooking> affected) {
    super(FacilityError.AFFECTED_BOOKINGS, Map.of("count", affected.size()));
    this.affected = List.copyOf(affected);
  }

  public List<AffectedBooking> affected() {
    return affected;
  }
}
