// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain.season;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.booking.domain.BookableUse;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingRules;
import gal.furar.booking.domain.RentalMode;
import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Que días crea unha tempada (ADR-0039, punto 8), sen base nin reloxo. */
class SeriesPlannerTest {

  private static final UUID FACILITY = UUID.randomUUID();
  private static final UUID USE = UUID.randomUUID();
  private static final UUID PART_A = UUID.randomUUID();
  private static final UUID PART_B = UUID.randomUUID();
  private static final Instant PAST = Instant.parse("2026-01-01T00:00:00Z");

  private static BookableUse use(RentalMode mode) {
    return new BookableUse(
        USE,
        mode,
        60,
        null,
        mode == RentalMode.PARTS ? List.of(PART_A, PART_B) : List.of(),
        List.of(),
        127,
        false,
        false,
        BookingRules.none());
  }

  private static SeriesSchedule schedule(
      List<Integer> days, int start, int end, String first, String last) {
    return SeriesSchedule.of(
        FACILITY, USE, null, days, start, end, LocalDate.parse(first), LocalDate.parse(last));
  }

  /** Aberto todo o día cada día, sen festivos nin bloqueos. */
  private static List<SeriesPlanner.Day> open(String first, String last) {
    List<SeriesPlanner.Day> days = new ArrayList<>();
    for (LocalDate d = LocalDate.parse(first);
        !d.isAfter(LocalDate.parse(last));
        d = d.plusDays(1)) {
      days.add(day(d, false, null, List.of()));
    }
    return days;
  }

  private static SeriesPlanner.Day day(
      LocalDate d, boolean holiday, UUID exclusive, List<SeriesPlanner.Block> blocks) {
    Instant from = d.atStartOfDay(SeriesSchedule.ZONE).toInstant();
    Instant to = d.plusDays(1).atStartOfDay(SeriesSchedule.ZONE).toInstant();
    return new SeriesPlanner.Day(
        d, holiday, List.of(new SeriesPlanner.Span(from, to)), blocks, exclusive);
  }

  private static Instant at(String date, int minute) {
    return ZonedDateTime.ofLocal(
            LocalDate.parse(date).atStartOfDay().plusMinutes(minute), SeriesSchedule.ZONE, null)
        .toInstant();
  }

  @Test
  void keepsTheWallClockAcrossTheChangeOfHour() {
    // Domingos 19:00–20:30 arredor do 25 de outubro de 2026 (cambio a hora de inverno).
    SeriesPlan plan =
        SeriesPlanner.plan(
            schedule(List.of(7), 1140, 1230, "2026-10-18", "2026-11-01"),
            use(RentalMode.WHOLE),
            open("2026-10-18", "2026-11-01"),
            List.of(),
            Map.of(),
            PAST);

    assertThat(plan.sessions())
        .extracting(SeriesPlan.Session::start)
        .containsExactly(
            Instant.parse("2026-10-18T17:00:00Z"),
            Instant.parse("2026-10-25T18:00:00Z"),
            Instant.parse("2026-11-01T18:00:00Z"));
    assertThat(plan.sessions().get(1).end()).isEqualTo(Instant.parse("2026-10-25T19:30:00Z"));
  }

  @Test
  void theSpringChangeKeepsSevenPmAndTheMissingHourIsClosed() {
    // 28 de marzo de 2027: ás 19:00 xa é hora de verán (17:00 UTC).
    SeriesPlan evening =
        SeriesPlanner.plan(
            schedule(List.of(7), 1140, 1230, "2027-03-21", "2027-03-28"),
            use(RentalMode.WHOLE),
            open("2027-03-21", "2027-03-28"),
            List.of(),
            Map.of(),
            PAST);
    assertThat(evening.sessions())
        .extracting(SeriesPlan.Session::start)
        .containsExactly(
            Instant.parse("2027-03-21T18:00:00Z"), Instant.parse("2027-03-28T17:00:00Z"));

    // A sesión enteira na hora que non existe (02:00–03:00) non se crea: pechado.
    for (int end : List.of(165, 180)) {
      SeriesPlan gap =
          SeriesPlanner.plan(
              schedule(List.of(7), 120, end, "2027-03-28", "2027-03-28"),
              use(RentalMode.WHOLE),
              open("2027-03-28", "2027-03-28"),
              List.of(),
              Map.of(),
              PAST);
      assertThat(gap.sessions()).isEmpty();
      assertThat(gap.closed()).containsExactly(LocalDate.parse("2027-03-28"));
    }
  }

  @Test
  void holidaysAreSkippedAndListed() {
    // Venres do 22 de decembro de 2026 ao 12 de xaneiro de 2027, co 1 de xaneiro festivo.
    List<SeriesPlanner.Day> days = new ArrayList<>(open("2026-12-22", "2027-01-12"));
    days.replaceAll(
        d ->
            d.date().equals(LocalDate.parse("2027-01-01"))
                ? day(d.date(), true, null, List.of())
                : d);

    SeriesPlan plan =
        SeriesPlanner.plan(
            schedule(List.of(5), 1080, 1170, "2026-12-22", "2027-01-12"),
            use(RentalMode.WHOLE),
            days,
            List.of(),
            Map.of(),
            PAST);

    assertThat(plan.holidays()).containsExactly(LocalDate.parse("2027-01-01"));
    assertThat(plan.sessions())
        .extracting(SeriesPlan.Session::date)
        .containsExactly(LocalDate.parse("2026-12-25"), LocalDate.parse("2027-01-08"));
    assertThat(plan.skipped()).containsExactly(LocalDate.parse("2027-01-01"));
    assertThat(plan.conflictDates()).isEmpty();
  }

  @Test
  void theLeapDayIsATuesdayIn2028() {
    SeriesPlan plan =
        SeriesPlanner.plan(
            schedule(List.of(2), 600, 660, "2028-02-01", "2028-03-31"),
            use(RentalMode.WHOLE),
            open("2028-02-01", "2028-03-31"),
            List.of(),
            Map.of(),
            PAST);

    assertThat(plan.sessions()).hasSize(9);
    assertThat(plan.sessions())
        .extracting(SeriesPlan.Session::date)
        .contains(LocalDate.parse("2028-02-29"));
  }

  @Test
  void midnightIsTheStartOfTheNextDay() {
    SeriesPlan plan =
        SeriesPlanner.plan(
            schedule(List.of(1), 1380, 1440, "2026-11-16", "2026-11-16"),
            use(RentalMode.WHOLE),
            open("2026-11-16", "2026-11-16"),
            List.of(),
            Map.of(),
            PAST);

    assertThat(plan.sessions().getFirst().end()).isEqualTo(at("2026-11-17", 0));
  }

  @Test
  void eachDayFallsInTheFirstClassThatApplies() {
    UUID other = UUID.randomUUID();
    UUID booking = UUID.randomUUID();
    UUID session = UUID.randomUUID();
    // Do luns 16 ao domingo 22 de novembro de 2026, ás 18:00–19:00.
    List<SeriesPlanner.Day> days = new ArrayList<>();
    days.add(day(LocalDate.parse("2026-11-16"), true, null, List.of()));
    days.add(
        new SeriesPlanner.Day(LocalDate.parse("2026-11-17"), false, List.of(), List.of(), null));
    days.add(day(LocalDate.parse("2026-11-18"), false, other, List.of()));
    days.add(
        new SeriesPlanner.Day(
            LocalDate.parse("2026-11-19"),
            false,
            List.of(new SeriesPlanner.Span(at("2026-11-19", 540), at("2026-11-19", 1110))),
            List.of(),
            null));
    days.add(
        day(
            LocalDate.parse("2026-11-20"),
            false,
            null,
            List.of(
                new SeriesPlanner.Block(
                    at("2026-11-20", 1050), at("2026-11-20", 1110), "Limpeza"))));
    days.add(day(LocalDate.parse("2026-11-21"), false, null, List.of()));
    days.add(day(LocalDate.parse("2026-11-22"), false, null, List.of()));
    List<SeriesPlanner.Occupant> occupants =
        List.of(
            new SeriesPlanner.Occupant(
                booking, USE, null, at("2026-11-21", 1110), at("2026-11-21", 1170), "Ana", false),
            new SeriesPlanner.Occupant(
                session, USE, null, at("2026-11-22", 1020), at("2026-11-22", 1110), "Club", true));

    SeriesPlan plan =
        SeriesPlanner.plan(
            schedule(List.of(1, 2, 3, 4, 5, 6, 7), 1080, 1140, "2026-11-16", "2026-11-22"),
            use(RentalMode.WHOLE),
            days,
            occupants,
            Map.of(other, "Natación"),
            PAST);

    assertThat(plan.holidays()).containsExactly(LocalDate.parse("2026-11-16"));
    assertThat(plan.closed()).containsExactly(LocalDate.parse("2026-11-17"));
    assertThat(plan.conflicts())
        .extracting(SeriesConflict::kind)
        .containsExactly(
            SeriesConflict.Kind.OTHER_USE_EXCLUSIVE,
            SeriesConflict.Kind.OUT_OF_HOURS,
            SeriesConflict.Kind.BLOCK,
            SeriesConflict.Kind.BOOKING,
            SeriesConflict.Kind.SESSION);
    assertThat(plan.conflicts().getFirst().label()).isEqualTo("Natación");
    assertThat(plan.conflicts().get(2).label()).isEqualTo("Limpeza");
    assertThat(plan.conflicts().get(3).bookingId()).isEqualTo(booking);
    assertThat(plan.conflicts().get(3).name()).isEqualTo("Ana");
    assertThat(plan.conflicts().get(4).bookingId()).isEqualTo(session);
    assertThat(plan.sessions()).isEmpty();
  }

  @Test
  void aUseThatIsNotAvailableIsNotClosed() {
    BookableUse weekdaysOnly =
        new BookableUse(
            USE,
            RentalMode.WHOLE,
            60,
            null,
            List.of(),
            List.of(),
            31,
            false,
            false,
            BookingRules.none());

    SeriesPlan plan =
        SeriesPlanner.plan(
            schedule(List.of(5, 6), 1080, 1140, "2026-11-20", "2026-11-21"),
            weekdaysOnly,
            open("2026-11-20", "2026-11-21"),
            List.of(),
            Map.of(),
            PAST);

    assertThat(plan.useOff()).containsExactly(LocalDate.parse("2026-11-21"));
    assertThat(plan.closed()).isEmpty();
    assertThat(plan.sessions()).hasSize(1);
  }

  @Test
  void byPartsOnlyTheSamePartOrAnotherUseCollides() {
    SeriesSchedule onA =
        SeriesSchedule.of(
            FACILITY,
            USE,
            PART_A,
            List.of(1),
            1080,
            1140,
            LocalDate.parse("2026-11-16"),
            LocalDate.parse("2026-11-16"));
    Instant from = at("2026-11-16", 1080);
    Instant to = at("2026-11-16", 1140);

    SeriesPlan otherPart =
        SeriesPlanner.plan(
            onA,
            use(RentalMode.PARTS),
            open("2026-11-16", "2026-11-16"),
            List.of(
                new SeriesPlanner.Occupant(UUID.randomUUID(), USE, PART_B, from, to, "Bea", false)),
            Map.of(),
            PAST);
    SeriesPlan samePart =
        SeriesPlanner.plan(
            onA,
            use(RentalMode.PARTS),
            open("2026-11-16", "2026-11-16"),
            List.of(
                new SeriesPlanner.Occupant(UUID.randomUUID(), USE, PART_A, from, to, "Bea", false)),
            Map.of(),
            PAST);
    SeriesPlan otherUse =
        SeriesPlanner.plan(
            onA,
            use(RentalMode.PARTS),
            open("2026-11-16", "2026-11-16"),
            List.of(
                new SeriesPlanner.Occupant(
                    UUID.randomUUID(), UUID.randomUUID(), null, from, to, "Rui", false)),
            Map.of(),
            PAST);

    assertThat(otherPart.sessions()).hasSize(1);
    assertThat(samePart.sessions()).isEmpty();
    assertThat(otherUse.sessions()).isEmpty();
  }

  @Test
  void daysThatAlreadyStartedAreLeftOut() {
    SeriesPlan plan =
        SeriesPlanner.plan(
            schedule(List.of(1, 2), 1080, 1140, "2026-11-16", "2026-11-17"),
            use(RentalMode.WHOLE),
            open("2026-11-16", "2026-11-17"),
            List.of(),
            Map.of(),
            at("2026-11-16", 1090));

    assertThat(plan.sessions())
        .extracting(SeriesPlan.Session::date)
        .containsExactly(LocalDate.parse("2026-11-17"));
    assertThat(plan.skipped()).isEmpty();
  }

  @Test
  void theShapeIsCheckedBeforePlanning() {
    LocalDate today = LocalDate.parse("2026-11-12");
    assertThatThrownBy(() -> schedule(List.of(), 600, 660, "2026-11-16", "2026-12-16").check(today))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(BookingError.SERIES_WEEKDAYS_REQUIRED));
    assertThatThrownBy(
            () -> schedule(List.of(1), 610, 660, "2026-11-16", "2026-12-16").check(today))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(BookingError.SERIES_TIME_INVALID));
    assertThatThrownBy(
            () -> schedule(List.of(1), 660, 660, "2026-11-16", "2026-12-16").check(today))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(BookingError.SERIES_TIME_INVALID));
    assertThatThrownBy(
            () -> schedule(List.of(1), 600, 660, "2026-11-11", "2026-12-16").check(today))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(BookingError.SERIES_RANGE_INVALID));
    assertThatThrownBy(
            () -> schedule(List.of(1), 600, 660, "2026-11-16", "2027-11-17").check(today))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(BookingError.SERIES_RANGE_INVALID));
    schedule(List.of(1), 0, 1440, "2026-11-12", "2027-11-12").check(today);
  }
}
