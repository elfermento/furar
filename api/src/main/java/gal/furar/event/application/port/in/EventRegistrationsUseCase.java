// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.port.in;

import gal.furar.event.domain.EventId;
import gal.furar.event.domain.PaymentStatus;
import gal.furar.event.domain.Registration;
import gal.furar.event.domain.RegistrationId;
import java.util.List;

/**
 * As persoas dun evento na xestión: Xestión e Recepción ven quen está apuntado e marcan o pago; só
 * Xestión acepta ou rexeita as solicitudes. A plataforma non (datos da veciñanza, ADR-0028).
 */
public interface EventRegistrationsUseCase {

  /** Todas as inscricións do evento, pola orde en que chegaron. */
  List<Registration> list(EventId id);

  /** Acepta unha solicitude se queda praza ({@code event.full}) e avisa á persoa por correo. */
  Registration accept(EventId id, RegistrationId registration);

  /** Rexeita unha solicitude e avisa á persoa por correo. */
  Registration reject(EventId id, RegistrationId registration);

  Registration pay(EventId id, RegistrationId registration, PaymentStatus status);

  /** Solicitudes pendentes nos eventos que aínda non empezaron: o número da pestana «Eventos». */
  long pendingCount();
}
