// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.portability;

import gal.furar.shared.security.PermissionKey;
import gal.furar.shared.security.Role;
import java.util.Set;

/** Exportar e importar o concello enteiro (ADR-0018): cada módulo usa estes nas súas seccións. */
public enum PortabilityPermission implements PermissionKey {
  /** Descargar todos os datos do propio concello no formato {@code furar-export}. */
  EXPORT(Code.EXPORT, Set.of(Role.MANAGER)),
  /** Crear un concello desde un ficheiro {@code furar-export} (só plataforma). */
  IMPORT(Code.IMPORT, Set.of(Role.PLATFORM_ADMIN));

  /** Códigos, para {@code @RequiresPermission}. */
  public static final class Code {
    public static final String EXPORT = "municipality.export";
    public static final String IMPORT = "municipality.import";

    private Code() {}
  }

  private final String code;
  private final Set<Role> roles;

  PortabilityPermission(String code, Set<Role> roles) {
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
