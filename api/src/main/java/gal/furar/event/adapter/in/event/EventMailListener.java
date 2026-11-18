// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.event;

import gal.furar.booking.BookingCancelled;
import gal.furar.event.EventCancelled;
import gal.furar.event.EventEndedEarly;
import gal.furar.event.EventMoved;
import gal.furar.event.EventSessionCancelled;
import gal.furar.event.EventWaitlistNotified;
import gal.furar.event.RegistrationDecided;
import gal.furar.event.application.port.in.SendEventMails;
import gal.furar.event.application.port.in.SyncActivityBookings;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Escoita os eventos despois do commit, nun fío propio e no concello do evento (ADR-0006,
 * ADR-0038): unha solicitude decidida avisa á persoa; un evento que cambia de data, hora ou lugar
 * ou se cancela, a cada persoa apuntada. Desde ADR-0041, tamén o aviso da lista de espera, a sesión
 * cancelada e a actividade que remata antes; e, se a reserva dunha sesión se cancela nas reservas,
 * a sesión. Un SMTP caído nunca desfai o cambio: o envío queda rexistrado como fallido.
 */
@Component
class EventMailListener {

  private final SendEventMails mails;
  private final SyncActivityBookings sync;

  EventMailListener(SendEventMails mails, SyncActivityBookings sync) {
    this.mails = mails;
    this.sync = sync;
  }

  @Async
  @TransactionalEventListener
  void on(EventWaitlistNotified event) {
    mails.waitlistNotified(event.registrationId());
  }

  @Async
  @TransactionalEventListener
  void on(EventSessionCancelled event) {
    mails.sessionCancelled(event.eventId(), event.sessionId());
  }

  @Async
  @TransactionalEventListener
  void on(EventEndedEarly event) {
    mails.endedEarly(event.eventId(), event.changeId(), event.from());
  }

  /** A reserva dunha sesión cancelouse nas reservas: a sesión tamén. */
  @Async
  @TransactionalEventListener
  void on(BookingCancelled event) {
    sync.bookingCancelled(event.bookingId());
  }

  @Async
  @TransactionalEventListener
  void on(RegistrationDecided event) {
    mails.decided(event.registrationId());
  }

  @Async
  @TransactionalEventListener
  void on(EventMoved event) {
    mails.moved(event.eventId(), event.changeId());
  }

  @Async
  @TransactionalEventListener
  void on(EventCancelled event) {
    mails.cancelled(event.eventId());
  }
}
