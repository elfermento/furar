// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Minutos abertos por día e por día × hora, co cambio de hora (plan do dominio 3, §1.2.3). */
class OpeningMinutesTest {

  /** Un tramo coa hora de reloxo de Madrid (no test só: o dominio traballa con instantes). */
  private static TimeSpan local(String from, String to) {
    return new TimeSpan(at(from), at(to));
  }

  private static Instant at(String localDateTime) {
    return LocalDateTime.parse(localDateTime).atZone(ReportPeriod.ZONE).toInstant();
  }

  @Test
  void springForwardDayFromMidnightToFourIsThreeHours() {
    OpeningMinutes minutes =
        OpeningMinutes.of(List.of(local("2026-03-29T00:00", "2026-03-29T04:00")));
    assertThat(minutes.total()).isEqualTo(180);
    assertThat(minutes.onDay(LocalDate.parse("2026-03-29"))).isEqualTo(180);
    assertThat(minutes.at(DayOfWeek.SUNDAY, 0)).isEqualTo(60);
    assertThat(minutes.at(DayOfWeek.SUNDAY, 1)).isEqualTo(60);
    assertThat(minutes.at(DayOfWeek.SUNDAY, 2)).isZero();
    assertThat(minutes.at(DayOfWeek.SUNDAY, 3)).isEqualTo(60);
  }

  @Test
  void fallBackDayFromMidnightToFourIsFiveHoursWithTwoAtTwo() {
    OpeningMinutes minutes =
        OpeningMinutes.of(List.of(local("2026-10-25T00:00", "2026-10-25T04:00")));
    assertThat(minutes.total()).isEqualTo(300);
    assertThat(minutes.onDay(LocalDate.parse("2026-10-25"))).isEqualTo(300);
    assertThat(minutes.at(DayOfWeek.SUNDAY, 1)).isEqualTo(60);
    assertThat(minutes.at(DayOfWeek.SUNDAY, 2)).isEqualTo(120);
    assertThat(minutes.at(DayOfWeek.SUNDAY, 3)).isEqualTo(60);
  }

  @Test
  void cutsByClockHourAndAddsSpans() {
    OpeningMinutes minutes =
        OpeningMinutes.of(
            List.of(
                local("2026-10-28T09:30", "2026-10-28T11:15"),
                // Outra instalación á mesma hora: súmase.
                local("2026-10-28T10:00", "2026-10-28T11:00"),
                // O mércores seguinte.
                local("2026-11-04T10:00", "2026-11-04T10:30")));
    assertThat(minutes.total()).isEqualTo(105 + 60 + 30);
    assertThat(minutes.at(DayOfWeek.WEDNESDAY, 9)).isEqualTo(30);
    assertThat(minutes.at(DayOfWeek.WEDNESDAY, 10)).isEqualTo(60 + 60 + 30);
    assertThat(minutes.at(DayOfWeek.WEDNESDAY, 11)).isEqualTo(15);
    assertThat(minutes.at(DayOfWeek.THURSDAY, 10)).isZero();
    assertThat(minutes.perDay())
        .containsOnlyKeys(LocalDate.parse("2026-10-28"), LocalDate.parse("2026-11-04"));
    assertThat(minutes.onDay(LocalDate.parse("2026-10-28"))).isEqualTo(165);
    assertThat(minutes.onDay(LocalDate.parse("2026-10-29"))).isZero();
  }

  @Test
  void aSpanAcrossMidnightCountsInEachDay() {
    OpeningMinutes minutes =
        OpeningMinutes.of(List.of(local("2026-10-27T22:30", "2026-10-28T00:30")));
    assertThat(minutes.onDay(LocalDate.parse("2026-10-27"))).isEqualTo(90);
    assertThat(minutes.onDay(LocalDate.parse("2026-10-28"))).isEqualTo(30);
    assertThat(minutes.at(DayOfWeek.TUESDAY, 23)).isEqualTo(60);
    assertThat(minutes.at(DayOfWeek.WEDNESDAY, 0)).isEqualTo(30);
  }

  @Test
  void emptyAndZeroLengthSpans() {
    assertThat(OpeningMinutes.of(List.of()).total()).isZero();
    OpeningMinutes zero = OpeningMinutes.of(List.of(local("2026-10-28T10:00", "2026-10-28T10:00")));
    assertThat(zero.total()).isZero();
    assertThat(zero.perDay()).isEmpty();
  }

  @Test
  void rejectsBadInput() {
    OpeningMinutes minutes = OpeningMinutes.of(List.of());
    assertThatThrownBy(() -> minutes.at(DayOfWeek.MONDAY, 24))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> minutes.at(DayOfWeek.MONDAY, -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> local("2026-10-28T10:00", "2026-10-28T09:00"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
