// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.season.SeriesSchedule;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A tempada que pide unha entidade (ADR-0039, punto 10), cunha nota. */
public record SeriesRequestCommand(SeriesSchedule schedule, @Nullable String note) {

  public SeriesRequestCommand {
    Objects.requireNonNull(schedule, "schedule");
  }
}
