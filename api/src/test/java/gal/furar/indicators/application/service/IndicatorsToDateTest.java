// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gal.furar.booking.BookingStatistics;
import gal.furar.booking.BookingStatistics.FacilityMoney;
import gal.furar.booking.BookingStatistics.PeopleBucket;
import gal.furar.booking.BookingStatistics.UseBucket;
import gal.furar.booking.BookingStatistics.UseDay;
import gal.furar.booking.StatsQuery;
import gal.furar.booking.StatsUnit;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.OpeningInterval;
import gal.furar.facility.UseSummary;
import gal.furar.facility.UseTerms;
import gal.furar.indicators.application.port.in.IndicatorsPanel;
import gal.furar.indicators.application.port.in.IndicatorsPanel.BucketOccupancy;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Kpis;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Occupancy;
import gal.furar.indicators.application.port.in.IndicatorsPanel.People;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Point;
import gal.furar.indicators.application.port.in.IndicatorsQuery;
import gal.furar.indicators.domain.Comparison;
import gal.furar.indicators.domain.PeriodKind;
import gal.furar.indicators.domain.ReportPeriod;
import gal.furar.shared.security.CurrentActor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A variación «ata hoxe» do período en curso e o cubo en curso de cada serie (ADR-0026, revisión do
 * 2026-10-29), sen base de datos: as lecturas de {@code booking} e {@code facility} son dobres.
 * Hoxe é o 29 de outubro de 2026: outubro compárase do 1 ao 29 co 1 ao 29 de setembro.
 */
class IndicatorsToDateTest {

  static final Clock OCTOBER_29 =
      Clock.fixed(Instant.parse("2026-10-29T10:00:00Z"), ZoneOffset.UTC);
  static final int OPEN_PER_DAY = 300;

  final UUID facility = UUID.randomUUID();
  final UUID use = UUID.randomUUID();
  final BookingStatistics statistics = mock(BookingStatistics.class);
  final FacilityQueries facilities = mock(FacilityQueries.class);
  final IndicatorsService service =
      new IndicatorsService(statistics, facilities, mock(CurrentActor.class), OCTOBER_29);

  static LocalDate d(String iso) {
    return LocalDate.parse(iso);
  }

  static StatsQuery q(String from, String to) {
    return new StatsQuery(d(from), d(to), null, Set.of());
  }

  @BeforeEach
  void facilityOpenEveryDayFromNineToTwo() {
    when(facilities.all())
        .thenReturn(
            List.of(
                new FacilitySummary(
                    facility,
                    "Pista do Alto",
                    "PUBLISHED",
                    true,
                    true,
                    new FacilitySummary.Address("Rúa do Alto", null, "15570", "Narón"))));
    when(facilities.usesById(anyCollection()))
        .thenReturn(
            List.of(
                new UseSummary(
                    use,
                    facility,
                    "Enteira",
                    "WHOLE",
                    null,
                    60,
                    false,
                    List.of(),
                    List.of(),
                    127,
                    false,
                    new UseTerms(
                        new UseTerms.Price("FREE", null, "BOOKING"),
                        new UseTerms.Rules(null, null, null, null, null, null)))));
    when(facilities.longOpeningIntervalsByFacility(isNull(), any(), any()))
        .thenAnswer(
            call -> {
              LocalDate from = call.getArgument(1);
              LocalDate to = call.getArgument(2);
              List<OpeningInterval> intervals = new ArrayList<>();
              for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                Instant start = day.atTime(9, 0).atZone(ReportPeriod.ZONE).toInstant();
                intervals.add(
                    new OpeningInterval(
                        day, 540, 840, start, start.plusSeconds(OPEN_PER_DAY * 60L), 540));
              }
              return Map.of(facility, intervals);
            });
    // Outubro: catro reservas ata hoxe e unha o 30 (xa feita); setembro: dúas ata o 29 e unha o 30.
    when(statistics.perUseAndDay(q("2026-10-01", "2026-10-31")))
        .thenReturn(
            List.of(
                new UseDay(use, facility, d("2026-10-02"), 4, 240),
                new UseDay(use, facility, d("2026-10-30"), 1, 60)));
    when(statistics.perBucket(q("2025-11-01", "2026-10-31"), StatsUnit.MONTH))
        .thenReturn(
            List.of(
                new UseBucket(d("2026-09-01"), use, 3, 180, 700),
                new UseBucket(d("2026-10-01"), use, 5, 300, 1200)));
    when(statistics.peoplePerBucket(q("2025-11-01", "2026-10-31"), StatsUnit.MONTH))
        .thenReturn(
            List.of(new PeopleBucket(d("2026-09-01"), 3), new PeopleBucket(d("2026-10-01"), 4)));
    when(statistics.money(q("2026-10-01", "2026-10-31")))
        .thenReturn(List.of(new FacilityMoney(facility, 1200, 0, 0, 0)));
    when(statistics.perBucket(q("2026-10-01", "2026-10-29"), StatsUnit.YEAR))
        .thenReturn(List.of(new UseBucket(d("2026-01-01"), use, 4, 240, 1000)));
    when(statistics.perBucket(q("2026-09-01", "2026-09-29"), StatsUnit.YEAR))
        .thenReturn(List.of(new UseBucket(d("2026-01-01"), use, 2, 120, 500)));
    when(statistics.people(q("2026-10-01", "2026-10-29"))).thenReturn(3L);
    when(statistics.people(q("2026-09-01", "2026-09-29"))).thenReturn(2L);
  }

  IndicatorsPanel read(@Nullable LocalDate date) {
    return service.read(new IndicatorsQuery(PeriodKind.MONTH, date, null, null, null, List.of()));
  }

  @Test
  void theMonthInProgressComparesUpToTodayWithTheSameDaysOfThePreviousMonth() {
    IndicatorsPanel panel = read(null);

    assertThat(panel.comparison())
        .isEqualTo(
            new Comparison(
                d("2026-10-01"), d("2026-10-29"), d("2026-09-01"), d("2026-09-29"), true));
    Kpis kpis = panel.kpis();
    // O valor segue sendo o mes enteiro (P8); a variación, ata hoxe.
    assertThat(kpis.bookings().value()).isEqualTo(5);
    assertThat(kpis.bookings().previous()).isEqualTo(3);
    assertThat(kpis.bookings().valueToDate()).isEqualTo(4);
    assertThat(kpis.bookings().previousToDate()).isEqualTo(2);
    assertThat(kpis.reservedMinutes().valueToDate()).isEqualTo(240.0);
    assertThat(kpis.reservedMinutes().previousToDate()).isEqualTo(120.0);
    assertThat(kpis.paidCents().value()).isEqualTo(1200);
    assertThat(kpis.paidCents().valueToDate()).isEqualTo(1000);
    assertThat(kpis.paidCents().previousToDate()).isEqualTo(500);
    assertThat(kpis.people().value()).isEqualTo(4);
    assertThat(kpis.people().valueToDate()).isEqualTo(3);
    assertThat(kpis.people().previousToDate()).isEqualTo(2);
    // As horas abertas tamén son as dos 29 días de cada lado.
    assertThat(kpis.occupancy().valueToDate()).isEqualTo(new Occupancy(240, 29L * OPEN_PER_DAY));
    assertThat(kpis.occupancy().previousToDate()).isEqualTo(new Occupancy(120, 29L * OPEN_PER_DAY));
    assertThat(kpis.occupancy().value().openMinutes()).isEqualTo(31L * OPEN_PER_DAY);

    verify(statistics, times(2)).perBucket(any(), eq(StatsUnit.YEAR));
    verify(statistics, times(2)).people(any());
  }

  @Test
  void onlyTheBucketThatContainsTodayIsInProgress() {
    IndicatorsPanel panel = read(null);

    List<Point<Long>> evolution = panel.kpis().bookings().evolution();
    assertThat(evolution.getLast().partial()).isTrue();
    assertThat(evolution.subList(0, evolution.size() - 1)).noneMatch(Point::partial);
    assertThat(panel.kpis().occupancy().evolution().getLast().partial()).isTrue();
    assertThat(panel.people().getLast().partial()).isTrue();
    assertThat(panel.people().subList(0, 5)).noneMatch(People::partial);
    // Por semanas: só a do 26 ao 31 de outubro.
    assertThat(panel.dailyOccupancy())
        .filteredOn(BucketOccupancy::partial)
        .singleElement()
        .satisfies(b -> assertThat(b.from()).isEqualTo(d("2026-10-26")));
    assertThat(panel.cancellations())
        .filteredOn(IndicatorsPanel.Cancellations::partial)
        .singleElement()
        .satisfies(c -> assertThat(c.to()).isEqualTo(d("2026-10-31")));
  }

  @Test
  void aClosedPeriodComparesWholePeriodsWithoutMoreReads() {
    when(statistics.perUseAndDay(q("2026-09-01", "2026-09-30")))
        .thenReturn(List.of(new UseDay(use, facility, d("2026-09-02"), 3, 180)));
    when(statistics.perBucket(q("2025-10-01", "2026-09-30"), StatsUnit.MONTH))
        .thenReturn(
            List.of(
                new UseBucket(d("2026-08-01"), use, 1, 60, 0),
                new UseBucket(d("2026-09-01"), use, 3, 180, 700)));

    IndicatorsPanel panel = read(d("2026-09-10"));

    assertThat(panel.comparison())
        .isEqualTo(
            new Comparison(
                d("2026-09-01"), d("2026-09-30"), d("2026-08-01"), d("2026-08-31"), false));
    assertThat(panel.kpis().bookings().valueToDate()).isEqualTo(panel.kpis().bookings().value());
    assertThat(panel.kpis().bookings().previousToDate()).isEqualTo(1);
    assertThat(panel.kpis().occupancy().previousToDate())
        .isEqualTo(panel.kpis().occupancy().previous());
    assertThat(panel.kpis().bookings().evolution()).noneMatch(Point::partial);
    assertThat(panel.dailyOccupancy()).noneMatch(BucketOccupancy::partial);
    verify(statistics, never()).perBucket(any(), eq(StatsUnit.YEAR));
    verify(statistics, never()).people(any());
  }
}
