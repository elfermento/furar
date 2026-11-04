// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality;

import gal.furar.shared.security.PermissionKey;
import gal.furar.shared.security.Role;
import java.util.Set;

/** Permisos do módulo concello e quen os ten (ADR-0005 ARQ3-2, ADR-0007). */
public enum MunicipalityPermission implements PermissionKey {
  /** Dar de alta concellos (só plataforma). */
  CREATE(Code.CREATE, Set.of(Role.PLATFORM_ADMIN)),
  /**
   * Editar os datos, o tema, os datos legais e os campos personalizados do propio concello. Tamén a
   * plataforma, para deixar o concello configurado (ADR-0028, revisión do 2026-11-03).
   */
  UPDATE(Code.UPDATE, Set.of(Role.MANAGER, Role.PLATFORM_ADMIN));

  /** Códigos, para {@code @RequiresPermission}. */
  public static final class Code {
    public static final String CREATE = "municipality.create";
    public static final String UPDATE = "municipality.update";

    private Code() {}
  }

  private final String code;
  private final Set<Role> roles;

  MunicipalityPermission(String code, Set<Role> roles) {
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
