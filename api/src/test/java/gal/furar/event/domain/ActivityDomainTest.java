// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.shared.domain.DomainException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** O horario, o límite de idade, o tipo e as sesións dunha actividade (ADR-0041). */
class ActivityDomainTest {

  static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
  static final LocalDate TODAY = LocalDate.parse("2026-11-16");

  static String code(Runnable action) {
    try {
      action.run();
    } catch (DomainException e) {
      return e.errorCode().code();
    }
    throw new AssertionError("no error");
  }

  static EventSchedule schedule(
      List<Integer> days, int start, int end, LocalDate first, LocalDate last) {
    return EventSchedule.of(UUID.randomUUID(), null, days, start, end, first, last);
  }

  @Test
  void theScheduleChecksDaysTimesAndDates() {
    LocalDate next = TODAY.plusDays(1);
    assertThat(code(() -> schedule(List.of(8), 600, 660, next, next)))
        .isEqualTo("event.schedule-weekdays-required");
    assertThat(code(() -> schedule(List.of(), 600, 660, next, next).check(TODAY)))
        .isEqualTo("event.schedule-weekdays-required");
    assertThat(code(() -> schedule(List.of(1), 610, 660, next, next).check(TODAY)))
        .isEqualTo("event.schedule-time-invalid");
    assertThat(code(() -> schedule(List.of(1), 660, 660, next, next).check(TODAY)))
        .isEqualTo("event.schedule-time-invalid");
    assertThat(code(() -> schedule(List.of(1), 600, 660, TODAY.minusDays(1), next).check(TODAY)))
        .isEqualTo("event.schedule-range-invalid");
    assertThat(code(() -> schedule(List.of(1), 600, 660, next, next.plusDays(366)).check(TODAY)))
        .isEqualTo("event.schedule-range-invalid");
    schedule(List.of(1), 600, 1440, next, next.plusDays(365)).check(TODAY);
  }

  @Test
  void outsideAFacilityThereIsASessionPerDayThatHasNotStarted() {
    // Luns e mércores, 18:00–19:00, do luns 16 ao mércores 25 de novembro.
    EventSchedule s =
        EventSchedule.of(
            null, null, List.of(3, 1), 1080, 1140, TODAY, LocalDate.parse("2026-11-25"));
    List<EventSchedule.Session> sessions = s.sessionsOutside(NOW);
    assertThat(sessions)
        .extracting(EventSchedule.Session::date)
        .containsExactly(
            LocalDate.parse("2026-11-16"),
            LocalDate.parse("2026-11-18"),
            LocalDate.parse("2026-11-23"),
            LocalDate.parse("2026-11-25"));
    assertThat(sessions.getFirst().start()).isEqualTo(Instant.parse("2026-11-16T17:00:00Z"));
    assertThat(s.sessionsOutside(Instant.parse("2026-11-16T17:00:00Z"))).hasSize(3);
  }

  @Test
  void birthYearsAreOptionalAndInclusive() {
    assertThat(BirthYears.NONE.limited()).isFalse();
    BirthYears kids = new BirthYears(2014, 2016);
    assertThat(kids.allows(2014)).isTrue();
    assertThat(kids.allows(2016)).isTrue();
    assertThat(kids.allows(2017)).isFalse();
    assertThat(new BirthYears(null, 2000).allows(1950)).isTrue();
    assertThat(code(() -> kids.check(2013))).isEqualTo("event.age-not-allowed");
    assertThat(code(() -> new BirthYears(2016, 2014))).isEqualTo("event.birth-years-invalid");
    assertThat(code(() -> new BirthYears(1800, null))).isEqualTo("event.birth-years-invalid");
  }

  static EventDetails activity(Integer capacity, boolean approval) {
    EventSchedule s =
        EventSchedule.of(null, null, List.of(1), 1080, 1140, TODAY, TODAY.plusDays(60));
    return new EventDetails(
        EventText.name("Escola de atletismo", "Escuela de atletismo"),
        null,
        EventKind.SCHEDULED,
        Instant.parse("2026-11-16T17:00:00Z"),
        Instant.parse("2027-01-11T18:00:00Z"),
        EventPlace.other("Pista municipal"),
        s,
        capacity,
        2000,
        PricePeriod.MONTH,
        null,
        true,
        approval,
        BirthYears.NONE);
  }

  @Test
  void anActivityTakesPeopleUntilItEndsAndOnlyThenStopsChanging() {
    Event e = Event.create(EventId.newId(), activity(10, false), NOW);
    assertThat(e.waitlist()).isTrue();
    Instant started = Instant.parse("2026-12-01T10:00:00Z");
    assertThat(e.started(started)).isTrue();
    assertThat(e.open(started)).isTrue();
    assertThat(e.leavable(started)).isTrue();
    Instant after = Instant.parse("2027-01-11T18:00:00Z");
    assertThat(e.open(after)).isFalse();
    assertThat(code(() -> e.cancel(after))).isEqualTo("event.ended");
    assertThat(Event.create(EventId.newId(), activity(10, true), NOW).waitlist()).isFalse();
    assertThat(Event.create(EventId.newId(), activity(null, false), NOW).waitlist()).isFalse();
  }

  @Test
  void theScheduleOfAnActivityDoesNotChange() {
    Event e = Event.create(EventId.newId(), activity(10, false), NOW);
    EventDetails d = e.details();
    EventDetails moved =
        new EventDetails(
            d.name(),
            null,
            EventKind.SCHEDULED,
            d.startsAt(),
            d.endsAt(),
            d.place(),
            EventSchedule.of(null, null, List.of(2), 1080, 1140, TODAY, TODAY.plusDays(60)),
            d.capacity(),
            d.priceCents(),
            d.pricePeriod(),
            null,
            true,
            false,
            BirthYears.NONE);
    assertThat(code(() -> e.update(moved, NOW))).isEqualTo("event.schedule-not-editable");
    Event renamed =
        e.update(
            new EventDetails(
                EventText.name("Atletismo", "Atletismo"),
                null,
                EventKind.SCHEDULED,
                NOW,
                NOW,
                d.place(),
                d.schedule(),
                20,
                null,
                PricePeriod.ONCE,
                null,
                true,
                false,
                BirthYears.NONE),
            NOW);
    // As datas quedan as das sesións.
    assertThat(renamed.details().startsAt()).isEqualTo(d.startsAt());
    assertThat(renamed.details().endsAt()).isEqualTo(d.endsAt());
  }

  @Test
  void endingEarlyMovesTheEndAndIsNotRepeated() {
    Event e = Event.create(EventId.newId(), activity(10, false), NOW);
    LocalDate from = LocalDate.parse("2026-12-14");
    Instant lastLeft = Instant.parse("2026-12-07T18:00:00Z");
    Event ended = e.endFrom(from, TODAY, lastLeft, NOW);
    assertThat(ended.endedFrom()).isEqualTo(from);
    assertThat(ended.details().endsAt()).isEqualTo(lastLeft);
    assertThat(ended.endFrom(LocalDate.parse("2026-12-21"), TODAY, lastLeft, NOW)).isSameAs(ended);
    assertThat(code(() -> e.endFrom(TODAY.minusDays(1), TODAY, lastLeft, NOW)))
        .isEqualTo("event.end-invalid");
  }

  @Test
  void aSessionIsCancelledBeforeItStartsAndWithoutListAndTheListOpensWithTheMargin() {
    Instant start = Instant.parse("2026-11-20T17:00:00Z");
    EventSession s =
        new EventSession(
            UUID.randomUUID(),
            EventId.newId(),
            LocalDate.parse("2026-11-20"),
            start,
            start.plus(Duration.ofHours(1)),
            null,
            null,
            null);
    s.checkCancellable(NOW);
    assertThat(code(() -> s.checkCancellable(start))).isEqualTo("event.session-started");
    Duration margin = Duration.ofMinutes(45);
    assertThat(code(() -> s.checkAttendance(start.minus(Duration.ofMinutes(46)), margin)))
        .isEqualTo("event.attendance-too-early");
    s.checkAttendance(start.minus(margin), margin);
    s.checkAttendance(start.plus(Duration.ofDays(3)), margin);
    assertThat(code(() -> s.cancel(NOW).checkAttendance(start, margin)))
        .isEqualTo("event.session-cancelled");
  }
}
