// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import gal.furar.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

/** Identidade dun bloqueo. */
public record BlockId(UUID value) {

  public BlockId {
    Objects.requireNonNull(value, "value");
  }

  public static BlockId newId() {
    return new BlockId(Ids.newV7());
  }
}
