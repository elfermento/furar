// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.in;

import java.util.UUID;

/**
 * Avisa por correo, a todas á vez, ás persoas que esperaban por unha franxa que quedou libre
 * (ADR-0033, punto 7). Non é un caso de uso de ninguén: chámao a escoita do evento de {@code
 * booking}, despois do commit e no concello do evento. Cada destinatario vai por separado: un envío
 * que falla non corta os demais; se a algún lle queda un intento, ao rematar lanza {@link
 * MailNotSentException} para que o rexistro de publicacións volva entregar o evento.
 */
public interface SendWaitMail {

  /**
   * @param turn por onde pasa cada envío: quen chama decide cantos saen á vez
   */
  void send(WaitMailCommand command, Turn turn);

  /**
   * «Lista de espera cancelada» (ADR-0033, revisión do 2026-11-11): un correo por persoa con todas
   * as súas esperas canceladas en {@code cancellationId}. Coas mesmas regras de envío.
   */
  void sendCancelled(UUID cancellationId, Turn turn);

  /** A quenda dun envío. */
  @FunctionalInterface
  interface Turn {

    void take(Runnable send);
  }
}
