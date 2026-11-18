// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.service;

import gal.furar.event.EventWaitlistNotified;
import gal.furar.event.application.port.out.RegistrationRepository;
import gal.furar.event.application.port.out.RegistrationRepository.Counts;
import gal.furar.event.domain.Event;
import gal.furar.event.domain.EventSchedule;
import gal.furar.event.domain.Registration;
import gal.furar.event.domain.RegistrationStatus;
import gal.furar.event.domain.WaitlistClock;
import gal.furar.event.domain.WaitlistNotice;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * A lista de espera automática dos eventos (ADR-0041), por orde de chegada. Cando queda unha praza
 * libre avísase á primeira persoa que espera e, durante {@code
 * furar.event.waitlist-exclusive-duration} (PT1H, sen contar a noite: {@code
 * furar.event.waitlist-quiet-from}–{@code -to}, 22:00–8:00, {@link WaitlistClock}), só o sabe ela.
 * Pasada esa hora, se a praza segue libre, avísase tamén á seguinte, e así cada hora. Ninguén perde
 * o seu aviso: calquera avisada confirma mentres a praza siga libre e lévaa a primeira que o faga.
 * Cando se cubren todas as prazas, as demais avisadas volven a esperar no seu posto.
 *
 * <p>A regra, nun só invariante: mentres haxa N prazas libres, hai N persoas (ou todas as que
 * esperan, se son menos) dentro da súa hora de exclusividade. Así, con varias prazas avísase a
 * tantas como prazas; se unha avisada sae dentro da súa hora, avísase de inmediato á seguinte; e
 * cando pasa a hora, á seguinte quenda. Quen chama xa ten o bloqueo da fila do evento.
 */
@Component
class Waitlist {

  /** Esperas activas como moito por conta e concello, coma as das reservas (ADR-0033). */
  static final int MAX_WAITS = 3;

  private final RegistrationRepository registrations;
  private final ApplicationEventPublisher events;
  private final WaitlistClock clock;

  @Autowired
  Waitlist(
      RegistrationRepository registrations,
      ApplicationEventPublisher events,
      @Value("${furar.event.waitlist-exclusive-duration:PT1H}") Duration exclusive,
      @Value("${furar.event.waitlist-quiet-from:22:00}") String quietFrom,
      @Value("${furar.event.waitlist-quiet-to:08:00}") String quietTo) {
    this(
        registrations,
        events,
        new WaitlistClock(
            exclusive, LocalTime.parse(quietFrom), LocalTime.parse(quietTo), EventSchedule.ZONE));
  }

  Waitlist(
      RegistrationRepository registrations, ApplicationEventPublisher events, WaitlistClock clock) {
    this.registrations = registrations;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Pon a lista ao día: sen prazas libres, as avisadas volven a esperar; con prazas libres, avisa
   * ás seguintes ata ter tantas dentro da súa hora como prazas. Nada se o evento leva aprobación ou
   * xa non admite inscricións. Idempotente.
   *
   * @return as inscricións avisadas agora
   */
  List<Registration> fill(Event event, Instant now) {
    if (event.details().approval() || !event.open(now)) {
      return List.of();
    }
    Counts counts =
        registrations.counts(List.of(event.id())).getOrDefault(event.id().value(), Counts.NONE);
    if (counts.waitlisted() == 0) {
      return List.of();
    }
    Integer capacity = event.details().capacity();
    long free = capacity == null ? Long.MAX_VALUE : (long) capacity - counts.confirmed();
    List<Registration> line = registrations.waitlist(event.id());
    if (free <= 0) {
      for (Registration r : line) {
        if (r.status() == RegistrationStatus.NOTIFIED) {
          registrations.update(r.covered(now));
        }
      }
      return List.of();
    }
    long exclusive =
        line.stream()
            .filter(r -> r.status() == RegistrationStatus.NOTIFIED)
            .filter(r -> r.notice() != null && r.notice().exclusive(now))
            .count();
    List<Registration> notified = new ArrayList<>();
    for (Registration r : line) {
      if (exclusive + notified.size() >= free) {
        break;
      }
      if (r.status() != RegistrationStatus.WAITING) {
        continue;
      }
      WaitlistNotice notice = clock.notice(now);
      Registration saved = registrations.update(r.notified(notice, now));
      notified.add(saved);
      if (!notice.notifiedAt().isAfter(now)) {
        events.publishEvent(
            new EventWaitlistNotified(TenantContext.require().id(), saved.id().value()));
      }
    }
    return notified;
  }

  /** O posto na lista de espera do evento (1 = a primeira); 0 fóra dela. */
  int position(Registration registration) {
    int position = 0;
    for (Registration r : registrations.waitlist(registration.eventId())) {
      position++;
      if (r.id().equals(registration.id())) {
        return position;
      }
    }
    return 0;
  }
}
