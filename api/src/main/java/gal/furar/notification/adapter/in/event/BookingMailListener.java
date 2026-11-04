// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.in.event;

import gal.furar.booking.BookingCancelled;
import gal.furar.booking.BookingChanged;
import gal.furar.booking.BookingConfirmed;
import gal.furar.notification.application.port.in.BookingMailCommand;
import gal.furar.notification.application.port.in.SendBookingMail;
import gal.furar.notification.domain.MailKind;
import java.util.concurrent.Semaphore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Escoita as reservas despois do commit, nun fío propio e no concello do evento (ADR-0025, punto
 * 15; ADR-0006): confirmada, cambiada, cancelada pola persoa e, se o concello escolleu avisar por
 * correo, cancelada polo concello. Un SMTP caído nunca desfai a reserva; o rexistro de publicacións
 * garda o evento ata que o correo sae (ou se esgotan os intentos).
 */
@Component
class BookingMailListener {

  /** Só hai un correo de alta e un de cancelación por reserva. */
  static final String ONCE = "once";

  /** Correos que se envían á vez. */
  static final int PARALLEL = 2;

  private final Semaphore slots = new Semaphore(PARALLEL, true);
  private final SendBookingMail mails;

  BookingMailListener(SendBookingMail mails) {
    this.mails = mails;
  }

  private void send(BookingMailCommand command) {
    slots.acquireUninterruptibly();
    try {
      mails.send(command);
    } finally {
      slots.release();
    }
  }

  @Async
  @TransactionalEventListener
  void on(BookingConfirmed event) {
    send(new BookingMailCommand(event.bookingId(), MailKind.CONFIRMED, ONCE, null, null));
  }

  @Async
  @TransactionalEventListener
  void on(BookingChanged event) {
    send(
        new BookingMailCommand(
            event.bookingId(),
            MailKind.CHANGED,
            "v" + event.version(),
            event.previousStart(),
            event.previousEnd()));
  }

  @Async
  @TransactionalEventListener
  void on(BookingCancelled event) {
    MailKind kind;
    if (event.by() == BookingCancelled.By.PERSON) {
      kind = MailKind.CANCELLED_BY_PERSON;
    } else if (event.channels().contains(BookingCancelled.Channel.EMAIL)) {
      kind = MailKind.CANCELLED_BY_MUNICIPALITY;
    } else {
      return;
    }
    send(new BookingMailCommand(event.bookingId(), kind, ONCE, null, null));
  }
}
