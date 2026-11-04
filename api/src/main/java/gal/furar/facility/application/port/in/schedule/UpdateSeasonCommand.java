// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in.schedule;

import gal.furar.facility.BookingDecision;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.DayHoursSpec;
import gal.furar.facility.domain.schedule.SeasonId;
import gal.furar.facility.domain.schedule.SeasonSpec;
import java.util.List;

/**
 * Edición enteira dunha tempada coa súa versión ({@code If-Match}): nome, datas e a semana
 * completa, máis as decisións do modal de reservas afectadas (P6).
 */
public record UpdateSeasonCommand(
    FacilityId facilityId,
    SeasonId seasonId,
    long expectedVersion,
    SeasonSpec spec,
    List<DayHoursSpec> week,
    List<BookingDecision> decisions) {

  public UpdateSeasonCommand {
    week = week == null ? List.of() : List.copyOf(week);
    decisions = decisions == null ? List.of() : List.copyOf(decisions);
  }
}
