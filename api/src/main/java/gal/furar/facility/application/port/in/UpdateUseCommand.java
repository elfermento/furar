// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.BookingDecision;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.UseId;
import gal.furar.facility.domain.UseSpec;
import java.util.List;

/** Edición enteira dun uso coa versión do uso ({@code If-Match}) e as decisións do modal. */
public record UpdateUseCommand(
    FacilityId facilityId,
    UseId useId,
    long expectedVersion,
    UseSpec spec,
    List<BookingDecision> decisions) {

  public UpdateUseCommand {
    decisions = decisions == null ? List.of() : List.copyOf(decisions);
  }
}
