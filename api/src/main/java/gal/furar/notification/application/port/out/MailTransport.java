// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

/** Envía un correo polo SMTP do concello. */
public interface MailTransport {

  /**
   * @throws MailTransportException se o servidor non o aceptou ou non hai servidor
   */
  void send(OutgoingMail mail);
}
