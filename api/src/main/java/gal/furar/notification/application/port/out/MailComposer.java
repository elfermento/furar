// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

/** Escribe o correo (asunto, HTML, texto, QR e {@code .ics}) a partir do seu contido. */
public interface MailComposer {

  OutgoingMail compose(MailContent content);

  /** O correo «Quedou libre» (ADR-0033, punto 11): mesma plantilla, sen saúdo, QR nin adxunto. */
  OutgoingMail compose(WaitMailContent content);

  /** «Lista de espera cancelada» (ADR-0033, revisión do 2026-11-11): mesma plantilla, sen QR. */
  OutgoingMail compose(CancelledWaitsMailContent content);
}
