// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.service;

import gal.furar.event.application.port.in.SendEventMails;
import gal.furar.event.application.port.out.EventRepository;
import gal.furar.event.application.port.out.RegistrationRepository;
import gal.furar.event.application.port.out.SessionRepository;
import gal.furar.event.domain.Event;
import gal.furar.event.domain.EventDetails;
import gal.furar.event.domain.EventId;
import gal.furar.event.domain.EventSchedule;
import gal.furar.event.domain.Registration;
import gal.furar.event.domain.RegistrationId;
import gal.furar.event.domain.RegistrationStatus;
import gal.furar.event.domain.WaitlistNotice;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.notification.EventDecisionMail;
import gal.furar.notification.EventMailFacts;
import gal.furar.notification.EventMailer;
import gal.furar.notification.EventNoticeMail;
import gal.furar.notification.EventSessionMail;
import gal.furar.notification.EventWaitlistMail;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Prepara os correos dos eventos nunha lectura curta e envíaos despois, fóra de toda transacción,
 * con {@link EventMailer} (ADR-0038): á persoa, cando o concello decide a súa solicitude; a cada
 * persoa apuntada ou pedindo praza (coa súa conta e correo), cando o evento cambia de data, hora ou
 * lugar ou se cancela.
 */
@Service
class EventMails implements SendEventMails {

  private final EventRepository events;
  private final RegistrationRepository registrations;
  private final SessionRepository sessions;
  private final FacilityQueries facilities;
  private final EventMailer mailer;
  private final TransactionTemplate read;
  private final TransactionTemplate write;
  private final Clock clock;

  EventMails(
      EventRepository events,
      RegistrationRepository registrations,
      SessionRepository sessions,
      FacilityQueries facilities,
      EventMailer mailer,
      PlatformTransactionManager transactions,
      Clock clock) {
    this.events = events;
    this.registrations = registrations;
    this.sessions = sessions;
    this.facilities = facilities;
    this.mailer = mailer;
    this.read = new TransactionTemplate(transactions);
    this.read.setReadOnly(true);
    this.write = new TransactionTemplate(transactions);
    this.clock = clock;
  }

  @Override
  public void waitlistNotified(UUID registrationId) {
    Instant now = clock.instant();
    Optional<EventWaitlistMail> mail =
        Objects.requireNonNull(
            read.execute(
                status ->
                    registrations
                        .find(new RegistrationId(registrationId))
                        .filter(r -> r.status() == RegistrationStatus.NOTIFIED)
                        .filter(r -> r.participant().email() != null)
                        .filter(r -> r.notice() != null)
                        .filter(r -> Objects.requireNonNull(r.notice()).mailedAt() == null)
                        .filter(r -> !Objects.requireNonNull(r.notice()).notifiedAt().isAfter(now))
                        .flatMap(r -> events.findById(r.eventId()).map(e -> waitlistMail(e, r)))));
    if (mail.isEmpty()) {
      return;
    }
    mailer.sendWaitlistNotice(mail.get());
    write.executeWithoutResult(
        status -> registrations.markNoticeMailed(new RegistrationId(registrationId), now));
  }

  private EventWaitlistMail waitlistMail(Event e, Registration r) {
    WaitlistNotice notice = Objects.requireNonNull(r.notice());
    return new EventWaitlistMail(
        facts(e),
        r.id().value(),
        notice.notifiedAt(),
        notice.exclusiveUntil(),
        Objects.requireNonNull(r.participant().email()),
        r.participant().language(),
        r.participant().name(),
        r.dependent() == null ? null : r.dependent().name());
  }

  @Override
  public void sessionCancelled(UUID eventId, UUID sessionId) {
    Optional<EventSessionMail> mail =
        Objects.requireNonNull(
            read.execute(
                status ->
                    events
                        .findById(new EventId(eventId))
                        .flatMap(
                            e ->
                                sessions
                                    .find(e.id(), sessionId)
                                    .map(
                                        s ->
                                            new EventSessionMail(
                                                facts(e),
                                                EventSessionMail.Kind.SESSION_CANCELLED,
                                                sessionId,
                                                s.date(),
                                                s.startsAt(),
                                                seated(e.id()))))));
    mail.ifPresent(mailer::sendSessionNotice);
  }

  @Override
  public void endedEarly(UUID eventId, UUID changeId, LocalDate from) {
    Optional<EventSessionMail> mail =
        Objects.requireNonNull(
            read.execute(
                status ->
                    events
                        .findById(new EventId(eventId))
                        .map(
                            e ->
                                new EventSessionMail(
                                    facts(e),
                                    EventSessionMail.Kind.ENDED_EARLY,
                                    changeId,
                                    from,
                                    from.atStartOfDay(EventSchedule.ZONE).toInstant(),
                                    seated(e.id())))));
    mail.ifPresent(mailer::sendSessionNotice);
  }

  /** As persoas con praza (coa súa conta e correo). */
  private List<EventNoticeMail.Recipient> seated(EventId event) {
    return registrations.listForEvent(event).stream()
        .filter(Registration::confirmed)
        .filter(r -> r.participant().email() != null)
        .map(EventMails::recipient)
        .toList();
  }

  @Override
  public void decided(UUID registrationId) {
    Optional<EventDecisionMail> mail =
        Objects.requireNonNull(
            read.execute(
                status ->
                    registrations
                        .find(new RegistrationId(registrationId))
                        .filter(r -> r.status() != RegistrationStatus.PENDING)
                        .filter(r -> r.participant().email() != null)
                        .flatMap(
                            r ->
                                events
                                    .findById(r.eventId())
                                    .map(
                                        e ->
                                            new EventDecisionMail(
                                                facts(e),
                                                registrationId,
                                                r.confirmed(),
                                                Objects.requireNonNull(r.participant().email()),
                                                r.participant().language(),
                                                r.participant().name())))));
    mail.ifPresent(mailer::sendDecision);
  }

  @Override
  public void moved(UUID eventId, UUID changeId) {
    notice(eventId, EventNoticeMail.Kind.MOVED, changeId);
  }

  @Override
  public void cancelled(UUID eventId) {
    notice(eventId, EventNoticeMail.Kind.CANCELLED, null);
  }

  private void notice(UUID eventId, EventNoticeMail.Kind kind, @Nullable UUID change) {
    Optional<EventNoticeMail> mail =
        Objects.requireNonNull(
            read.execute(
                status ->
                    events
                        .findById(new EventId(eventId))
                        .map(
                            e -> new EventNoticeMail(facts(e), kind, change, recipients(e.id())))));
    mail.ifPresent(mailer::sendNotice);
  }

  private List<EventNoticeMail.Recipient> recipients(EventId event) {
    return registrations.listForEvent(event).stream()
        .filter(r -> r.status() != RegistrationStatus.REJECTED)
        .filter(r -> r.participant().email() != null)
        .map(EventMails::recipient)
        .toList();
  }

  private static EventNoticeMail.Recipient recipient(Registration r) {
    return new EventNoticeMail.Recipient(
        r.id().value(),
        Objects.requireNonNull(r.participant().email()),
        r.participant().language(),
        r.participant().name());
  }

  private EventMailFacts facts(Event event) {
    EventDetails d = event.details();
    UUID facility = d.place().facilityId();
    String place =
        facility == null
            ? Objects.requireNonNull(d.place().other())
            : facilities.find(facility).map(FacilitySummary::name).orElse("");
    EventSchedule s = d.schedule();
    return new EventMailFacts(
        event.id().value(),
        d.name().gl(),
        d.name().es(),
        place,
        place,
        d.startsAt(),
        s == null
            ? null
            : new EventMailFacts.Schedule(
                s.isoWeekdays(), s.startMinute(), s.endMinute(), s.firstDate(), s.lastDate()));
  }
}
