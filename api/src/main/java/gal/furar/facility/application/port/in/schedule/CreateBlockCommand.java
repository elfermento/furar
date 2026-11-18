// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in.schedule;

import gal.furar.facility.BookingDecision;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.BlockSpec;
import java.util.List;

/**
 * «+ Bloqueo»: data, tramo e motivo, máis as decisións do modal de reservas afectadas (ADR-0022,
 * punto 6).
 */
public record CreateBlockCommand(
    FacilityId facilityId, BlockSpec spec, List<BookingDecision> decisions) {

  public CreateBlockCommand {
    decisions = decisions == null ? List.of() : List.copyOf(decisions);
  }
}
