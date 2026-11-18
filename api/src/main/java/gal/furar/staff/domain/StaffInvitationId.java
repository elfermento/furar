// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import gal.furar.shared.domain.Ids;
import java.util.Objects;
import java.util.UUID;

public record StaffInvitationId(UUID value) {

  public StaffInvitationId {
    Objects.requireNonNull(value, "value");
  }

  public static StaffInvitationId newId() {
    return new StaffInvitationId(Ids.newV7());
  }
}
