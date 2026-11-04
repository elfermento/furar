// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.BookingDecision;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityStatus;
import java.util.List;

/**
 * Cambio de estado. {@code decisions}: baleiro no primeiro paso; no segundo, unha por reserva
 * afectada (ADR-0022, punto 6).
 */
public record ChangeFacilityStatusCommand(
    FacilityId id, long expectedVersion, FacilityStatus status, List<BookingDecision> decisions) {

  public ChangeFacilityStatusCommand {
    decisions = decisions == null ? List.of() : List.copyOf(decisions);
  }
}
