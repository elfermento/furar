// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

/** Identidade dunha foto. Os bytes non cambian nunca: outra imaxe é outra foto, con outro id. */
public record PhotoId(UUID value) {

  public PhotoId {
    Objects.requireNonNull(value, "value");
  }

  public static PhotoId newId() {
    return new PhotoId(Ids.newV7());
  }
}
