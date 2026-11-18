// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.service;

import gal.furar.event.EventPermission;
import gal.furar.event.RegistrationDecided;
import gal.furar.event.application.port.in.EventRegistrationsUseCase;
import gal.furar.event.application.port.in.EventView;
import gal.furar.event.application.port.in.JoinEventUseCase;
import gal.furar.event.application.port.in.RegistrationView;
import gal.furar.event.application.port.out.EventRepository;
import gal.furar.event.application.port.out.RegistrationRepository;
import gal.furar.event.domain.BirthYears;
import gal.furar.event.domain.Dependent;
import gal.furar.event.domain.Event;
import gal.furar.event.domain.EventError;
import gal.furar.event.domain.EventId;
import gal.furar.event.domain.EventSchedule;
import gal.furar.event.domain.Participant;
import gal.furar.event.domain.PaymentStatus;
import gal.furar.event.domain.Registration;
import gal.furar.event.domain.RegistrationId;
import gal.furar.event.domain.RegistrationStatus;
import gal.furar.people.DependentSummary;
import gal.furar.people.PeopleQueries;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As persoas nos eventos (ADR-0038, ADR-0041). Apuntarse, aceptar unha solicitude e confirmar unha
 * praza da lista de espera confirman unha praza baixo o bloqueo da fila do evento: dúas persoas á
 * vez non ocupan a última ({@code event.full}). A conta apúntase a si ou a unha persoa a cargo,
 * unha vez cada unha. Nun evento completo sen aprobación (ou con lista), á lista de espera ({@link
 * Waitlist}); con aprobación, a solicitude queda pendente aínda que estea completo. Aceptar ou
 * rexeitar avisa á persoa por correo despois do commit ({@link RegistrationDecided}).
 */
@Service
public class RegistrationService implements JoinEventUseCase, EventRegistrationsUseCase {

  public static final String ACCEPT = "event.accept";
  public static final String REJECT = "event.reject";
  public static final String PAYMENT = "event.payment";

  private final EventRepository events;
  private final RegistrationRepository registrations;
  private final EventViews views;
  private final Waitlist waitlist;
  private final PeopleQueries people;
  private final CurrentActor currentActor;
  private final AuditLog audit;
  private final ApplicationEventPublisher publisher;
  private final Clock clock;

  public RegistrationService(
      EventRepository events,
      RegistrationRepository registrations,
      EventViews views,
      Waitlist waitlist,
      PeopleQueries people,
      CurrentActor currentActor,
      AuditLog audit,
      ApplicationEventPublisher publisher,
      Clock clock) {
    this.events = events;
    this.registrations = registrations;
    this.views = views;
    this.waitlist = waitlist;
    this.people = people;
    this.currentActor = currentActor;
    this.audit = audit;
    this.publisher = publisher;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public Optional<RegistrationView> mine(EventId id, @Nullable UUID dependentId) {
    Actor actor = actor();
    Event event = visible(id);
    EventView view = views.of(event);
    return registrations.findByPerson(id, actor.subject(), dependentId).map(r -> view(r, view));
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public List<RegistrationView> mineAll(EventId id) {
    Actor actor = actor();
    Event event = visible(id);
    EventView view = views.of(event);
    return registrations.listByAccount(id, actor.subject()).stream()
        .map(r -> view(r, view))
        .toList();
  }

  @Override
  @Transactional
  @PersonUseCase
  public RegistrationView join(EventId id, JoinCommand command) {
    Actor actor = actor();
    lock(id);
    Event event = visible(id);
    Instant now = clock.instant();
    event.checkOpen(now);
    Dependent dependent = null;
    LocalDate birthDate = command.birthDate();
    Integer birthYear = null;
    if (birthDate != null) {
      LocalDate today = now.atZone(EventSchedule.ZONE).toLocalDate();
      if (!birthDate.isBefore(today) || birthDate.getYear() < BirthYears.MIN) {
        throw new DomainException(EventError.BIRTH_DATE_INVALID);
      }
      birthYear = birthDate.getYear();
    }
    if (command.dependentId() != null) {
      DependentSummary found =
          people.dependents(actor.subject(), List.of(command.dependentId())).stream()
              .findFirst()
              .orElseThrow(() -> new DomainException(EventError.DEPENDENT_NOT_FOUND));
      dependent = new Dependent(found.id(), found.name());
      birthYear = found.birthYear();
    }
    BirthYears limits = event.details().birthYears();
    if (limits.limited()) {
      if (birthYear == null) {
        throw new DomainException(EventError.BIRTH_YEAR_REQUIRED);
      }
      limits.check(birthYear);
    }
    // Só nun evento con límite: a data da propia persoa ou o ano da persoa a cargo.
    LocalDate storedDate = dependent == null && limits.limited() ? birthDate : null;
    Integer storedYear = dependent != null && limits.limited() ? birthYear : null;
    if (registrations.findByPerson(id, actor.subject(), command.dependentId()).isPresent()) {
      throw new DomainException(EventError.ALREADY_REGISTERED);
    }
    EventView view = views.of(event);
    Registration saved;
    if (event.waitlist() && (view.full() || view.waitlisted() > 0)) {
      if (registrations.countActiveWaits(actor.subject(), now) >= Waitlist.MAX_WAITS) {
        throw new DomainException(EventError.WAITLIST_LIMIT, Map.of("max", Waitlist.MAX_WAITS));
      }
      saved =
          registrations.insert(
              Registration.waiting(
                  event, participant(actor), dependent, storedYear, storedDate, now));
      // Se había praza libre (a lista estaba esperando o aviso), quen toca recíbeo agora.
      waitlist.fill(event, now);
      saved = registrations.find(saved.id()).orElse(saved);
    } else {
      // Sen lista de espera, ou é «Con aprobación» (pendente aínda que estea completo: aceptar
      // segue precisando praza) ou non ten límite de prazas.
      saved =
          registrations.insert(
              Registration.open(event, participant(actor), dependent, storedYear, storedDate, now));
    }
    return view(saved, views.of(event));
  }

  @Override
  @Transactional
  @PersonUseCase
  public RegistrationView confirm(EventId id, @Nullable UUID dependentId) {
    Actor actor = actor();
    lock(id);
    Event event = visible(id);
    Instant now = clock.instant();
    Registration mine =
        registrations
            .findByPerson(id, actor.subject(), dependentId)
            .filter(r -> r.status() == RegistrationStatus.NOTIFIED)
            .orElseThrow(() -> new DomainException(EventError.NOT_NOTIFIED));
    if (!event.leavable(now)) {
      throw new DomainException(EventError.CLOSED);
    }
    // A praza lévaa a primeira avisada que confirma, se aínda está libre.
    if (views.of(event).full()) {
      waitlist.fill(event, now);
      throw new DomainException(EventError.FULL);
    }
    Registration saved = registrations.update(mine.confirmNotice(event, now));
    waitlist.fill(event, now);
    return view(saved, views.of(event));
  }

  @Override
  @Transactional
  @PersonUseCase
  public void leave(EventId id, @Nullable UUID dependentId) {
    Actor actor = actor();
    lock(id);
    Event event = load(id);
    Registration mine =
        registrations
            .findByPerson(id, actor.subject(), dependentId)
            .filter(r -> r.status() != RegistrationStatus.REJECTED)
            .orElseThrow(() -> new DomainException(EventError.NOT_REGISTERED));
    if (event.cancelled()) {
      throw new DomainException(EventError.CANCELLED);
    }
    Instant now = clock.instant();
    if (!event.leavable(now)) {
      throw new DomainException(EventError.CLOSED);
    }
    registrations.delete(mine.id());
    // Unha praza libre ou unha avisada menos: quen toca recíbeo agora.
    waitlist.fill(event, now);
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public List<RegistrationView> upcoming() {
    Actor actor = actor();
    Instant now = clock.instant();
    List<Registration> mine = registrations.listForPerson(actor.subject());
    if (mine.isEmpty()) {
      return List.of();
    }
    List<Event> found =
        events.findAllById(mine.stream().map(Registration::eventId).toList()).stream()
            .filter(e -> !e.cancelled() && !e.ended(now))
            .toList();
    Map<UUID, EventView> byId = views.byId(found);
    return mine.stream()
        .filter(r -> byId.containsKey(r.eventId().value()))
        .map(r -> view(r, byId.get(r.eventId().value())))
        .sorted(
            Comparator.comparing((RegistrationView v) -> v.event().event().details().startsAt()))
        .toList();
  }

  /** Coa posición na lista de espera, se está nela. */
  private RegistrationView view(Registration r, EventView event) {
    return new RegistrationView(r, event, r.status().waitlisted() ? waitlist.position(r) : null);
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(EventPermission.Code.REGISTRATIONS)
  public List<Registration> list(EventId id) {
    load(id);
    return registrations.listForEvent(id);
  }

  @Override
  @Transactional
  @RequiresPermission(EventPermission.Code.REVIEW)
  public Registration accept(EventId id, RegistrationId registration) {
    lock(id);
    Event event = load(id);
    if (event.cancelled()) {
      throw new DomainException(EventError.CANCELLED);
    }
    Registration current = loadRegistration(id, registration);
    if (current.status() == RegistrationStatus.PENDING && views.of(event).full()) {
      throw new DomainException(EventError.FULL);
    }
    return decide(current.accept(clock.instant()), ACCEPT);
  }

  @Override
  @Transactional
  @RequiresPermission(EventPermission.Code.REVIEW)
  public Registration reject(EventId id, RegistrationId registration) {
    lock(id);
    load(id);
    Registration current = loadRegistration(id, registration);
    return decide(current.reject(clock.instant()), REJECT);
  }

  @Override
  @Transactional
  @RequiresPermission(EventPermission.Code.PAYMENT)
  public Registration pay(EventId id, RegistrationId registration, PaymentStatus status) {
    Event event = load(id);
    Registration current = loadRegistration(id, registration);
    Registration saved = registrations.update(current.pay(status, event, clock.instant()));
    Map<String, Object> details = details(saved);
    details.put("from", String.valueOf(current.paymentFor(event)));
    details.put("to", status.name());
    record(PAYMENT, details);
    return saved;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(EventPermission.Code.REGISTRATIONS)
  public long pendingCount() {
    return registrations.countPendingFrom(clock.instant());
  }

  private Registration decide(Registration changed, String action) {
    Registration saved = registrations.update(changed);
    record(action, details(saved));
    if (saved.participant().email() != null) {
      publisher.publishEvent(
          new RegistrationDecided(TenantContext.require().id(), saved.id().value()));
    }
    return saved;
  }

  private Registration loadRegistration(EventId event, RegistrationId id) {
    return registrations
        .find(id)
        .filter(r -> r.eventId().equals(event))
        .orElseThrow(() -> new DomainException(EventError.REGISTRATION_NOT_FOUND));
  }

  private void lock(EventId id) {
    if (!registrations.lockEvent(id)) {
      throw new DomainException(EventError.NOT_FOUND);
    }
  }

  private Event load(EventId id) {
    return events.findById(id).orElseThrow(() -> new DomainException(EventError.NOT_FOUND));
  }

  /** A veciñanza só ve os publicados e sen cancelar. */
  private Event visible(EventId id) {
    Event event = load(id);
    if (!event.visible()) {
      throw new DomainException(EventError.NOT_FOUND);
    }
    return event;
  }

  private Actor actor() {
    return currentActor.current().orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED));
  }

  /** Quen se apunta, tal como vén no token; sen idioma coñecido, galego. */
  static Participant participant(Actor actor) {
    Actor.Profile profile = actor.profile();
    String locale = profile.locale() == null ? "" : profile.locale();
    Language language =
        Language.fromCode(locale.toLowerCase(Locale.ROOT).split("[-_]")[0]).orElse(Language.GL);
    return new Participant(actor.subject(), profile.name(), profile.email(), language);
  }

  /** No rexistro de actividade: o evento e a inscrición, sen datos da persoa. */
  private static Map<String, Object> details(Registration r) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("eventId", r.eventId().value().toString());
    details.put("registrationId", r.id().value().toString());
    return details;
  }

  private void record(String action, Map<String, Object> details) {
    TenantRef tenant = TenantContext.require();
    audit.record(new AuditEvent(action, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
  }
}
