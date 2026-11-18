// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import gal.furar.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

public record StaffMemberId(UUID value) {

  public StaffMemberId {
    Objects.requireNonNull(value, "value");
  }

  public static StaffMemberId newId() {
    return new StaffMemberId(Ids.newV7());
  }
}
