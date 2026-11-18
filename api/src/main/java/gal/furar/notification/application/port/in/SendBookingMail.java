// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.in;

/**
 * Envía o correo dunha reserva á persoa que a fixo, no seu idioma, unha soa vez (ADR-0025, punto
 * 15). Non é un caso de uso de ninguén: chámao a escoita dos eventos de {@code booking}, despois do
 * commit e no concello do evento. Se o envío falla e queda un intento, lanza {@link
 * MailNotSentException} para que o rexistro de publicacións o volva intentar.
 */
public interface SendBookingMail {

  void send(BookingMailCommand command);
}
