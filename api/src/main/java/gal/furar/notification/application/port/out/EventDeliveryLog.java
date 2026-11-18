// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

import gal.furar.notification.domain.EventMailDelivery;
import java.util.Set;
import java.util.UUID;

/**
 * Os envíos dos correos dos eventos (ADR-0038) en {@code notification_delivery}, no concello
 * actual. A conservación xeral dos envíos tamén os borra.
 */
public interface EventDeliveryLog {

  /** As claves dos correos dese tipo dese evento que xa se rexistraron. */
  Set<String> keys(UUID eventId, EventMailDelivery.Kind kind);

  /** Garda o resultado nunha transacción propia e curta. */
  void record(EventMailDelivery delivery);
}
