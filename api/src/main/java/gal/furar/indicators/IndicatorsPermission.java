// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators;

import gal.furar.shared.security.PermissionKey;
import gal.furar.shared.security.Role;
import java.util.Set;

/**
 * Permisos dos indicadores e quen os ten (ADR-0026; ADR-0005, ADR-0007). Só agregados, sen datos
 * persoais: sen {@code AuditLog}.
 */
public enum IndicatorsPermission implements PermissionKey {
  /** Ver o panel de indicadores e os seus filtros. */
  VIEW(Code.VIEW, Set.of(Role.MANAGER)),
  /**
   * Os indicadores da plataforma: só cifras de cada concello e os totais, sen datos de ningunha
   * persoa (ADR-0026, revisión do 2026-11-11). Só a administración da plataforma.
   */
  PLATFORM_VIEW(Code.PLATFORM_VIEW, Set.of(Role.PLATFORM_ADMIN));

  /** Códigos, para {@code @RequiresPermission}. */
  public static final class Code {
    public static final String VIEW = "indicators.view";
    public static final String PLATFORM_VIEW = "indicators.platform-view";

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
