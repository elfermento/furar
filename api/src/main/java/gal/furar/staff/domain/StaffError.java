// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import gal.furar.shared.domain.ErrorCode;

/**
 * Erros do persoal (plan do dominio 4, §2.3). Cada código ten texto en {@code
 * messages/staff_*.properties} e en {@code gl.json}/{@code es.json}. O correo sen verificar ao
 * aceptar é {@code auth.email-not-verified}, de {@code AuthError}.
 */
public enum StaffError implements ErrorCode {
  NOT_FOUND("staff.not-found", 404),
  /**
   * Xa hai unha pertenza non desactivada con ese correo, ou a persoa xa é activa no concello (ao
   * invitar, aceptar ou reactivar).
   */
  ALREADY_MEMBER("staff.already-member", 409),
  /** Baixar de rol ou desactivar a última persoa xestora activa. */
  LAST_MANAGER("staff.last-manager", 409),
  /**
   * A transición non vale desde o estado actual (p. ex., cambiar o rol dunha desactivada ou
   * reactivar unha que nunca aceptou).
   */
  TRANSITION_INVALID("staff.transition-invalid", 409),
  INVITATION_RATE_LIMITED("staff.invitation-rate-limited", 429),
  /** Token descoñecido, usado, substituído ou doutro concello: a mesma resposta para todos. */
  INVITATION_INVALID("staff.invitation-invalid", 404),
  INVITATION_EXPIRED("staff.invitation-expired", 410),
  /** O correo verificado da sesión non é o invitado. */
  INVITATION_EMAIL_MISMATCH("staff.invitation-email-mismatch", 403),
  /**
   * Quen acepta ten {@code PLATFORM_ADMIN} (aínda que estea retido): a administración da plataforma
   * non é persoal dun concello (ADR-0007, op.acc.3); a persoa acepta cunha conta propia.
   */
  INVITATION_PLATFORM_ACCOUNT("staff.invitation-platform-account", 403),
  /** Quen acepta é quen fixo a invitación: ninguén se dá de alta a si mesmo (op.acc.3). */
  INVITATION_OWN("staff.invitation-own", 403),
  /** O correo para invitar non ten forma de correo (3–254 caracteres, cun @ e sen espazos). */
  EMAIL_INVALID("staff.email-invalid", 422),
  /**
   * A conta que acepta xa reservou como veciña, nalgún concello e en calquera estado: a conta de
   * traballo vai á parte da persoal (especificación, bloque 7, P1). Como a sesión xa é do correo
   * invitado, a persoa ten que ser invitada co correo do traballo.
   */
  INVITATION_PERSONAL_ACCOUNT("staff.invitation-personal-account", 403),
  /**
   * Reactivar unha pertenza cuxa conta reservou como veciña mentres estaba desactivada: xa é unha
   * conta persoal. A persoa invítase de novo, co correo do traballo.
   */
  PERSONAL_ACCOUNT("staff.personal-account", 409),
  /** Reactivar unha pertenza xa anonimizada: non queda de quen era. */
  ANONYMISED("staff.anonymised", 409);

  private final String code;
  private final int status;

  StaffError(String code, int status) {
    this.code = code;
    this.status = status;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public int status() {
    return status;
  }
}
