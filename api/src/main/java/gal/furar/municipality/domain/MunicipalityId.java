// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import gal.furar.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

public record MunicipalityId(UUID value) {

  public MunicipalityId {
    Objects.requireNonNull(value, "value");
  }

  public static MunicipalityId newId() {
    return new MunicipalityId(Ids.newV7());
  }
}
