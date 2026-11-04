// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.domain;

import gal.furar.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

public record @Name@Id(UUID value) {

  public @Name@Id {
    Objects.requireNonNull(value, "value");
  }

  public static @Name@Id newId() {
    return new @Name@Id(Ids.newV7());
  }
}
