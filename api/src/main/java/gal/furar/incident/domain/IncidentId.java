// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.domain;

import gal.furar.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

public record IncidentId(UUID value) {

  public IncidentId {
    Objects.requireNonNull(value, "value");
  }

  public static IncidentId newId() {
    return new IncidentId(Ids.newV7());
  }
}
