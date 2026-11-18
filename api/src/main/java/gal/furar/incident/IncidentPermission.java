// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident;

import gal.furar.shared.security.PermissionKey;
import gal.furar.shared.security.Role;
import java.util.Set;

/**
 * Permisos das incidencias e quen os ten (ADR-0036; ADR-0005, ADR-0007): Xestión e Recepción ven a
 * lista e cambian os estados; tamén anotan unha incidencia. A plataforma non: son datos da
 * veciñanza (ADR-0028, punto 9). A veciñanza avisa sen permiso ({@code @PersonUseCase}).
 */
public enum IncidentPermission implements PermissionKey {
  /** Ver a lista, o detalle, as fotos e o número de novas. */
  VIEW(Code.VIEW, Set.of(Role.MANAGER, Role.STAFF)),
  /** Cambiar o estado e a nota, resolver e anotar unha incidencia («Nova incidencia»). */
  MANAGE(Code.MANAGE, Set.of(Role.MANAGER, Role.STAFF)),
  /** Eliminar unha incidencia ou unha das súas fotos: só a xestión. */
  DELETE(Code.DELETE, Set.of(Role.MANAGER));

  /** Códigos, para {@code @RequiresPermission}. */
  public static final class Code {
    public static final String VIEW = "incident.view";
    public static final String MANAGE = "incident.manage";
    public static final String DELETE = "incident.delete";

    private Code() {}
  }

  private final String code;
  private final Set<Role> roles;

  IncidentPermission(String code, Set<Role> roles) {
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
