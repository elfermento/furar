// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event;

import gal.furar.shared.security.PermissionKey;
import gal.furar.shared.security.Role;
import java.util.Set;

/**
 * Permisos dos eventos do concello e quen os ten (ADR-0038; ADR-0005, ADR-0007). O evento é
 * configuración do concello: Xestión e a plataforma dan de alta, editan, publican e cancelan
 * (ADR-0028, revisión do 2026-11-03); Recepción os ve. As persoas apuntadas son datos da veciñanza:
 * a plataforma non as ve (ADR-0028, punto 9); Xestión e Recepción si, e marcan o pago coma nas
 * reservas; aceptar ou rexeitar unha solicitude, só Xestión. A veciñanza apúntase sen permiso
 * ({@code @PersonUseCase}).
 */
public enum EventPermission implements PermissionKey {
  /** Ver a lista e o detalle na xestión (só números de persoas, sen nomes). */
  VIEW(Code.VIEW, Set.of(Role.MANAGER, Role.STAFF, Role.PLATFORM_ADMIN)),
  /** Alta, edición, publicar, cancelar e eliminar. */
  MANAGE(Code.MANAGE, Set.of(Role.MANAGER, Role.PLATFORM_ADMIN)),
  /** Ver as persoas apuntadas e as solicitudes, e o número de solicitudes pendentes. */
  REGISTRATIONS(Code.REGISTRATIONS, Set.of(Role.MANAGER, Role.STAFF)),
  /** Aceptar ou rexeitar unha solicitude («Con aprobación»). */
  REVIEW(Code.REVIEW, Set.of(Role.MANAGER)),
  /** Marcar o pago dunha persoa apuntada, coma no mostrador. */
  PAYMENT(Code.PAYMENT, Set.of(Role.MANAGER, Role.STAFF)),
  /**
   * Pasar lista nas sesións dunha actividade con horario e ver as sesións de hoxe (ADR-0041): coma
   * o pago, Xestión e Recepción; a plataforma non (son persoas).
   */
  ATTENDANCE(Code.ATTENDANCE, Set.of(Role.MANAGER, Role.STAFF));

  /** Códigos, para {@code @RequiresPermission}. */
  public static final class Code {
    public static final String VIEW = "event.view";
    public static final String MANAGE = "event.manage";
    public static final String REGISTRATIONS = "event.registrations";
    public static final String REVIEW = "event.review";
    public static final String PAYMENT = "event.payment";
    public static final String ATTENDANCE = "event.attendance";

    private Code() {}
  }

  private final String code;
  private final Set<Role> roles;

  EventPermission(String code, Set<Role> roles) {
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
