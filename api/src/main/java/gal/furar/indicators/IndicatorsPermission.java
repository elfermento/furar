// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators;

import gal.furar.shared.security.PermissionKey;
import gal.furar.shared.security.Role;
import java.util.Set;

/**
 * Permisos dos indicadores e quen os ten (plan do dominio 3, §1.2.14 e P1; ADR-0005 ARQ3-2,
 * ADR-0007). Só agregados, sen datos persoais: sen {@code AuditLog}.
 */
public enum IndicatorsPermission implements PermissionKey {
  /** Ver o panel de indicadores e os seus filtros. */
  VIEW(Code.VIEW, Set.of(Role.MANAGER));

  /** Códigos, para {@code @RequiresPermission}. */
  public static final class Code {
    public static final String VIEW = "indicators.view";

    private Code() {}
  }

  private final String code;
  private final Set<Role> roles;

  IndicatorsPermission(String code, Set<Role> roles) {
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
