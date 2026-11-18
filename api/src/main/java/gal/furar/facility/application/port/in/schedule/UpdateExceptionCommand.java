// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in.schedule;

import gal.furar.facility.BookingDecision;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.ExceptionSpec;
import gal.furar.facility.domain.schedule.ScheduleExceptionId;
import java.util.List;

/** Edición enteira dunha excepción coa súa versión ({@code If-Match}) e as decisións do modal. */
public record UpdateExceptionCommand(
    FacilityId facilityId,
    ScheduleExceptionId exceptionId,
    long expectedVersion,
    ExceptionSpec spec,
    List<BookingDecision> decisions) {

  public UpdateExceptionCommand {
    decisions = decisions == null ? List.of() : List.copyOf(decisions);
  }
}
