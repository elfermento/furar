// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import gal.furar.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

public record RegistrationId(UUID value) {

  public RegistrationId {
    Objects.requireNonNull(value, "value");
  }

  public static RegistrationId newId() {
    return new RegistrationId(Ids.newV7());
  }
}
