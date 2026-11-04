// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tramos da semana: fin despois do inicio, ata as 24:00, de cuarto en cuarto e sen solapes. */
class WeekHoursTest {

  static DayHoursSpec day(ScheduleDay day, TimeRange... ranges) {
    return new DayHoursSpec(day, List.of(ranges));
  }

  static TimeRange t(int start, int end) {
    return new TimeRange(start, end);
  }

  static DomainException failure(List<DayHoursSpec> week) {
    try {
      WeekHours.of(week);
    } catch (DomainException e) {
      return e;
    }
    throw new AssertionError("valid week: " + week);
  }

  static List<String> fields(DomainException e) {
    return e.violations().stream().map(FieldViolation::field).toList();
  }

  @Test
  void validRangesAreSortedAndMissingDaysAreClosed() {
    WeekHours week = WeekHours.of(List.of(day(ScheduleDay.MONDAY, t(960, 1380), t(480, 840))));
    assertThat(week.hours(ScheduleDay.MONDAY)).containsExactly(t(480, 840), t(960, 1380));
    assertThat(week.hours(ScheduleDay.TUESDAY)).isEmpty();
    assertThat(week.hours(ScheduleDay.HOLIDAYS)).isEmpty();
    assertThat(week.days()).hasSize(8);
    assertThat(week.hasAnyInterval()).isTrue();
    assertThat(WeekHours.closed().hasAnyInterval()).isFalse();
  }

  @Test
  void endMustComeAfterStart() {
    DomainException e = failure(List.of(day(ScheduleDay.MONDAY, t(600, 600), t(600, 540))));
    assertThat(e.errorCode()).isEqualTo(ScheduleError.INTERVAL_INVALID);
    assertThat(fields(e)).containsExactly("week[0].intervals[0]", "week[0].intervals[1]");
  }

  @Test
  void midnightIsTheLastEndAndNothingGoesPastIt() {
    assertThat(WeekHours.of(List.of(day(ScheduleDay.SUNDAY, t(0, 1440)))).hasAnyInterval())
        .isTrue();
    assertThat(failure(List.of(day(ScheduleDay.SUNDAY, t(1380, 1455)))).errorCode())
        .isEqualTo(ScheduleError.INTERVAL_INVALID);
    assertThat(failure(List.of(day(ScheduleDay.SUNDAY, t(-15, 60)))).errorCode())
        .isEqualTo(ScheduleError.INTERVAL_INVALID);
  }

  @Test
  void minutesGoInQuarters() {
    DomainException e = failure(List.of(day(ScheduleDay.FRIDAY, t(485, 600), t(900, 1010))));
    assertThat(e.errorCode()).isEqualTo(ScheduleError.INTERVAL_INVALID);
    assertThat(e.params()).containsEntry("step", 15);
    assertThat(fields(e)).containsExactly("week[0].intervals[0]", "week[0].intervals[1]");
  }

  @Test
  void rangesOfTheSameDayCannotOverlapButCanTouch() {
    WeekHours touching = WeekHours.of(List.of(day(ScheduleDay.MONDAY, t(480, 840), t(840, 1320))));
    assertThat(touching.hours(ScheduleDay.MONDAY)).hasSize(2);

    DomainException e =
        failure(
            List.of(
                day(ScheduleDay.MONDAY, t(480, 840)),
                day(ScheduleDay.TUESDAY, t(480, 840), t(600, 900))));
    assertThat(e.errorCode()).isEqualTo(ScheduleError.INTERVAL_OVERLAP);
    assertThat(fields(e)).containsExactly("week[1].intervals[1]");
  }

  @Test
  void sameRangeOnDifferentDaysIsFine() {
    WeekHours week =
        WeekHours.of(
            List.of(day(ScheduleDay.MONDAY, t(480, 840)), day(ScheduleDay.TUESDAY, t(480, 840))));
    assertThat(week.hours(ScheduleDay.TUESDAY)).containsExactly(t(480, 840));
  }

  @Test
  void invalidRangesAreReportedBeforeOverlaps() {
    DomainException e =
        failure(List.of(day(ScheduleDay.MONDAY, t(480, 840), t(600, 900), t(700, 690))));
    assertThat(e.errorCode()).isEqualTo(ScheduleError.INTERVAL_INVALID);
    assertThat(fields(e)).containsExactly("week[0].intervals[2]");
  }

  @Test
  void aDayComesOnceAndAlwaysWithItsName() {
    assertThatThrownBy(
            () ->
                WeekHours.of(
                    List.of(day(ScheduleDay.MONDAY, t(480, 840)), day(ScheduleDay.MONDAY))))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(ScheduleError.SEASON_INVALID);
              assertThat(fields(e)).containsExactly("week[1].day");
            });
    assertThat(failure(List.of(new DayHoursSpec(null, List.of()))).errorCode())
        .isEqualTo(ScheduleError.SEASON_INVALID);
  }

  @Test
  void atMostTenRangesADay() {
    TimeRange[] eleven = new TimeRange[11];
    for (int i = 0; i < eleven.length; i++) {
      eleven[i] = t(i * 60, i * 60 + 30);
    }
    DomainException e = failure(List.of(day(ScheduleDay.MONDAY, eleven)));
    assertThat(e.errorCode()).isEqualTo(ScheduleError.SEASON_INVALID);
    assertThat(fields(e)).containsExactly("week[0].intervals");
  }

  @Test
  void holidaysRowCopiesSunday() {
    WeekHours week =
        WeekHours.of(
            List.of(day(ScheduleDay.SUNDAY, t(600, 840)), day(ScheduleDay.HOLIDAYS, t(0, 60))));
    assertThat(week.withHolidaysLikeSunday().hours(ScheduleDay.HOLIDAYS))
        .containsExactly(t(600, 840));
  }
}
