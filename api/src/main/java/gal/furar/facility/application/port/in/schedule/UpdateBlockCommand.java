// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in.schedule;

import gal.furar.facility.BookingDecision;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.BlockId;
import gal.furar.facility.domain.schedule.BlockSpec;
import java.util.List;

/** Edición enteira dun bloqueo coa súa versión ({@code If-Match}) e as decisións do modal. */
public record UpdateBlockCommand(
    FacilityId facilityId,
    BlockId blockId,
    long expectedVersion,
    BlockSpec spec,
    List<BookingDecision> decisions) {

  public UpdateBlockCommand {
    decisions = decisions == null ? List.of() : List.copyOf(decisions);
  }
}
