// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in.schedule;

import gal.furar.facility.BookingDecision;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.ExceptionSpec;
import java.util.List;

/**
 * «+ Excepción»: data, aberto ou pechado, tramo e motivo, máis as decisións do modal de reservas
 * afectadas se a excepción quita horas (P6).
 */
public record CreateExceptionCommand(
    FacilityId facilityId, ExceptionSpec spec, List<BookingDecision> decisions) {

  public CreateExceptionCommand {
    decisions = decisions == null ? List.of() : List.copyOf(decisions);
  }
}
