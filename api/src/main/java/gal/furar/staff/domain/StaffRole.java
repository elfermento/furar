// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import gal.furar.shared.security.Role;

/** Rol dunha pertenza no seu concello: xestión ou persoal (ADR-0027). */
public enum StaffRole {
  MANAGER(Role.MANAGER),
  STAFF(Role.STAFF);

  private final Role role;

  StaffRole(Role role) {
    this.role = role;
  }

  /** O rol de Furar que dá esta pertenza no concello (ADR-0007). */
  public Role role() {
    return role;
  }
}
