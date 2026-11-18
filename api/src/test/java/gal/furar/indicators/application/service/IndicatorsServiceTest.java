// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.facility.UseSummary;
import gal.furar.facility.UseTerms;
import gal.furar.indicators.application.port.in.IndicatorsQuery;
import gal.furar.indicators.domain.PeriodKind;
import gal.furar.shared.domain.DomainException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** As regras sen base de datos do panel: os límites das datas e os días dun uso. */
class IndicatorsServiceTest {

  static final LocalDate TODAY = LocalDate.parse("2026-10-28");

  static IndicatorsQuery query(
      PeriodKind kind, @Nullable LocalDate date, @Nullable LocalDate from, @Nullable LocalDate to) {
    return new IndicatorsQuery(kind, date, from, to, null, List.of());
  }

  @Test
  void anchorsTheNaturalUnitOnTodayOrTheDate() {
    assertThat(IndicatorsService.period(query(PeriodKind.MONTH, null, null, null), TODAY).from())
        .isEqualTo(LocalDate.parse("2026-10-01"));
    assertThat(
            IndicatorsService.period(
                    query(PeriodKind.YEAR, LocalDate.parse("2000-01-01"), null, null), TODAY)
                .to())
        .isEqualTo(LocalDate.parse("2000-12-31"));
    assertThat(
            IndicatorsService.period(query(PeriodKind.WEEK, TODAY.plusYears(2), null, null), TODAY)
                .days())
        .isEqualTo(7);
  }

  @Test
  void rejectsDatesOutsideTheRangeAsAPeriodError() {
    for (IndicatorsQuery q :
        List.of(
            query(PeriodKind.MONTH, LocalDate.parse("1999-12-31"), null, null),
            query(PeriodKind.MONTH, LocalDate.of(0, 1, 1), null, null),
            query(PeriodKind.YEAR, LocalDate.of(9999, 12, 31), null, null),
            query(PeriodKind.WEEK, TODAY.plusYears(2).plusDays(1), null, null),
            query(PeriodKind.CUSTOM, null, LocalDate.parse("1999-12-31"), TODAY),
            query(PeriodKind.CUSTOM, null, TODAY, TODAY.plusYears(3)),
            query(PeriodKind.CUSTOM, null, TODAY, TODAY.minusDays(1)),
            query(PeriodKind.CUSTOM, null, null, TODAY),
            query(PeriodKind.CUSTOM, null, TODAY, TODAY.plusDays(366)))) {
      assertThatThrownBy(() -> IndicatorsService.period(q, TODAY))
          .as(q.toString())
          .isInstanceOf(DomainException.class)
          .hasMessage("indicators.period-invalid");
    }
  }

  @Test
  void aUseCountsOnItsWeekdaysAndDatesEvenWhenArchived() {
    UseSummary saturdays = use(32, List.of(), true);
    assertThat(IndicatorsService.usedOn(saturdays, LocalDate.parse("2027-12-04"))).isTrue();
    assertThat(IndicatorsService.usedOn(saturdays, LocalDate.parse("2027-12-05"))).isFalse();
    UseSummary summer =
        use(
            127,
            List.of(
                new UseSummary.DateRange(
                    LocalDate.parse("2027-06-01"), LocalDate.parse("2027-08-31"))),
            false);
    assertThat(IndicatorsService.usedOn(summer, LocalDate.parse("2027-08-31"))).isTrue();
    assertThat(IndicatorsService.usedOn(summer, LocalDate.parse("2027-09-01"))).isFalse();
  }

  static UseSummary use(int weekdays, List<UseSummary.DateRange> periods, boolean archived) {
    return new UseSummary(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Uso",
        "WHOLE",
        null,
        60,
        false,
        List.of(),
        periods,
        weekdays,
        archived,
        new UseTerms(
            new UseTerms.Price("FREE", null, "BOOKING"),
            new UseTerms.Rules(null, null, null, null, null, null)));
  }
}
