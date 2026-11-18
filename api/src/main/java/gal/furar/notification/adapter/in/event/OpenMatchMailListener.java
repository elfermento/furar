// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.in.event;

import gal.furar.booking.OpenMatchCancelled;
import gal.furar.booking.OpenMatchChanged;
import gal.furar.booking.OpenMatchDecided;
import gal.furar.booking.OpenMatchHidden;
import gal.furar.booking.OpenMatchNotice;
import gal.furar.booking.OpenMatchRequested;
import gal.furar.notification.application.port.in.SendOpenMatchMail;
import java.util.concurrent.Semaphore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Escoita as partidas abertas despois do commit, nun fío propio e no concello do evento (ADR-0040,
 * punto 12; ADR-0006), e escribe a quen toca. Ten o seu propio semáforo, dun só evento á vez: unha
 * partida con moita xente non atrasa as confirmacións das reservas. Unirse directamente non manda
 * correo.
 */
@Component
class OpenMatchMailListener {

  /** Eventos das partidas que se atenden á vez. */
  static final int PARALLEL = 1;

  private final Semaphore slots = new Semaphore(PARALLEL, true);
  private final SendOpenMatchMail mails;

  OpenMatchMailListener(SendOpenMatchMail mails) {
    this.mails = mails;
  }

  @Async
  @TransactionalEventListener
  void on(OpenMatchCancelled event) {
    oneAtATime(
        () -> mails.send(OpenMatchNotice.Kind.CANCELLED, event.matchId(), null, event.noticeId()));
  }

  @Async
  @TransactionalEventListener
  void on(OpenMatchChanged event) {
    oneAtATime(
        () -> mails.send(OpenMatchNotice.Kind.CHANGED, event.matchId(), null, event.noticeId()));
  }

  @Async
  @TransactionalEventListener
  void on(OpenMatchHidden event) {
    oneAtATime(
        () -> mails.send(OpenMatchNotice.Kind.HIDDEN, event.matchId(), null, event.noticeId()));
  }

  @Async
  @TransactionalEventListener
  void on(OpenMatchRequested event) {
    oneAtATime(
        () ->
            mails.send(
                OpenMatchNotice.Kind.REQUESTED,
                event.matchId(),
                event.playerId(),
                event.playerId()));
  }

  @Async
  @TransactionalEventListener
  void on(OpenMatchDecided event) {
    oneAtATime(
        () ->
            mails.send(
                event.accepted() ? OpenMatchNotice.Kind.ACCEPTED : OpenMatchNotice.Kind.REJECTED,
                event.matchId(),
                event.playerId(),
                event.noticeId()));
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
