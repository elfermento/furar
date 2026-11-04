// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.in.event;

import gal.furar.booking.BookingCancelled;
import gal.furar.booking.BookingChanged;
import gal.furar.booking.BookingConfirmed;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * O reintento dos correos que non saíron (ADR-0025, punto 15): de cando en vez volve entregar os
 * eventos de reservas que quedaron sen completar e teñen xa uns minutos (só os escoita este
 * módulo). O rexistro de envíos fai que cada correo saia unha vez e que un fallido se intente só
 * unha vez máis. Ao arrancar, o rexistro de publicacións xa reenvía todo o pendente.
 */
@Component
class MailRetry {

  /** Os eventos que escoita este módulo (e ningún outro, de momento). */
  private static final Set<Class<?>> MAIL_EVENTS =
      Set.of(BookingConfirmed.class, BookingChanged.class, BookingCancelled.class);

  private final IncompleteEventPublications publications;
  private final Duration after;

  MailRetry(
      IncompleteEventPublications publications,
      @Value("${furar.mail.retry-after:PT5M}") Duration after) {
    this.publications = publications;
    this.after = after;
  }

  @Scheduled(
      initialDelayString = "${furar.mail.retry-every:PT10M}",
      fixedDelayString = "${furar.mail.retry-every:PT10M}")
  void retry() {
    retryOlderThan(after);
  }

  /** Volve entregar os eventos pendentes dos correos publicados hai máis de {@code age}. */
  void retryOlderThan(Duration age) {
    Instant limit = Instant.now().minus(age);
    publications.resubmitIncompletePublications(
        p ->
            MAIL_EVENTS.contains(p.getEvent().getClass())
                && !p.getPublicationDate().isAfter(limit));
  }
}
