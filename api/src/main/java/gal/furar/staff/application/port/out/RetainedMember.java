// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.port.out;

import gal.furar.staff.domain.StaffMemberId;
import gal.furar.staff.domain.StaffRole;
import java.util.Objects;

/**
 * Unha pertenza que a conservación acaba de anonimizar ou desactivar (ADR-0027, punto 14): o que
 * vai ao rexistro de actividade, sen datos persoais.
 *
 * @param id a pertenza
 * @param role o seu rol
 */
public record RetainedMember(StaffMemberId id, StaffRole role) {

  public RetainedMember {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(role, "role");
  }
}
