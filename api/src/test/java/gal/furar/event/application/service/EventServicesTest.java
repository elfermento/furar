// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.furar.event.EventCancelled;
import gal.furar.event.EventMoved;
import gal.furar.event.RegistrationDecided;
import gal.furar.event.application.port.in.EventCommand;
import gal.furar.event.application.port.in.EventView;
import gal.furar.event.application.port.in.RegistrationView;
import gal.furar.event.domain.EventId;
import gal.furar.event.domain.PaymentStatus;
import gal.furar.event.domain.Registration;
import gal.furar.event.domain.RegistrationStatus;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.people.PeopleQueries;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** Casos de uso dos eventos e das inscricións cos repositorios en memoria (ADR-0038). */
class EventServicesTest {

  static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
  static final Instant STARTS = NOW.plus(Duration.ofDays(10));
  static final UUID MUNICIPALITY = UUID.randomUUID();
  static final UUID FACILITY = UUID.randomUUID();
  static final UUID WITHDRAWN = UUID.randomUUID();

  final InMemoryEvents repo = new InMemoryEvents();
  final FacilityQueries facilities = mock(FacilityQueries.class);
  final ActivityFixtures.FakeBookings bookings = new ActivityFixtures.FakeBookings();
  final PeopleQueries people = mock(PeopleQueries.class);
  final List<AuditEvent> audited = new ArrayList<>();
  final AuditLog audit = audited::add;
  final List<Object> published = new ArrayList<>();
  final ApplicationEventPublisher publisher = published::add;
  Actor actor = person("sub-1");
  final CurrentActor currentActor = () -> Optional.of(actor);
  Instant now = NOW;
  EventService events;
  RegistrationService registrations;
  TenantContext.Scope scope;

  static Actor person(String subject) {
    return new Actor(
        subject,
        Set.of(),
        true,
        new Actor.Profile("Persoa " + subject, subject + "@example.org", "gl"));
  }

  @BeforeEach
  void setUp() {
    Clock clock =
        new Clock() {
          @Override
          public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(java.time.ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            return now;
          }
        };
    EventViews views = new EventViews(repo, facilities, repo, clock);
    Waitlist waitlist = new Waitlist(repo, publisher, ActivityFixtures.CLOCK);
    ActivitySessions activity = new ActivitySessions(bookings, repo);
    events =
        new EventService(
            repo, repo, views, activity, repo, waitlist, facilities, audit, publisher, clock);
    registrations =
        new RegistrationService(
            repo, repo, views, waitlist, people, currentActor, audit, publisher, clock);
    when(facilities.find(FACILITY))
        .thenReturn(
            Optional.of(
                new FacilitySummary(FACILITY, "Pista de atletismo", "ACTIVE", true, true, null)));
    when(facilities.find(WITHDRAWN))
        .thenReturn(
            Optional.of(new FacilitySummary(WITHDRAWN, "Vella", "WITHDRAWN", false, false, null)));
    when(facilities.summary(anyList())).thenReturn(List.of());
    when(facilities.covers(anyCollection())).thenReturn(List.of());
    scope = TenantContext.open(new TenantRef(MUNICIPALITY, "naron"));
  }

  @AfterEach
  void tearDown() {
    scope.close();
  }

  static EventCommand command(
      @Nullable Integer capacity,
      @Nullable Integer price,
      boolean approval,
      @Nullable UUID facility) {
    return new EventCommand(
        "Carreira popular",
        "Carrera popular",
        null,
        null,
        STARTS,
        facility,
        facility == null ? "Praza de Galicia" : null,
        capacity,
        price,
        null,
        true,
        approval);
  }

  EventId created(@Nullable Integer capacity, @Nullable Integer price, boolean approval) {
    return events.create(command(capacity, price, approval, FACILITY)).event().id();
  }

  static String code(Runnable action) {
    try {
      action.run();
    } catch (DomainException e) {
      return e.errorCode().code();
    }
    throw new AssertionError("no error");
  }

  @Test
  void createChecksTheDateAndThePlace() {
    EventCommand past =
        new EventCommand(
            "A",
            "A",
            null,
            null,
            NOW.minusSeconds(60),
            FACILITY,
            null,
            null,
            null,
            null,
            true,
            false);
    assertThat(code(() -> events.create(past))).isEqualTo("event.starts-in-past");
    assertThat(code(() -> events.create(command(null, null, false, WITHDRAWN))))
        .isEqualTo("event.facility-not-found");
    EventView other = events.create(command(null, null, false, null));
    assertThat(other.event().details().place().other()).isEqualTo("Praza de Galicia");
    assertThat(audited).extracting(AuditEvent::action).containsExactly("event.create");
  }

  @Test
  void joiningTakesASeatAndTheLastOneOnlyOnce() {
    EventId id = created(1, null, false);
    RegistrationView mine = registrations.join(id);
    assertThat(mine.registration().status()).isEqualTo(RegistrationStatus.CONFIRMED);
    assertThat(mine.event().full()).isTrue();
    assertThat(code(() -> registrations.join(id))).isEqualTo("event.already-registered");
    // Completo e sen aprobación: á lista de espera (ADR-0041).
    actor = person("sub-2");
    assertThat(registrations.join(id).registration().status())
        .isEqualTo(RegistrationStatus.WAITING);
    assertThat(repo.locks).isGreaterThanOrEqualTo(2);

    actor = person("sub-1");
    registrations.leave(id);
    actor = person("sub-2");
    assertThat(registrations.confirm(id, null).registration().confirmed()).isTrue();
  }

  @Test
  void withApprovalAFullEventStillTakesRequestsButAcceptingNeedsASeat() {
    EventId id = created(1, null, true);
    Registration first = registrations.join(id).registration();
    registrations.accept(id, first.id());
    actor = person("sub-2");
    RegistrationView asked = registrations.join(id);
    assertThat(asked.registration().status()).isEqualTo(RegistrationStatus.PENDING);
    assertThat(asked.event().full()).isTrue();
    assertThat(asked.event().open(now)).isTrue();
    assertThat(code(() -> registrations.accept(id, asked.registration().id())))
        .isEqualTo("event.full");

    // Cando se libera unha praza, a xestión acepta a quen queira.
    actor = person("sub-1");
    registrations.leave(id);
    assertThat(registrations.accept(id, asked.registration().id()).confirmed()).isTrue();
  }

  @Test
  void theDeadlineClosesRegistrations() {
    EventCommand c =
        new EventCommand(
            "A",
            "A",
            null,
            null,
            STARTS,
            FACILITY,
            null,
            null,
            null,
            STARTS.minus(Duration.ofDays(1)),
            true,
            false);
    EventId id = events.create(c).event().id();
    now = STARTS.minus(Duration.ofHours(12));
    assertThat(code(() -> registrations.join(id))).isEqualTo("event.closed");
  }

  @Test
  void withApprovalThePersonAsksAndTheManagerDecides() {
    EventId id = created(1, 500, true);
    Registration asked = registrations.join(id).registration();
    assertThat(asked.status()).isEqualTo(RegistrationStatus.PENDING);
    assertThat(registrations.pendingCount()).isEqualTo(1);
    actor = person("sub-2");
    Registration second = registrations.join(id).registration();

    Registration accepted = registrations.accept(id, asked.id());
    assertThat(accepted.confirmed()).isTrue();
    assertThat(code(() -> registrations.accept(id, second.id()))).isEqualTo("event.full");
    Registration rejected = registrations.reject(id, second.id());
    assertThat(rejected.status()).isEqualTo(RegistrationStatus.REJECTED);
    assertThat(published).filteredOn(RegistrationDecided.class::isInstance).hasSize(2);
    assertThat(registrations.pendingCount()).isZero();

    Registration paid = registrations.pay(id, accepted.id(), PaymentStatus.PAID);
    assertThat(paid.payment()).isEqualTo(PaymentStatus.PAID);
    assertThat(audited)
        .extracting(AuditEvent::action)
        .contains("event.accept", "event.reject", "event.payment");
    assertThat(audited.toString()).doesNotContain("example.org");
  }

  @Test
  void aFreeEventHasNoPayment() {
    EventId id = created(null, null, false);
    Registration mine = registrations.join(id).registration();
    assertThat(mine.payment()).isNull();
    assertThat(code(() -> registrations.pay(id, mine.id(), PaymentStatus.PAID)))
        .isEqualTo("event.no-payment");
  }

  @Test
  void movingOrCancellingTellsThePeopleAndSeatsCannotDropBelowThem() {
    EventId id = created(5, null, false);
    registrations.join(id);
    actor = person("sub-2");
    registrations.join(id);

    assertThat(code(() -> events.update(id, 0, command(1, null, false, FACILITY))))
        .isEqualTo("event.capacity-below-registered");
    events.update(id, 0, command(2, 300, true, FACILITY));
    assertThat(published).noneMatch(EventMoved.class::isInstance);
    EventCommand moved =
        new EventCommand(
            "Carreira popular",
            "Carrera popular",
            null,
            null,
            STARTS.plusSeconds(3600),
            FACILITY,
            null,
            2,
            300,
            null,
            true,
            true);
    events.update(id, 1, moved);
    assertThat(published).filteredOn(EventMoved.class::isInstance).hasSize(1);

    assertThat(code(() -> events.delete(id))).isEqualTo("event.has-registrations");
    events.cancel(id, 2);
    assertThat(published).filteredOn(EventCancelled.class::isInstance).hasSize(1);
    assertThat(code(() -> registrations.join(id))).isEqualTo("event.not-found");
    assertThat(registrations.upcoming()).isEmpty();
  }

  @Test
  void myUpcomingAreTheFutureOnes() {
    EventId first = created(null, null, false);
    EventId second = created(null, null, true);
    registrations.join(first);
    registrations.join(second);
    assertThat(registrations.upcoming())
        .extracting(v -> v.registration().status())
        .containsExactlyInAnyOrder(RegistrationStatus.CONFIRMED, RegistrationStatus.PENDING);
    now = STARTS;
    assertThat(registrations.upcoming()).isEmpty();
    assertThat(code(() -> registrations.leave(first))).isEqualTo("event.closed");
  }

  @Test
  void anEventWithoutPeopleIsDeleted() {
    EventId id = created(null, null, false);
    events.delete(id);
    assertThat(repo.events).isEmpty();
    assertThat(audited).extracting(AuditEvent::action).contains("event.delete");
  }
}
