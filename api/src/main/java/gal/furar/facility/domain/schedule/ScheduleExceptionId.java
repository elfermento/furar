// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import gal.furar.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

/** Identidade dunha excepción do horario. */
public record ScheduleExceptionId(UUID value) {

  public ScheduleExceptionId {
    Objects.requireNonNull(value, "value");
  }

  public static ScheduleExceptionId newId() {
    return new ScheduleExceptionId(Ids.newV7());
  }
}
