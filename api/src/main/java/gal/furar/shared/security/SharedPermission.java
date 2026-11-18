// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import java.util.Set;

/** Permisos transversais, que usa {@code shared} (ADR-0007). */
public enum SharedPermission implements PermissionKey {
  /** Ver datos de uso interno do persoal (campos con visibilidade {@code STAFF}). */
  STAFF_DATA_VIEW(Code.STAFF_DATA_VIEW, Set.of(Role.MANAGER, Role.STAFF));

  /** Códigos, para {@link RequiresPermission}. */
  public static final class Code {
    public static final String STAFF_DATA_VIEW = "staff-data.view";

    private Code() {}
  }

  private final String code;
  private final Set<Role> roles;

  SharedPermission(String code, Set<Role> roles) {
    this.code = code;
    this.roles = roles;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public Set<Role> roles() {
    return roles;
  }
}
