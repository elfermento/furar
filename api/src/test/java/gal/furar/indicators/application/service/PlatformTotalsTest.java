// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.indicators.application.port.in.IndicatorsPanel.Kpi;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Kpis;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Occupancy;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Point;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Rating;
import gal.furar.indicators.application.port.in.PlatformIndicators.Totals;
import gal.furar.indicators.domain.PeriodKind;
import gal.furar.indicators.domain.ReportPeriod;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Os totais da plataforma: suma valor a valor e punto a punto; a ocupación, por minutos. */
class PlatformTotalsTest {

  static final LocalDate OCT = LocalDate.parse("2027-10-01");
  static final LocalDate NOV = LocalDate.parse("2027-11-01");

  @Test
  void sumsEveryValueAndEveryEvolutionPoint() {
    Kpis a = kpis(10, new Occupancy(300, 1000), 600.0, 5, 1500);
    Kpis b = kpis(2, new Occupancy(100, 3000), 120.5, 9, 200);

    Totals totals = PlatformTotals.sum(List.of(a, b), null);

    assertThat(totals.bookings().value()).isEqualTo(12);
    assertThat(totals.bookings().previous()).isEqualTo(24);
    assertThat(totals.bookings().valueToDate()).isEqualTo(12);
    assertThat(totals.bookings().evolution()).extracting(Point::value).containsExactly(24L, 12L);
    assertThat(totals.bookings().evolution().getLast().partial()).isTrue();
    assertThat(totals.reservedMinutes().value()).isEqualTo(720.5);
    assertThat(totals.paidCents().value()).isEqualTo(1700);
    // Media ponderada polas horas abertas: 400 / 4000 = 10 %, non (30 % + 3,3 %) / 2.
    assertThat(totals.occupancy().value()).isEqualTo(new Occupancy(400, 4000));
  }

  @Test
  void oneMunicipalityIsItsOwnTotal() {
    Kpis a = kpis(7, new Occupancy(30, 100), 60.0, 1, 0);

    Totals totals = PlatformTotals.sum(List.of(a), null);

    assertThat(totals.bookings()).isEqualTo(a.bookings());
    assertThat(totals.occupancy()).isEqualTo(a.occupancy());
  }

  @Test
  void withoutPanelsTheEmptyTotalsWithTheirBuckets() {
    ReportPeriod period = ReportPeriod.of(PeriodKind.MONTH, NOV);
    Totals empty = PlatformIndicatorsService.empty(period, NOV.plusDays(3));

    assertThat(PlatformTotals.sum(List.of(), empty)).isSameAs(empty);
    assertThat(empty.bookings().evolution()).hasSize(12);
    assertThat(empty.bookings().evolution().getLast().partial()).isTrue();
    assertThat(empty.occupancy().value()).isEqualTo(Occupancy.NONE);
  }

  @Test
  void differentBucketsAreAProgrammingError() {
    Kpis a = kpis(1, Occupancy.NONE, 0.0, 0, 0);
    Kpi<Long> shifted =
        new Kpi<>(
            1L,
            1L,
            1L,
            1L,
            List.of(new Point<>(NOV, NOV, 1L, false), new Point<>(NOV, NOV, 1L, false)));
    Kpis b =
        new Kpis(
            shifted, a.occupancy(), a.reservedMinutes(), a.people(), a.paidCents(), a.rating());

    assertThatThrownBy(() -> PlatformTotals.sum(List.of(a, b), null))
        .isInstanceOf(IllegalStateException.class);
  }

  /** Un panel con cada cifra igual no período e no tramo ata hoxe, e o dobre no anterior. */
  private static Kpis kpis(
      long bookings, Occupancy occupancy, double minutes, long people, long paid) {
    return new Kpis(
        kpi(bookings, bookings * 2),
        kpi(occupancy, new Occupancy(occupancy.reservedMinutes() * 2, occupancy.openMinutes() * 2)),
        kpi(minutes, minutes * 2),
        kpi(people, people * 2),
        kpi(paid, paid * 2),
        kpi(Rating.NONE, Rating.NONE));
  }

  private static <T> Kpi<T> kpi(T value, T previous) {
    return new Kpi<>(
        value,
        previous,
        value,
        previous,
        List.of(
            new Point<>(OCT, OCT.plusMonths(1).minusDays(1), previous, false),
            new Point<>(NOV, NOV.plusMonths(1).minusDays(1), value, true)));
  }
}
