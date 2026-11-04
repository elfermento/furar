// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

/** Escribe o correo (asunto, HTML, texto, QR e {@code .ics}) a partir do seu contido. */
public interface MailComposer {

  OutgoingMail compose(MailContent content);
}
