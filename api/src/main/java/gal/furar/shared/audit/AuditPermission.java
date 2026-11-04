// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.audit;

import gal.furar.shared.security.PermissionKey;
import gal.furar.shared.security.Role;
import java.util.Set;

/**
 * Permisos de lectura do rexistro de actividade (ADR-0020, ADR-0027; plan do dominio 4, §1.2.11 e
 * §1.2.12). O xestor le todo o do seu concello, tamén o que fixo a plataforma; a plataforma le só
 * as súas accións e as que non teñen concello.
 */
public enum AuditPermission implements PermissionKey {
  /** Ler o rexistro do concello (a plataforma tamén: ADR-0028, revisión do 2026-11-03). */
  VIEW(Code.VIEW, Set.of(Role.MANAGER, Role.PLATFORM_ADMIN)),
  /**
   * Exportar os eventos bloqueados do concello cun motivo (LOPDGDD art. 32; ADR-0020, revisión do
   * 2026-10-29). Só o xestor: a plataforma non os exporta.
   */
  EXPORT_BLOCKED(Code.EXPORT_BLOCKED, Set.of(Role.MANAGER)),
  /** Ler o rexistro da plataforma. */
  PLATFORM_VIEW(Code.PLATFORM_VIEW, Set.of(Role.PLATFORM_ADMIN));

  /** Códigos, para {@code @RequiresPermission}. */
  public static final class Code {
    public static final String VIEW = "audit.view";
    public static final String EXPORT_BLOCKED = "audit.export-blocked";
    public static final String PLATFORM_VIEW = "audit.platform-view";

    private Code() {}
  }

  private final String code;
  private final Set<Role> roles;

  AuditPermission(String code, Set<Role> roles) {
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
