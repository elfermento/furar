// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Períodos, anterior, evolución e desagregación (plan do dominio 3, §1.2.5; D3-03). */
class ReportPeriodTest {

  private static LocalDate d(String iso) {
    return LocalDate.parse(iso);
  }

  @ParameterizedTest(name = "{0} {1} → {2}..{3}")
  @CsvSource({
    // Semanas ISO de luns a domingo, tamén as que cruzan o ano.
    "WEEK, 2026-01-01, 2025-12-29, 2026-01-04",
    "WEEK, 2026-12-31, 2026-12-28, 2027-01-03",
    "WEEK, 2027-01-01, 2026-12-28, 2027-01-03",
    "WEEK, 2021-01-01, 2020-12-28, 2021-01-03",
    "WEEK, 2026-10-26, 2026-10-26, 2026-11-01",
    "WEEK, 2026-11-01, 2026-10-26, 2026-11-01",
    "MONTH, 2026-10-28, 2026-10-01, 2026-10-31",
    "MONTH, 2024-02-10, 2024-02-01, 2024-02-29",
    "MONTH, 2026-02-10, 2026-02-01, 2026-02-28",
    "QUARTER, 2026-01-01, 2026-01-01, 2026-03-31",
    "QUARTER, 2026-05-15, 2026-04-01, 2026-06-30",
    "QUARTER, 2026-09-30, 2026-07-01, 2026-09-30",
    "QUARTER, 2026-12-31, 2026-10-01, 2026-12-31",
    "YEAR, 2026-10-28, 2026-01-01, 2026-12-31",
    "YEAR, 2024-12-31, 2024-01-01, 2024-12-31"
  })
  void naturalUnitThatContainsTheDate(
      PeriodKind kind, LocalDate anchor, LocalDate from, LocalDate to) {
    ReportPeriod period = ReportPeriod.of(kind, anchor);
    assertThat(period.kind()).isEqualTo(kind);
    assertThat(period.from()).isEqualTo(from);
    assertThat(period.to()).isEqualTo(to);
    assertThat(period.contains(anchor)).isTrue();
  }

  /**
   * A variación «ata hoxe» do período en curso (ADR-0026, revisión do 2026-10-29): do inicio a hoxe
   * fronte ao mesmo tramo do anterior, co tope no último día dun mes máis curto.
   */
  @ParameterizedTest(name = "{0} {1}, hoxe {2} → {3}..{2} fronte a {4}..{5}")
  @CsvSource({
    // O exemplo: do 1 ao 29 de outubro fronte ao 1 ao 29 de setembro.
    "MONTH, 2026-10-29, 2026-10-29, 2026-10-01, 2026-09-01, 2026-09-29",
    // Fin de mes: o 31 de outubro compárase con todo setembro (30 días).
    "MONTH, 2026-10-31, 2026-10-31, 2026-10-01, 2026-09-01, 2026-09-30",
    // Febreiro: o 31 e o 30 de marzo, ata o último de febreiro (28, ou 29 nun bisesto).
    "MONTH, 2026-03-31, 2026-03-31, 2026-03-01, 2026-02-01, 2026-02-28",
    "MONTH, 2026-03-30, 2026-03-30, 2026-03-01, 2026-02-01, 2026-02-28",
    "MONTH, 2028-03-31, 2028-03-31, 2028-03-01, 2028-02-01, 2028-02-29",
    "MONTH, 2026-02-28, 2026-02-28, 2026-02-01, 2026-01-01, 2026-01-28",
    "MONTH, 2028-02-29, 2028-02-29, 2028-02-01, 2028-01-01, 2028-01-29",
    // Cambio de ano: xaneiro fronte a decembro, o ano fronte ao anterior (29 de febreiro → 28).
    "MONTH, 2027-01-15, 2027-01-15, 2027-01-01, 2026-12-01, 2026-12-15",
    "YEAR, 2027-01-01, 2027-01-01, 2027-01-01, 2026-01-01, 2026-01-01",
    "YEAR, 2028-02-29, 2028-02-29, 2028-01-01, 2027-01-01, 2027-02-28",
    "QUARTER, 2027-03-31, 2027-03-31, 2027-01-01, 2026-10-01, 2026-12-31",
    "QUARTER, 2026-05-31, 2026-05-31, 2026-04-01, 2026-01-01, 2026-02-28",
    // Semana ISO 53 de 2026 (do 28 de decembro ao 3 de xaneiro), tamén despois do aninovo, e a
    // semana 1 de 2027 fronte á 53.
    "WEEK, 2026-12-31, 2026-12-31, 2026-12-28, 2026-12-21, 2026-12-24",
    "WEEK, 2027-01-02, 2027-01-02, 2026-12-28, 2026-12-21, 2026-12-26",
    "WEEK, 2027-01-06, 2027-01-06, 2027-01-04, 2026-12-28, 2026-12-30",
    // Hoxe é o primeiro día: un tramo dun día fronte ao primeiro día do anterior.
    "MONTH, 2026-11-01, 2026-11-01, 2026-11-01, 2026-10-01, 2026-10-01",
    "WEEK, 2026-10-26, 2026-10-26, 2026-10-26, 2026-10-19, 2026-10-19",
    "YEAR, 2026-01-01, 2026-01-01, 2026-01-01, 2025-01-01, 2025-01-01"
  })
  void inProgressComparesUpToTodayWithTheSameStretchBefore(
      PeriodKind kind,
      LocalDate anchor,
      LocalDate today,
      LocalDate from,
      LocalDate previousFrom,
      LocalDate previousTo) {
    Comparison comparison = ReportPeriod.of(kind, anchor).comparison(today);
    assertThat(comparison).isEqualTo(new Comparison(from, today, previousFrom, previousTo, true));
  }

  @Test
  void inProgressCustomRangeComparesTheSameDaysCountedFromTheStart() {
    // Do 20 de outubro ao 5 de novembro (17 días); o anterior, do 3 ao 19 de outubro.
    ReportPeriod period = ReportPeriod.custom(d("2026-10-20"), d("2026-11-05"));
    assertThat(period.comparison(d("2026-10-29")))
        .isEqualTo(
            new Comparison(
                d("2026-10-20"), d("2026-10-29"), d("2026-10-03"), d("2026-10-12"), true));
    assertThat(period.comparison(d("2026-10-20")))
        .isEqualTo(
            new Comparison(
                d("2026-10-20"), d("2026-10-20"), d("2026-10-03"), d("2026-10-03"), true));
    assertThat(period.comparison(d("2026-11-05")))
        .isEqualTo(
            new Comparison(
                d("2026-10-20"), d("2026-11-05"), d("2026-10-03"), d("2026-10-19"), true));
  }

  @Test
  void closedAndFuturePeriodsCompareWholePeriods() {
    ReportPeriod september = ReportPeriod.of(PeriodKind.MONTH, d("2026-09-10"));
    Comparison whole =
        new Comparison(d("2026-09-01"), d("2026-09-30"), d("2026-08-01"), d("2026-08-31"), false);
    assertThat(september.comparison(d("2026-10-29"))).isEqualTo(whole);
    // Aínda non empezou (reservas xa feitas): tamén enteiros.
    assertThat(september.comparison(d("2026-08-31"))).isEqualTo(whole);
    ReportPeriod week53 = ReportPeriod.of(PeriodKind.WEEK, d("2026-12-31"));
    assertThat(week53.comparison(d("2027-01-04")).toDate()).isFalse();
    assertThat(week53.comparison(d("2027-01-04")).previousTo()).isEqualTo(d("2026-12-27"));
  }

  @Test
  void theBucketInProgressContainsToday() {
    assertThat(ReportPeriod.inProgress(d("2026-10-01"), d("2026-10-31"), d("2026-10-01"))).isTrue();
    assertThat(ReportPeriod.inProgress(d("2026-10-01"), d("2026-10-31"), d("2026-10-31"))).isTrue();
    assertThat(ReportPeriod.inProgress(d("2026-10-01"), d("2026-10-31"), d("2026-11-01")))
        .isFalse();
    assertThat(ReportPeriod.inProgress(d("2026-10-01"), d("2026-10-31"), d("2026-09-30")))
        .isFalse();
  }

  @Test
  void aSpanCannotEndBeforeItStarts() {
    assertThatThrownBy(
            () ->
                new Comparison(
                    d("2026-10-02"), d("2026-10-01"), d("2026-09-01"), d("2026-09-01"), true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Comparison(
                    d("2026-10-01"), d("2026-10-01"), d("2026-09-02"), d("2026-09-01"), true))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void leapYearsHaveTheirExtraDay() {
    assertThat(ReportPeriod.of(PeriodKind.YEAR, d("2024-06-01")).days()).isEqualTo(366);
    assertThat(ReportPeriod.of(PeriodKind.YEAR, d("2026-06-01")).days()).isEqualTo(365);
    assertThat(ReportPeriod.of(PeriodKind.MONTH, d("2024-03-15")).previous())
        .isEqualTo(ReportPeriod.of(PeriodKind.MONTH, d("2024-02-29")));
    assertThat(ReportPeriod.of(PeriodKind.MONTH, d("2024-03-15")).previous().days()).isEqualTo(29);
    assertThat(ReportPeriod.of(PeriodKind.QUARTER, d("2024-02-29")).days()).isEqualTo(91);
    assertThat(ReportPeriod.of(PeriodKind.YEAR, d("2025-01-01")).previous().days()).isEqualTo(366);
  }

  @ParameterizedTest(name = "{0} {1} → anterior {2}..{3}")
  @CsvSource({
    "WEEK, 2026-01-01, 2025-12-22, 2025-12-28",
    "MONTH, 2026-01-20, 2025-12-01, 2025-12-31",
    "MONTH, 2026-03-31, 2026-02-01, 2026-02-28",
    "QUARTER, 2026-02-01, 2025-10-01, 2025-12-31",
    "QUARTER, 2026-08-01, 2026-04-01, 2026-06-30",
    "YEAR, 2026-08-01, 2025-01-01, 2025-12-31"
  })
  void previousIsTheUnitBefore(PeriodKind kind, LocalDate anchor, LocalDate from, LocalDate to) {
    ReportPeriod previous = ReportPeriod.of(kind, anchor).previous();
    assertThat(previous.kind()).isEqualTo(kind);
    assertThat(previous.from()).isEqualTo(from);
    assertThat(previous.to()).isEqualTo(to);
  }

  @Test
  void previousOfACustomRangeIsTheSameDaysJustBefore() {
    ReportPeriod period = ReportPeriod.custom(d("2026-03-01"), d("2026-03-10"));
    assertThat(period.days()).isEqualTo(10);
    ReportPeriod previous = period.previous();
    assertThat(previous.kind()).isEqualTo(PeriodKind.CUSTOM);
    assertThat(previous.from()).isEqualTo(d("2026-02-19"));
    assertThat(previous.to()).isEqualTo(d("2026-02-28"));
    // Bisesto: os cinco días antes do 1 de marzo de 2024 acaban o 29 de febreiro.
    ReportPeriod leap = ReportPeriod.custom(d("2024-03-01"), d("2024-03-05")).previous();
    assertThat(leap.from()).isEqualTo(d("2024-02-25"));
    assertThat(leap.to()).isEqualTo(d("2024-02-29"));
    // Un só día: o día anterior.
    ReportPeriod one = ReportPeriod.custom(d("2026-01-01"), d("2026-01-01")).previous();
    assertThat(one.from()).isEqualTo(d("2025-12-31"));
    assertThat(one.to()).isEqualTo(d("2025-12-31"));
  }

  @Test
  void customRangeGoesFromOneTo366Days() {
    assertThat(ReportPeriod.custom(d("2026-10-28"), d("2026-10-28")).days()).isEqualTo(1);
    assertThat(ReportPeriod.custom(d("2024-01-01"), d("2024-12-31")).days()).isEqualTo(366);
    assertThat(ReportPeriod.custom(d("2025-10-28"), d("2026-10-28")).days()).isEqualTo(366);
    assertPeriodInvalid(d("2024-01-01"), d("2025-01-01"));
    assertPeriodInvalid(d("2026-10-28"), d("2026-10-27"));
    assertPeriodInvalid(null, d("2026-10-27"));
    assertPeriodInvalid(d("2026-10-28"), null);
    assertPeriodInvalid(null, null);
  }

  private static void assertPeriodInvalid(LocalDate from, LocalDate to) {
    assertThatThrownBy(() -> ReportPeriod.custom(from, to))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(IndicatorsError.PERIOD_INVALID);
              assertThat(e.params()).isEqualTo(Map.of("max", 366));
            });
  }

  @Test
  void customIsNotANaturalUnit() {
    assertThatThrownBy(() -> ReportPeriod.of(PeriodKind.CUSTOM, d("2026-10-28")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(PeriodKind.CUSTOM.unit()).isNull();
  }

  @Test
  void evolutionHas12Weeks12Months8QuartersOr5YearsEndingInThePeriod() {
    List<ReportPeriod> weeks = ReportPeriod.of(PeriodKind.WEEK, d("2026-01-01")).evolution();
    assertThat(weeks).hasSize(12);
    assertThat(weeks.getFirst().from()).isEqualTo(d("2025-10-13"));
    assertThat(weeks.getLast()).isEqualTo(ReportPeriod.of(PeriodKind.WEEK, d("2026-01-01")));
    assertContiguous(weeks);

    List<ReportPeriod> months = ReportPeriod.of(PeriodKind.MONTH, d("2026-10-28")).evolution();
    assertThat(months).hasSize(12);
    assertThat(months.getFirst().from()).isEqualTo(d("2025-11-01"));
    assertThat(months.getLast().to()).isEqualTo(d("2026-10-31"));
    assertContiguous(months);

    List<ReportPeriod> quarters = ReportPeriod.of(PeriodKind.QUARTER, d("2026-12-31")).evolution();
    assertThat(quarters).hasSize(8);
    assertThat(quarters.getFirst().from()).isEqualTo(d("2025-01-01"));
    assertThat(quarters.getLast().from()).isEqualTo(d("2026-10-01"));
    assertThat(quarters).allMatch(q -> q.kind() == PeriodKind.QUARTER);
    assertContiguous(quarters);

    List<ReportPeriod> years = ReportPeriod.of(PeriodKind.YEAR, d("2026-10-28")).evolution();
    assertThat(years)
        .extracting(p -> p.from().getYear())
        .containsExactly(2022, 2023, 2024, 2025, 2026);
    assertContiguous(years);
  }

  @Test
  void evolutionOfACustomRangeIs12MonthsEndingInTheMonthOfItsEnd() {
    ReportPeriod period = ReportPeriod.custom(d("2026-02-10"), d("2026-03-05"));
    assertThat(period.evolutionUnit()).isEqualTo(PeriodUnit.MONTH);
    List<ReportPeriod> months = period.evolution();
    assertThat(months).hasSize(12).allMatch(p -> p.kind() == PeriodKind.MONTH);
    assertThat(months.getFirst().from()).isEqualTo(d("2025-04-01"));
    assertThat(months.getLast().from()).isEqualTo(d("2026-03-01"));
    assertThat(months.getLast().to()).isEqualTo(d("2026-03-31"));
    assertContiguous(months);
  }

  private static void assertContiguous(List<ReportPeriod> periods) {
    for (int i = 1; i < periods.size(); i++) {
      assertThat(periods.get(i).from()).isEqualTo(periods.get(i - 1).to().plusDays(1));
    }
  }

  @Test
  void evolutionUnitIsThePeriodUnit() {
    assertThat(ReportPeriod.of(PeriodKind.WEEK, d("2026-10-28")).evolutionUnit())
        .isEqualTo(PeriodUnit.WEEK);
    assertThat(ReportPeriod.of(PeriodKind.QUARTER, d("2026-10-28")).evolutionUnit())
        .isEqualTo(PeriodUnit.QUARTER);
    assertThat(ReportPeriod.of(PeriodKind.YEAR, d("2026-10-28")).evolutionUnit())
        .isEqualTo(PeriodUnit.YEAR);
  }

  @ParameterizedTest(name = "{0} días → {1}")
  @CsvSource({"1, DAY", "31, DAY", "32, WEEK", "120, WEEK", "121, MONTH", "366, MONTH"})
  void customBreakdownDependsOnTheLength(int days, PeriodUnit unit) {
    LocalDate from = d("2025-11-01");
    assertThat(ReportPeriod.custom(from, from.plusDays(days - 1L)).breakdownUnit()).isEqualTo(unit);
  }

  @Test
  void naturalBreakdown() {
    assertThat(ReportPeriod.of(PeriodKind.WEEK, d("2026-10-28")).breakdownUnit())
        .isEqualTo(PeriodUnit.DAY);
    assertThat(ReportPeriod.of(PeriodKind.MONTH, d("2026-10-28")).breakdownUnit())
        .isEqualTo(PeriodUnit.WEEK);
    assertThat(ReportPeriod.of(PeriodKind.QUARTER, d("2026-10-28")).breakdownUnit())
        .isEqualTo(PeriodUnit.WEEK);
    assertThat(ReportPeriod.of(PeriodKind.YEAR, d("2026-10-28")).breakdownUnit())
        .isEqualTo(PeriodUnit.MONTH);
  }

  @Test
  void bucketStartsAreAlignedLikeDateTrunc() {
    ReportPeriod october = ReportPeriod.of(PeriodKind.MONTH, d("2026-10-28"));
    assertThat(october.bucketStarts(PeriodUnit.WEEK))
        .containsExactly(
            d("2026-09-28"), d("2026-10-05"), d("2026-10-12"), d("2026-10-19"), d("2026-10-26"));
    assertThat(october.bucketStarts(PeriodUnit.DAY)).hasSize(31);
    assertThat(ReportPeriod.of(PeriodKind.YEAR, d("2026-10-28")).bucketStarts(PeriodUnit.MONTH))
        .hasSize(12)
        .endsWith(d("2026-12-01"));
    assertThat(ReportPeriod.of(PeriodKind.WEEK, d("2026-10-28")).bucketStarts(PeriodUnit.DAY))
        .containsExactly(
            d("2026-10-26"),
            d("2026-10-27"),
            d("2026-10-28"),
            d("2026-10-29"),
            d("2026-10-30"),
            d("2026-10-31"),
            d("2026-11-01"));
  }

  @Test
  void limitsAreMadridMidnightsIncludingDaylightSavingDays() {
    // 29 de marzo de 2026: día de 23 horas.
    ReportPeriod spring = ReportPeriod.custom(d("2026-03-29"), d("2026-03-29"));
    assertThat(spring.start()).isEqualTo(Instant.parse("2026-03-28T23:00:00Z"));
    assertThat(spring.end()).isEqualTo(Instant.parse("2026-03-29T22:00:00Z"));
    // 25 de outubro de 2026: día de 25 horas.
    ReportPeriod autumn = ReportPeriod.custom(d("2026-10-25"), d("2026-10-25"));
    assertThat(autumn.start()).isEqualTo(Instant.parse("2026-10-24T22:00:00Z"));
    assertThat(autumn.end()).isEqualTo(Instant.parse("2026-10-25T23:00:00Z"));
    ReportPeriod year = ReportPeriod.of(PeriodKind.YEAR, d("2026-06-01"));
    assertThat(year.start()).isEqualTo(Instant.parse("2025-12-31T23:00:00Z"));
    assertThat(year.end()).isEqualTo(Instant.parse("2026-12-31T23:00:00Z"));
  }

  @Test
  void containsBothEnds() {
    ReportPeriod week = ReportPeriod.of(PeriodKind.WEEK, d("2026-10-28"));
    assertThat(week.contains(d("2026-10-26"))).isTrue();
    assertThat(week.contains(d("2026-11-01"))).isTrue();
    assertThat(week.contains(d("2026-10-25"))).isFalse();
    assertThat(week.contains(d("2026-11-02"))).isFalse();
  }

  @Test
  void valueSemantics() {
    ReportPeriod a = ReportPeriod.of(PeriodKind.MONTH, d("2026-10-01"));
    ReportPeriod b = ReportPeriod.of(PeriodKind.MONTH, d("2026-10-31"));
    ReportPeriod custom = ReportPeriod.custom(d("2026-10-01"), d("2026-10-31"));
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(custom).isNotEqualTo("x");
    assertThat(a.toString()).isEqualTo("MONTH[2026-10-01..2026-10-31]");
  }

  @ParameterizedTest(name = "{0} {1}")
  @CsvSource({
    "DAY, 2026-10-28, 2026-10-28, 2026-10-28, 2026-10-30",
    "WEEK, 2026-10-28, 2026-10-26, 2026-11-01, 2026-11-11",
    "MONTH, 2026-10-28, 2026-10-01, 2026-10-31, 2026-12-28",
    "QUARTER, 2026-10-28, 2026-10-01, 2026-12-31, 2027-04-28",
    "YEAR, 2026-10-28, 2026-01-01, 2026-12-31, 2028-10-28"
  })
  void unitsStartEndAndMove(
      PeriodUnit unit, LocalDate date, LocalDate start, LocalDate end, LocalDate plusTwo) {
    assertThat(unit.start(date)).isEqualTo(start);
    assertThat(unit.end(date)).isEqualTo(end);
    assertThat(unit.plus(date, 2)).isEqualTo(plusTwo);
  }

  @Test
  void errorCodes() {
    assertThat(IndicatorsError.PERIOD_INVALID.code()).isEqualTo("indicators.period-invalid");
    assertThat(IndicatorsError.PERIOD_INVALID.status()).isEqualTo(400);
    assertThat(IndicatorsError.FILTER_INVALID.code()).isEqualTo("indicators.filter-invalid");
    assertThat(IndicatorsError.FILTER_INVALID.status()).isEqualTo(400);
  }
}
