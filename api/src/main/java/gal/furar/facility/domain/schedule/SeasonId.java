// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import gal.furar.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

/** Identidade dunha tempada do horario. */
public record SeasonId(UUID value) {

  public SeasonId {
    Objects.requireNonNull(value, "value");
  }

  public static SeasonId newId() {
    return new SeasonId(Ids.newV7());
  }
}
