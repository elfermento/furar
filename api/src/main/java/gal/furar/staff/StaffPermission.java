// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff;

import gal.furar.shared.security.PermissionKey;
import gal.furar.shared.security.Role;
import java.util.Set;

/**
 * Permisos da xestión do persoal e quen os ten (ADR-0027; plan do dominio 4, §1.2.10 e §1.2.11;
 * ADR-0005 ARQ3-2, ADR-0007). A plataforma fai co persoal o mesmo ca o xestor (revisión do
 * 2026-11-02): invita con calquera rol, cambia o rol, desactiva e reactiva a calquera persoa. O
 * persoal (STAFF) non ten ningún.
 */
public enum StaffPermission implements PermissionKey {
  /** Ver a lista e o detalle das pertenzas. */
  VIEW(Code.VIEW, Set.of(Role.MANAGER, Role.PLATFORM_ADMIN)),
  /** Invitar e reenviar a invitación. */
  INVITE(Code.INVITE, Set.of(Role.MANAGER, Role.PLATFORM_ADMIN)),
  /** Cambiar o rol. */
  MANAGE(Code.MANAGE, Set.of(Role.MANAGER, Role.PLATFORM_ADMIN)),
  /** Desactivar e reactivar (ADR-0027, revisión do 2026-10-30). */
  DEACTIVATE(Code.DEACTIVATE, Set.of(Role.MANAGER, Role.PLATFORM_ADMIN));

  /** Códigos, para {@code @RequiresPermission}. */
  public static final class Code {
    public static final String VIEW = "staff.view";
    public static final String INVITE = "staff.invite";
    public static final String MANAGE = "staff.manage";
    public static final String DEACTIVATE = "staff.deactivate";

    private Code() {}
  }

  private final String code;
  private final Set<Role> roles;

  StaffPermission(String code, Set<Role> roles) {
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
