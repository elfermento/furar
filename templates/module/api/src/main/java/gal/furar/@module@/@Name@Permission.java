// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@;

import gal.furar.shared.security.PermissionKey;
import gal.furar.shared.security.Role;
import java.util.Set;

/**
 * Permisos do módulo @module@ e quen os ten (ADR-0005 ARQ3-2, ADR-0007). Decide aquí a fila de
 * roles (o xerado: só o xestor do concello; a plataforma non ve datos dos concellos) e, se lle dás
 * outro rol, engade o código a {@code RolePermissionsTest.expectedMatrix}.
 */
public enum @Name@Permission implements PermissionKey {
  /** Alta e edición en @module@. */
  MANAGE(Code.MANAGE, Set.of(Role.MANAGER));

  /** Códigos, para {@code @RequiresPermission}. */
  public static final class Code {
    public static final String MANAGE = "@module@.manage";

    private Code() {}
  }

  private final String code;
  private final Set<Role> roles;

  @Name@Permission(String code, Set<Role> roles) {
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
