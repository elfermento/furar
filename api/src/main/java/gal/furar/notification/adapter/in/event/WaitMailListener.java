// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.in.event;

import gal.furar.booking.BookingSlotReleased;
import gal.furar.booking.BookingWaitsCancelled;
import gal.furar.notification.application.port.in.SendWaitMail;
import gal.furar.notification.application.port.in.WaitMailCommand;
import java.util.concurrent.Semaphore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Escoita as franxas que quedan libres despois do commit, nun fío propio e no concello do evento
 * (ADR-0033, punto 7; ADR-0006), e avisa a quen esperaba. Ten o seu propio semáforo, dun só envío á
 * vez e collido por correo, non por evento: unha hora con moitas esperas non atrasa as
 * confirmacións, que van polos envíos de {@link BookingMailListener}.
 */
@Component
class WaitMailListener {

  /** Avisos «Quedou libre» que se envían á vez. */
  static final int PARALLEL = 1;

  private final Semaphore slots = new Semaphore(PARALLEL, true);
  private final SendWaitMail mails;

  WaitMailListener(SendWaitMail mails) {
    this.mails = mails;
  }

  @Async
  @TransactionalEventListener
  void on(BookingSlotReleased event) {
    mails.send(
        new WaitMailCommand(event.releaseId(), event.useId(), event.start(), event.releasedAt()),
        this::oneAtATime);
  }

  /** As esperas dunha instalación cancelaronse (ADR-0033, revisión do 2026-11-11). */
  @Async
  @TransactionalEventListener
  void on(BookingWaitsCancelled event) {
    mails.sendCancelled(event.cancellationId(), this::oneAtATime);
  }

  private void oneAtATime(Runnable send) {
    slots.acquireUninterruptibly();
    try {
      send.run();
    } finally {
      slots.release();
    }
  }
}
