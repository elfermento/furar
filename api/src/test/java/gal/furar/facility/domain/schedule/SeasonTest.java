// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.facility.domain.FacilityId;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tempadas: nome, datas, copia da semana, solape e días que perden horas (P6, P8). */
class SeasonTest {

  static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
  static final FacilityId FACILITY = FacilityId.newId();

  static SeasonSpec spec(String name, String from, String to) {
    return new SeasonSpec(name, LocalDate.parse(from), to == null ? null : LocalDate.parse(to));
  }

  static Season season(String from, String to) {
    return Season.create(SeasonId.newId(), FACILITY, spec("Inverno", from, to), null, NOW);
  }

  static WeekHours mondays(int start, int end) {
    return WeekHours.of(
        List.of(new DayHoursSpec(ScheduleDay.MONDAY, List.of(new TimeRange(start, end)))));
  }

  @Test
  void newSeasonStartsClosedWithTrimmedName() {
    Season s =
        Season.create(
            SeasonId.newId(), FACILITY, spec("  Inverno  ", "2026-09-01", "2027-06-30"), null, NOW);
    assertThat(s.name()).isEqualTo("Inverno");
    assertThat(s.week()).isEqualTo(WeekHours.closed());
    assertThat(s.version()).isNull();
  }

  @Test
  void copyTakesTheWeekAndFillsHolidaysWithSunday() {
    WeekHours source =
        WeekHours.of(
            List.of(
                new DayHoursSpec(ScheduleDay.MONDAY, List.of(new TimeRange(480, 1380))),
                new DayHoursSpec(ScheduleDay.SUNDAY, List.of(new TimeRange(600, 840))),
                new DayHoursSpec(ScheduleDay.HOLIDAYS, List.of(new TimeRange(0, 60)))));
    Season winter =
        season("2026-09-01", "2027-06-30")
            .update(spec("Inverno", "2026-09-01", "2027-06-30"), source, NOW);
    Season summer =
        Season.create(
            SeasonId.newId(), FACILITY, spec("Verán", "2027-07-01", "2027-08-31"), winter, NOW);
    assertThat(summer.week().hours(ScheduleDay.MONDAY)).containsExactly(new TimeRange(480, 1380));
    assertThat(summer.week().hours(ScheduleDay.HOLIDAYS)).containsExactly(new TimeRange(600, 840));
  }

  @Test
  void nameAndDatesAreChecked() {
    assertThatThrownBy(
            () ->
                Season.create(
                    SeasonId.newId(), FACILITY, spec(" ", "2027-01-02", "2027-01-01"), null, NOW))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(ScheduleError.SEASON_INVALID);
              assertThat(e.violations().stream().map(FieldViolation::field))
                  .containsExactly("name", "endDate");
            });
    assertThatThrownBy(() -> season("2026-01-01", "2036-01-02"))
        .isInstanceOf(DomainException.class);
    assertThat(season("2026-01-01", "2026-01-01").covers(LocalDate.parse("2026-01-01"))).isTrue();
  }

  @Test
  void withoutEndCoversEveryDayFromItsStart() {
    Season always = season("2026-01-01", null);
    assertThat(always.openEnded()).isTrue();
    assertThat(always.endDate()).isNull();
    assertThat(always.covers(LocalDate.parse("2025-12-31"))).isFalse();
    assertThat(always.covers(LocalDate.parse("2026-01-01"))).isTrue();
    assertThat(always.covers(LocalDate.parse("2099-12-31"))).isTrue();
    assertThat(always.lastDay(LocalDate.parse("2036-11-04"))).isEqualTo("2036-11-04");
    assertThat(season("2026-01-01", "2026-12-31").lastDay(LocalDate.parse("2036-11-04")))
        .isEqualTo("2026-12-31");
  }

  @Test
  void withoutEndOverlapsEverySeasonFromItsStart() {
    Season always = season("2026-01-01", null);
    assertThat(always.overlaps(season("2030-07-01", "2030-08-31"))).isTrue();
    assertThat(season("2030-07-01", "2030-08-31").overlaps(always)).isTrue();
    assertThat(always.overlaps(season("2025-01-01", "2025-12-31"))).isFalse();
    assertThat(always.overlaps(season("2025-01-01", "2026-01-01"))).isTrue();
    assertThat(always.overlaps(season("2028-01-01", null))).isTrue();
  }

  @Test
  void withoutEndCountsAsScheduleOnceItHasHours() {
    LocalDate today = LocalDate.parse("2026-10-25");
    Season always = season("2020-01-01", null);
    assertThat(always.countsAsSchedule(today)).isFalse();
    assertThat(
            always
                .update(spec("Todo o ano", "2020-01-01", null), mondays(480, 600), NOW)
                .countsAsSchedule(today))
        .isTrue();
  }

  @Test
  void overlapIncludesBothEnds() {
    Season winter = season("2026-09-01", "2027-06-30");
    assertThat(winter.overlaps(season("2027-06-30", "2027-08-31"))).isTrue();
    assertThat(winter.overlaps(season("2027-07-01", "2027-08-31"))).isFalse();
    assertThat(winter.overlaps(season("2026-01-01", "2026-09-01"))).isTrue();
    assertThat(winter.overlaps(winter)).isFalse();
    Season elsewhere =
        Season.create(
            SeasonId.newId(),
            FacilityId.newId(),
            spec("Inverno", "2026-09-01", "2027-06-30"),
            null,
            NOW);
    assertThat(winter.overlaps(elsewhere)).isFalse();
  }

  @Test
  void countsAsScheduleWithHoursAndAnEndFromToday() {
    LocalDate today = LocalDate.parse("2026-10-25");
    Season empty = season("2026-09-01", "2027-06-30");
    assertThat(empty.countsAsSchedule(today)).isFalse();
    Season open = empty.update(spec("Inverno", "2026-09-01", "2027-06-30"), mondays(480, 600), NOW);
    assertThat(open.countsAsSchedule(today)).isTrue();
    Season endsToday =
        open.update(spec("Inverno", "2026-09-01", "2026-10-25"), mondays(480, 600), NOW);
    assertThat(endsToday.countsAsSchedule(today)).isTrue();
    Season past = open.update(spec("Inverno", "2026-09-01", "2026-10-24"), mondays(480, 600), NOW);
    assertThat(past.countsAsSchedule(today)).isFalse();
  }

  @Test
  void lostHoursAreOnlyTheMinutesThatCloseOnTheDroppedDays() {
    Season before =
        season("2026-09-01", "2027-06-30")
            .update(spec("Inverno", "2026-09-01", "2027-06-30"), mondays(480, 600), NOW);
    // Renomear e alongar, ou abrir máis: non se perde nada.
    assertThat(lost(before, spec("Outro nome", "2026-08-01", "2027-07-31"), mondays(480, 600)))
        .isEmpty();
    assertThat(lost(before, spec("Inverno", "2026-09-01", "2027-06-30"), mondays(480, 660)))
        .isEmpty();
    // Abrir máis tarde: só de 8:00 a 9:00 de cada luns.
    List<OpeningHours.Slot> later =
        lost(before, spec("Inverno", "2026-09-01", "2027-06-30"), mondays(540, 600));
    assertThat(later).isNotEmpty().allMatch(s -> s.range().equals(new TimeRange(480, 540)));
    assertThat(later.getFirst().date()).isEqualTo(LocalDate.parse("2026-09-07"));
    assertThat(later.getLast().date()).isEqualTo(LocalDate.parse("2027-06-28"));
    // Acurtar polos dous extremos: só os luns de setembro de 2026 e de xuño de 2027, nada no medio.
    List<OpeningHours.Slot> ends =
        lost(before, spec("Inverno", "2026-10-01", "2027-05-31"), mondays(480, 600));
    assertThat(ends)
        .extracting(OpeningHours.Slot::date)
        .allMatch(
            d ->
                d.isBefore(LocalDate.parse("2026-10-01"))
                    || d.isAfter(LocalDate.parse("2027-05-31")))
        .contains(LocalDate.parse("2026-09-07"), LocalDate.parse("2027-06-28"))
        .hasSize(4 + 4);
    assertThat(ends.getFirst().start()).isEqualTo(Instant.parse("2026-09-07T06:00:00Z"));
  }

  /** Tramos perdidos ao cambiar a tempada, sen excepcións nin festivos. */
  private static List<OpeningHours.Slot> lost(Season before, SeasonSpec spec, WeekHours week) {
    Season after = before.update(spec, week, NOW);
    return new OpeningHours(List.of(after), List.of(), Set.of())
        .lostSince(
            new OpeningHours(List.of(before), List.of(), Set.of()),
            before.startDate(),
            before.endDate(),
            OpeningHours.ZONE);
  }
}
