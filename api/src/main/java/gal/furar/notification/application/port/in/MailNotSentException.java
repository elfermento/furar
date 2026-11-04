// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.in;

/** O correo non se enviou e aínda queda un intento: o evento queda sen completar. */
public class MailNotSentException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public MailNotSentException(String message, Throwable cause) {
    super(message, cause);
  }
}
