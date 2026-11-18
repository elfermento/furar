// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.domain;

import gal.furar.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

public record DependentId(UUID value) {

  public DependentId {
    Objects.requireNonNull(value, "value");
  }

  public static DependentId newId() {
    return new DependentId(Ids.newV7());
  }
}
