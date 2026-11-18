// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import gal.furar.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

public record EventId(UUID value) {

  public EventId {
    Objects.requireNonNull(value, "value");
  }

  public static EventId newId() {
    return new EventId(Ids.newV7());
  }
}
