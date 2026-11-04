// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

import gal.furar.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

public record CustomFieldDefinitionId(UUID value) {

  public CustomFieldDefinitionId {
    Objects.requireNonNull(value, "value");
  }

  public static CustomFieldDefinitionId newId() {
    return new CustomFieldDefinitionId(Ids.newV7());
  }
}
