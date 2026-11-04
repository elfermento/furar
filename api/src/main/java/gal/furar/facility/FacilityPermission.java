// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import gal.furar.shared.security.PermissionKey;
import gal.furar.shared.security.Role;
import java.util.Set;

/**
 * Permisos do módulo facility e quen os ten (ADR-0005 ARQ3-2, ADR-0007; plan do dominio 1 §2.2):
 * escribe a xestión ({@code MANAGER}) e a plataforma; le a ficha de xestión tamén o persoal ({@code
 * STAFF}).
 */
public enum FacilityPermission implements PermissionKey {
  /**
   * Alta e edición das instalacións (tamén o estado). A plataforma tamén (ADR-0028, revisión do
   * 2026-11-03), pero un cambio que afecte a reservas só o decide quen pode cancelalas.
   */
  MANAGE(Code.MANAGE, Set.of(Role.MANAGER, Role.PLATFORM_ADMIN)),
  /** Ler o listado e a ficha de xestión (con borradores e de baixa). */
  VIEW(Code.VIEW, Set.of(Role.MANAGER, Role.STAFF, Role.PLATFORM_ADMIN));

  /** Códigos, para {@code @RequiresPermission}. */
  public static final class Code {
    public static final String MANAGE = "facility.manage";
    public static final String VIEW = "facility.view";

    private Code() {}
  }

  private final String code;
  private final Set<Role> roles;

  FacilityPermission(String code, Set<Role> roles) {
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
