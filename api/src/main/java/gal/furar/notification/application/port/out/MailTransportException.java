// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

/**
 * O SMTP non aceptou o correo. A mensaxe é curta e sen datos persoais (o tipo do fallo e o código
 * SMTP, nunca o enderezo, o contido nin a ligazón): vai aos rexistros e á táboa dos envíos.
 */
public class MailTransportException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public MailTransportException(String message, Throwable cause) {
    super(message, cause);
  }
}
