// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in.schedule;

import gal.furar.facility.BookingDecision;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.SeasonId;
import java.util.List;

/** Borrar unha tempada coa súa versión ({@code If-Match}) e as decisións do modal (P6). */
public record DeleteSeasonCommand(
    FacilityId facilityId,
    SeasonId seasonId,
    long expectedVersion,
    List<BookingDecision> decisions) {

  public DeleteSeasonCommand {
    decisions = decisions == null ? List.of() : List.copyOf(decisions);
  }
}
