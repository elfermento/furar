// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.in.event;

import gal.furar.booking.BookingSeriesCreated;
import gal.furar.booking.BookingSeriesEnded;
import gal.furar.booking.BookingSeriesRequestRejected;
import gal.furar.notification.application.port.in.SendSeriesMail;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Escoita as tempadas das entidades despois do commit, nun fío propio e no concello do evento
 * (ADR-0039, punto 12; ADR-0006): un correo á entidade por tempada creada, rematada ou solicitude
 * non aprobada. As sesións non mandan un correo cada unha.
 */
@Component
class SeriesMailListener {

  private final SendSeriesMail mails;

  SeriesMailListener(SendSeriesMail mails) {
    this.mails = mails;
  }

  @Async
  @TransactionalEventListener
  void on(BookingSeriesCreated event) {
    mails.created(event.seriesId());
  }

  @Async
  @TransactionalEventListener
  void on(BookingSeriesEnded event) {
    mails.ended(event.seriesId(), event.from(), event.cancelled(), event.noticeId());
  }

  @Async
  @TransactionalEventListener
  void on(BookingSeriesRequestRejected event) {
    mails.rejected(event.requestId());
  }
}
