// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.season.SeriesSchedule;
import java.util.Objects;
import java.util.UUID;

/** Unha tempada para unha entidade (ADR-0039, punto 8): a entidade e o horario. */
public record SeriesCommand(UUID organizationId, SeriesSchedule schedule) {

  public SeriesCommand {
    Objects.requireNonNull(organizationId, "organizationId");
    Objects.requireNonNull(schedule, "schedule");
  }
}
