// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import gal.furar.shared.domain.ErrorCode;

/** Erros de autenticación e autorización. */
public enum AuthError implements ErrorCode {
  UNAUTHENTICATED("auth.unauthenticated", 401),
  FORBIDDEN("auth.forbidden", 403),
  /**
   * Caso de uso da persoa ({@link PersonUseCase}) co correo sen verificar no token (ADR-0025, punto
   * 3). Non é só de reservas: tamén o pide aceptar unha invitación do persoal.
   */
  EMAIL_NOT_VERIFIED("auth.email-not-verified", 403);

  private final String code;
  private final int status;

  AuthError(String code, int status) {
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
