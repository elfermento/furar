// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Os correos dos eventos (ADR-0038), despois do commit e fóra de toda transacción, no concello do
 * evento.
 */
public interface SendEventMails {

  /** Á persoa: o concello aceptou ou rexeitou a súa solicitude. */
  void decided(UUID registrationId);

  /** A cada persoa apuntada ou pedindo praza: o evento cambiou de data, hora ou lugar. */
  void moved(UUID eventId, UUID changeId);

  /** A cada persoa apuntada ou pedindo praza: o evento cancelouse. */
  void cancelled(UUID eventId);

  /**
   * Á persoa da lista de espera avisada dunha praza libre (ADR-0041), se o aviso xa corre e o
   * correo aínda non saíu; despois márcao como enviado.
   */
  void waitlistNotified(UUID registrationId);

  /** A cada persoa con praza: cancelouse unha sesión da actividade. */
  void sessionCancelled(UUID eventId, UUID sessionId);

  /** A cada persoa con praza: a actividade remata antes. */
  void endedEarly(UUID eventId, UUID changeId, LocalDate from);
}
