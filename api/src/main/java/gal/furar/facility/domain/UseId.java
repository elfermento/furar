// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

/** Identidade dun uso da instalación. */
public record UseId(UUID value) {

  public UseId {
    Objects.requireNonNull(value, "value");
  }

  public static UseId newId() {
    return new UseId(Ids.newV7());
  }
}
