// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

public record FacilityId(UUID value) {

  public FacilityId {
    Objects.requireNonNull(value, "value");
  }

  public static FacilityId newId() {
    return new FacilityId(Ids.newV7());
  }
}
