// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.BookingDecision;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.UseSpec;
import java.util.List;

/**
 * Uso novo. {@code decisions}: baleiro no primeiro paso; se o uso bloquea os demais e hai reservas
 * afectadas, unha por reserva (ADR-0022, punto 6).
 */
public record CreateUseCommand(
    FacilityId facilityId, UseSpec spec, List<BookingDecision> decisions) {

  public CreateUseCommand {
    decisions = decisions == null ? List.of() : List.copyOf(decisions);
  }
}
