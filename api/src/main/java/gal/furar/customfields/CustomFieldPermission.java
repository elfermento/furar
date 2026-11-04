// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields;

import gal.furar.shared.security.PermissionKey;
import gal.furar.shared.security.Role;
import java.util.Set;

/** Permisos do módulo de campos personalizados e quen os ten (ADR-0005 ARQ3-2, ADR-0007). */
public enum CustomFieldPermission implements PermissionKey {
  /** Definir, editar e arquivar campos personalizados. */
  MANAGE(Code.MANAGE, Set.of(Role.MANAGER, Role.PLATFORM_ADMIN));

  /** Códigos, para {@code @RequiresPermission}. */
  public static final class Code {
    public static final String MANAGE = "customfields.manage";

    private Code() {}
  }

  private final String code;
  private final Set<Role> roles;

  CustomFieldPermission(String code, Set<Role> roles) {
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
