// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.furar.event.EventEndedEarly;
import gal.furar.event.EventSessionCancelled;
import gal.furar.event.EventWaitlistNotified;
import gal.furar.event.application.port.in.EventAttendanceUseCase;
import gal.furar.event.application.port.in.EventCommand;
import gal.furar.event.application.port.in.EventView;
import gal.furar.event.application.port.in.JoinEventUseCase.JoinCommand;
import gal.furar.event.application.port.in.RegistrationView;
import gal.furar.event.application.port.in.SessionView;
import gal.furar.event.domain.EventId;
import gal.furar.event.domain.EventKind;
import gal.furar.event.domain.EventSession;
import gal.furar.event.domain.PricePeriod;
import gal.furar.event.domain.Registration;
import gal.furar.event.domain.RegistrationStatus;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.people.DependentSummary;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * As actividades con horario, a lista de espera en fervenza, as persoas a cargo, a idade e a
 * asistencia, cos repositorios en memoria (ADR-0041).
 */
class ActivityServicesTest {

  static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
  static final UUID MUNICIPALITY = UUID.randomUUID();
  static final UUID FACILITY = UUID.randomUUID();
  static final UUID USE = UUID.randomUUID();
  static final UUID CHILD = UUID.randomUUID();

  static Instant at(String local) {
    return LocalDateTime.parse(local).atZone(ZONE).toInstant();
  }

  final InMemoryEvents repo = new InMemoryEvents();
  final FacilityQueries facilities = mock(FacilityQueries.class);
  final PeopleQueries people = mock(PeopleQueries.class);
  final ActivityFixtures.FakeBookings bookings = new ActivityFixtures.FakeBookings();
  final List<AuditEvent> audited = new ArrayList<>();
  final AuditLog audit = audited::add;
  final List<Object> published = new ArrayList<>();
  final ApplicationEventPublisher publisher = published::add;
  Actor actor = person("sub-1");
  final CurrentActor currentActor = () -> Optional.of(actor);
  Instant now = at("2026-11-16T10:00");
  EventService events;
  RegistrationService registrations;
  EventActivityService activity;
  EventAttendanceService attendance;
  Waitlist waitlist;
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
          public ZoneId getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            return now;
          }
        };
    EventViews views = new EventViews(repo, facilities, repo, clock);
    waitlist = new Waitlist(repo, publisher, ActivityFixtures.CLOCK);
    ActivitySessions sessions = new ActivitySessions(bookings, repo);
    events =
        new EventService(
            repo, repo, views, sessions, repo, waitlist, facilities, audit, publisher, clock);
    registrations =
        new RegistrationService(
            repo, repo, views, waitlist, people, currentActor, audit, publisher, clock);
    activity =
        new EventActivityService(
            repo, repo, repo, sessions, views, facilities, audit, publisher, clock);
    attendance =
        new EventAttendanceService(
            repo, repo, repo, facilities, audit, currentActor, clock, Duration.ofMinutes(45));
    when(facilities.find(FACILITY))
        .thenReturn(
            Optional.of(new FacilitySummary(FACILITY, "Pista", "ACTIVE", true, true, null)));
    when(facilities.summary(anyList())).thenReturn(List.of());
    when(facilities.covers(anyCollection())).thenReturn(List.of());
    when(people.dependents(eq("sub-1"), anyCollection()))
        .thenAnswer(
            call ->
                ((java.util.Collection<?>) call.getArgument(1)).contains(CHILD)
                    ? List.of(new DependentSummary(CHILD, "Xiana", 2015))
                    : List.of());
    scope = TenantContext.open(new TenantRef(MUNICIPALITY, "naron"));
  }

  @AfterEach
  void tearDown() {
    scope.close();
  }

  static String code(Runnable action) {
    try {
      action.run();
    } catch (DomainException e) {
      return e.errorCode().code();
    }
    throw new AssertionError("no error");
  }

  /** Luns e mércores, 18:00–19:00, do 16 de novembro ao 16 de decembro. */
  static EventCommand.ScheduleCommand weekly(@Nullable UUID use, Set<LocalDate> skip) {
    return new EventCommand.ScheduleCommand(
        use,
        null,
        List.of(1, 3),
        1080,
        1140,
        LocalDate.parse("2026-11-16"),
        LocalDate.parse("2026-12-16"),
        skip);
  }

  static EventCommand activityCommand(
      @Nullable Integer capacity,
      boolean approval,
      @Nullable UUID facility,
      @Nullable Integer birthFrom,
      @Nullable Integer birthTo,
      Set<LocalDate> skip) {
    return new EventCommand(
        "Escola de atletismo",
        "Escuela de atletismo",
        null,
        null,
        EventKind.SCHEDULED,
        null,
        facility,
        facility == null ? "Parque" : null,
        weekly(facility == null ? null : USE, skip),
        capacity,
        2000,
        PricePeriod.MONTH,
        null,
        true,
        approval,
        birthFrom,
        birthTo);
  }

  EventId scheduled(@Nullable Integer capacity) {
    return events
        .create(activityCommand(capacity, false, FACILITY, null, null, Set.of()))
        .event()
        .id();
  }

  @Test
  void anActivityInAFacilityOccupiesItAndTellsTheConflictsFirst() {
    LocalDate busy = LocalDate.parse("2026-11-18");
    bookings.busy.add(busy);
    assertThat(activity.preview(FACILITY, weekly(USE, Set.of())).conflictDates())
        .containsExactly(busy);
    assertThat(
            code(() -> events.create(activityCommand(10, false, FACILITY, null, null, Set.of()))))
        .isEqualTo("event.sessions-changed");
    EventView created =
        events.create(activityCommand(10, false, FACILITY, null, null, Set.of(busy)));
    List<EventSession> sessions = repo.sessionsOf(created.event().id());
    // 16 nov a 16 dec, luns e mércores: 10 días, menos o ocupado.
    assertThat(sessions).hasSize(9).noneMatch(s -> s.date().equals(busy));
    assertThat(sessions).allMatch(s -> s.bookingId() != null);
    assertThat(created.event().details().startsAt()).isEqualTo(at("2026-11-16T18:00"));
    assertThat(created.event().details().endsAt()).isEqualTo(at("2026-12-16T19:00"));
    assertThat(bookings.occupied).hasSize(9);
  }

  @Test
  void outsideAFacilityThereIsASessionPerDayAndNothingIsOccupied() {
    EventView created = events.create(activityCommand(null, false, null, null, null, Set.of()));
    assertThat(repo.sessionsOf(created.event().id()))
        .hasSize(10)
        .allMatch(s -> s.bookingId() == null);
    assertThat(bookings.occupied).isEmpty();
  }

  @Test
  void theViewHasTheNextFiveSessionsWithoutTheCancelledOnes() {
    assertThat(
            events.create(activityCommand(null, false, null, null, null, Set.of())).nextSessions())
        .hasSize(5);
    EventId id = scheduled(10);
    List<EventSession> all = repo.sessionsOf(id);
    EventView view = events.published(id);
    assertThat(view.nextSessions())
        .extracting(EventSession::id)
        .containsExactlyElementsOf(all.subList(0, 5).stream().map(EventSession::id).toList());
    activity.cancelSession(id, all.getFirst().id());
    now = all.get(1).endsAt();
    assertThat(events.published(id).nextSessions())
        .extracting(EventSession::date)
        .first()
        .isEqualTo(all.get(2).date());
  }

  @Test
  void cancellingASessionReleasesItsHourAndTellsThePeople() {
    EventId id = scheduled(10);
    EventSession first = repo.sessionsOf(id).getFirst();
    SessionView cancelled = activity.cancelSession(id, first.id());
    assertThat(cancelled.session().cancelled()).isTrue();
    assertThat(bookings.released).containsExactly(first.bookingId());
    assertThat(published).filteredOn(EventSessionCancelled.class::isInstance).hasSize(1);
    assertThat(code(() -> activity.cancelSession(id, first.id())))
        .isEqualTo("event.session-cancelled");
    assertThat(audited).extracting(AuditEvent::action).contains("event.session-cancel");
  }

  @Test
  void endingEarlyKeepsStartedSessionsAndThoseWithAList() {
    EventId id = scheduled(10);
    now = at("2026-11-18T18:30");
    // A sesión do 18 xa empezou; a do 16 ten lista.
    EventSession monday = repo.sessionsOf(id).getFirst();
    attendance.mark(id, monday.id(), Map.of());
    EventView ended = activity.end(id, LocalDate.parse("2026-11-18"));
    assertThat(ended.event().endedFrom()).isEqualTo(LocalDate.parse("2026-11-18"));
    assertThat(repo.sessionsOf(id))
        .filteredOn(s -> !s.cancelled())
        .extracting(EventSession::date)
        .containsExactly(LocalDate.parse("2026-11-16"), LocalDate.parse("2026-11-18"));
    assertThat(ended.event().details().endsAt()).isEqualTo(at("2026-11-18T19:00"));
    assertThat(bookings.released).hasSize(8);
    assertThat(published).filteredOn(EventEndedEarly.class::isInstance).hasSize(1);
  }

  @Test
  void theScheduleOfAnActivityIsNotEditedButTheRestIs() {
    EventId id = scheduled(10);
    EventCommand renamed =
        new EventCommand(
            "Atletismo",
            "Atletismo",
            null,
            null,
            EventKind.SCHEDULED,
            null,
            FACILITY,
            null,
            weekly(USE, Set.of()),
            12,
            2000,
            PricePeriod.QUARTER,
            null,
            true,
            false,
            null,
            null);
    EventView saved = events.update(id, 0, renamed);
    assertThat(saved.event().details().pricePeriod()).isEqualTo(PricePeriod.QUARTER);
    assertThat(bookings.renamed).containsExactly("Atletismo");
    EventCommand moved =
        new EventCommand(
            "Atletismo",
            "Atletismo",
            null,
            null,
            EventKind.SCHEDULED,
            null,
            FACILITY,
            null,
            new EventCommand.ScheduleCommand(
                USE,
                null,
                List.of(2),
                1080,
                1140,
                LocalDate.parse("2026-11-16"),
                LocalDate.parse("2026-12-16"),
                Set.of()),
            12,
            2000,
            PricePeriod.QUARTER,
            null,
            true,
            false,
            null,
            null);
    assertThat(code(() -> events.update(id, 1, moved))).isEqualTo("event.schedule-not-editable");
  }

  @Test
  void anAccountSignsUpItselfAndItsDependentsOnceEach() {
    EventId id = scheduled(10);
    registrations.join(id);
    RegistrationView child = registrations.join(id, new JoinCommand(CHILD, null));
    assertThat(child.registration().dependent().name()).isEqualTo("Xiana");
    assertThat(code(() -> registrations.join(id, new JoinCommand(CHILD, null))))
        .isEqualTo("event.already-registered");
    assertThat(code(() -> registrations.join(id, new JoinCommand(UUID.randomUUID(), null))))
        .isEqualTo("event.dependent-not-found");
    assertThat(registrations.mineAll(id)).hasSize(2);
    registrations.leave(id, CHILD);
    assertThat(registrations.mineAll(id)).hasSize(1);
  }

  @Test
  void theBirthDateIsCheckedByItsYearAndOnlyKeptInTheRegistration() {
    EventId id =
        events.create(activityCommand(10, false, FACILITY, 2014, 2016, Set.of())).event().id();
    assertThat(code(() -> registrations.join(id))).isEqualTo("event.birth-year-required");
    assertThat(code(() -> registrations.join(id, new JoinCommand(null, LocalDate.of(1990, 5, 1)))))
        .isEqualTo("event.age-not-allowed");
    assertThat(code(() -> registrations.join(id, new JoinCommand(null, LocalDate.of(2030, 1, 1)))))
        .isEqualTo("event.birth-date-invalid");
    // A persoa a cargo compróbase polo ano da súa ficha (2015), non pola data que veña.
    Registration child =
        registrations.join(id, new JoinCommand(CHILD, LocalDate.of(1990, 1, 1))).registration();
    assertThat(child.birthYear()).isEqualTo(2015);
    assertThat(child.birthDate()).isNull();
    actor = person("sub-2");
    Registration self =
        registrations.join(id, new JoinCommand(null, LocalDate.of(2016, 12, 31))).registration();
    assertThat(self.birthDate()).isEqualTo(LocalDate.of(2016, 12, 31));
    assertThat(self.birthYear()).isNull();
  }

  @Test
  void aNewPersonGoesToTheWaitlistWhileThereIsOneAndAtMostThreeWaits() {
    EventId id = scheduled(1);
    registrations.join(id);
    actor = person("sub-2");
    RegistrationView waiting = registrations.join(id);
    assertThat(waiting.registration().status()).isEqualTo(RegistrationStatus.WAITING);
    assertThat(waiting.position()).isEqualTo(1);
    for (int i = 0; i < 2; i++) {
      EventId other = scheduled(1);
      actor = person("sub-1");
      registrations.join(other);
      actor = person("sub-2");
      registrations.join(other);
    }
    EventId fourth = scheduled(1);
    actor = person("sub-1");
    registrations.join(fourth);
    actor = person("sub-2");
    assertThat(code(() -> registrations.join(fourth))).isEqualTo("event.waitlist-limit");
  }

  /** Tres persoas esperan nun evento dunha praza; a que a ten sae ás 21:30. */
  EventId fullWithThreeWaiting() {
    EventId id = scheduled(1);
    registrations.join(id);
    for (String s : List.of("w1", "w2", "w3")) {
      actor = person(s);
      registrations.join(id);
    }
    return id;
  }

  /** O que fai a tarefa da lista cada minuto, nese instante. */
  void fill(EventId id, Instant at) {
    waitlist.fill(repo.findById(id).orElseThrow(), at);
  }

  Registration of(EventId id, String subject) {
    return repo.findByPerson(id, subject).orElseThrow();
  }

  @Test
  void theFirstHasTheSeatAloneForAnHourWithTheNightPauseAndThenTheNextIsAlsoTold() {
    EventId id = fullWithThreeWaiting();
    now = at("2026-11-16T21:30");
    actor = person("sub-1");
    registrations.leave(id);
    Registration first = of(id, "w1");
    assertThat(first.status()).isEqualTo(RegistrationStatus.NOTIFIED);
    assertThat(first.notice().exclusiveUntil()).isEqualTo(at("2026-11-17T08:30"));
    assertThat(of(id, "w2").status()).isEqualTo(RegistrationStatus.WAITING);
    assertThat(published).filteredOn(EventWaitlistNotified.class::isInstance).hasSize(1);

    // De noite non pasa nada; ás 8:30 avísase tamén á segunda.
    fill(id, at("2026-11-17T08:00"));
    assertThat(of(id, "w2").status()).isEqualTo(RegistrationStatus.WAITING);
    fill(id, at("2026-11-17T08:30"));
    assertThat(of(id, "w2").status()).isEqualTo(RegistrationStatus.NOTIFIED);
    assertThat(of(id, "w2").notice().exclusiveUntil()).isEqualTo(at("2026-11-17T09:30"));
    assertThat(of(id, "w3").status()).isEqualTo(RegistrationStatus.WAITING);
  }

  @Test
  void theFirstCanStillConfirmLateAndTheOthersGoBackToWaitingInTheirPlace() {
    EventId id = fullWithThreeWaiting();
    now = at("2026-11-17T10:00");
    actor = person("sub-1");
    registrations.leave(id);
    fill(id, at("2026-11-17T11:05"));
    assertThat(of(id, "w2").status()).isEqualTo(RegistrationStatus.NOTIFIED);
    now = at("2026-11-17T11:05");
    actor = person("w1");
    RegistrationView confirmed = registrations.confirm(id, null);
    assertThat(confirmed.registration().confirmed()).isTrue();
    Registration second = of(id, "w2");
    assertThat(second.status()).isEqualTo(RegistrationStatus.WAITING);
    assertThat(second.coveredAt()).isEqualTo(now);
    actor = person("w2");
    assertThat(registrations.mine(id).orElseThrow().position()).isEqualTo(1);
    assertThat(code(() -> registrations.confirm(id, null))).isEqualTo("event.not-notified");
  }

  @Test
  void twoFreeSeatsTellTwoPeopleAtOnce() {
    EventId id = scheduled(2);
    registrations.join(id);
    actor = person("sub-x");
    registrations.join(id);
    for (String s : List.of("w1", "w2", "w3")) {
      actor = person(s);
      registrations.join(id);
    }
    actor = person("sub-admin");
    EventCommand more = activityCommand(4, false, FACILITY, null, null, Set.of());
    events.update(id, 0, more);
    assertThat(of(id, "w1").status()).isEqualTo(RegistrationStatus.NOTIFIED);
    assertThat(of(id, "w2").status()).isEqualTo(RegistrationStatus.NOTIFIED);
    assertThat(of(id, "w3").status()).isEqualTo(RegistrationStatus.WAITING);
  }

  @Test
  void whenAToldPersonLeavesWithinHerHourTheNextIsToldAtOnce() {
    EventId id = fullWithThreeWaiting();
    now = at("2026-11-17T10:00");
    actor = person("sub-1");
    registrations.leave(id);
    now = at("2026-11-17T10:20");
    actor = person("w1");
    registrations.leave(id);
    assertThat(of(id, "w2").status()).isEqualTo(RegistrationStatus.NOTIFIED);
    assertThat(of(id, "w2").notice().exclusiveUntil()).isEqualTo(at("2026-11-17T11:20"));
    assertThat(of(id, "w3").status()).isEqualTo(RegistrationStatus.WAITING);
  }

  @Test
  void theListStartsWithEveryonePresentAndOnlyCountsOnceSaved() {
    EventId id = scheduled(10);
    Registration a = registrations.join(id).registration();
    actor = person("sub-2");
    Registration b = registrations.join(id).registration();
    EventSession first = repo.sessionsOf(id).getFirst();
    EventAttendanceUseCase.SessionAttendance before = attendance.attendance(id, first.id());
    assertThat(before.taken()).isFalse();
    assertThat(before.attendees()).allMatch(m -> Boolean.TRUE.equals(m.present()));
    assertThat(attendance.summary(id)).allMatch(s -> s.sessions() == 0 && s.present() == 0);

    assertThat(code(() -> attendance.mark(id, first.id(), Map.of())))
        .isEqualTo("event.attendance-too-early");
    now = first.startsAt().minus(Duration.ofMinutes(30));
    Map<UUID, Boolean> marks = new HashMap<>();
    marks.put(b.id().value(), false);
    EventAttendanceUseCase.SessionAttendance saved = attendance.mark(id, first.id(), marks);
    assertThat(saved.taken()).isTrue();
    assertThat(saved.takenAt()).isEqualTo(now);
    assertThat(saved.takenByName()).isEqualTo("Persoa sub-2");
    assertThat(saved.attendees())
        .extracting(m -> m.registration().id(), EventAttendanceUseCase.AttendeeMark::present)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(a.id(), true),
            org.assertj.core.groups.Tuple.tuple(b.id(), false));
    assertThat(attendance.summary(id))
        .extracting(EventAttendanceUseCase.AttendeeSummary::present)
        .containsExactly(1, 0);
    assertThat(audited).filteredOn(e -> e.action().equals("event.attendance")).hasSize(1);
    assertThat(audited.toString()).doesNotContain("example.org");
    assertThat(attendance.today())
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.session().attendanceTakenAt()).isNotNull();
              assertThat(t.present()).isEqualTo(1);
              assertThat(t.absent()).isEqualTo(1);
            });
    now = at("2026-11-17T10:00");
    assertThat(attendance.today()).isEmpty();
  }
}
