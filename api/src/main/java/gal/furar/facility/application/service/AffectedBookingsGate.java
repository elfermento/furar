// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.AffectedBooking;
import gal.furar.facility.BookingDecision;
import gal.furar.facility.BookingImpact;
import gal.furar.facility.FacilityChange;
import gal.furar.facility.application.port.in.AffectedBookingsException;
import gal.furar.facility.domain.FacilityError;
import gal.furar.shared.domain.DomainException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * O modal de reservas afectadas en dous pasos sobre a mesma petición (ADR-0022, punto 6). Chámase
 * baixo o bloqueo da instalación, antes de gardar o cambio.
 */
public final class AffectedBookingsGate {

  private final BookingImpact impact;

  public AffectedBookingsGate(BookingImpact impact) {
    this.impact = impact;
  }

  /**
   * Pregunta que reservas afecta o cambio e comproba as decisións. Sen afectadas, as decisións
   * ignóranse. Con afectadas: unha decisión incoherente → 422 {@code facility.decision-invalid}; se
   * as decisións non son exactamente para as afectadas (faltan ou cambiaron) → 409 {@code
   * facility.affected-bookings} coa lista actual.
   *
   * @return as decisións que hai que aplicar (baleiro se non hai afectadas)
   */
  public List<BookingDecision> check(FacilityChange change, List<BookingDecision> decisions) {
    List<AffectedBooking> affected = impact.affectedBy(change);
    if (affected.isEmpty()) {
      return List.of();
    }
    for (int i = 0; i < decisions.size(); i++) {
      BookingDecision decision = decisions.get(i);
      if (!decision.isCoherent()) {
        throw new DomainException(
            FacilityError.DECISION_INVALID, Map.of("bookingId", decision.bookingId(), "index", i));
      }
    }
    Set<UUID> expected = new HashSet<>();
    affected.forEach(a -> expected.add(a.bookingId()));
    Set<UUID> decided = new HashSet<>();
    decisions.forEach(d -> decided.add(d.bookingId()));
    if (decisions.size() != decided.size() || !decided.equals(expected)) {
      throw new AffectedBookingsException(affected);
    }
    return decisions;
  }

  /** Aplica as decisións na mesma transacción ca o cambio (só estado; avisos por evento). */
  public void apply(FacilityChange change, List<BookingDecision> decisions) {
    if (!decisions.isEmpty()) {
      impact.apply(change, decisions);
    }
  }
}
