// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.domain;

import gal.furar.shared.domain.ErrorCode;

/** Erros de «¿Es un concello?» (ADR-0042). */
public enum JoinRequestError implements ErrorCode {
  /**
   * O servidor de correo non aceptou a solicitude e tampouco se puido gardar para reintentala: hai
   * que envíala outra vez.
   */
  NOT_SENT("notification.join-request-not-sent", 503),
  /** Demasiadas solicitudes desde o mesmo enderezo IP nun día. */
  RATE_LIMITED("notification.join-request-rate-limited", 429);

  private final String code;
  private final int status;

  JoinRequestError(String code, int status) {
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
