// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web;

import gal.furar.indicators.adapter.in.web.dto.IndicatorBucketOccupancyResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorCancellationsResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorComparisonResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorCountKpiResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorCountPointResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorFacilityOccupancyResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorFacilityStatusValue;
import gal.furar.indicators.adapter.in.web.dto.IndicatorFilterFacilityResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorFilterUseResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorFiltersResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorKpisResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorMinutesKpiResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorMinutesPointResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorOccupancyKpiResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorOccupancyPointResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorOccupancyResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorPeakHourResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorPeopleResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorPeriodKindValue;
import gal.furar.indicators.adapter.in.web.dto.IndicatorPeriodResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorRangeResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorRevenueResponse;
import gal.furar.indicators.adapter.in.web.dto.IndicatorUnitValue;
import gal.furar.indicators.adapter.in.web.dto.IndicatorsResponse;
import gal.furar.indicators.application.port.in.IndicatorFilters;
import gal.furar.indicators.application.port.in.IndicatorsPanel;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Kpi;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Occupancy;
import gal.furar.indicators.domain.Comparison;
import gal.furar.indicators.domain.PeriodUnit;
import gal.furar.indicators.domain.ReportPeriod;

/** Do panel e dos filtros aos DTO. Os minutos ponderados van con dous decimais. */
final class IndicatorsWebMapper {

  private static final double CENTS = 100.0;

  private IndicatorsWebMapper() {}

  static IndicatorsResponse panel(IndicatorsPanel panel) {
    ReportPeriod period = panel.period();
    IndicatorsPanel.Kpis kpis = panel.kpis();
    Comparison comparison = panel.comparison();
    return new IndicatorsResponse(
        new IndicatorPeriodResponse(
            IndicatorPeriodKindValue.valueOf(period.kind().name()),
            period.from(),
            period.to(),
            unit(period.evolutionUnit()),
            unit(period.breakdownUnit())),
        new IndicatorRangeResponse(panel.previous().from(), panel.previous().to()),
        new IndicatorComparisonResponse(
            comparison.toDate(),
            comparison.from(),
            comparison.to(),
            comparison.previousFrom(),
            comparison.previousTo()),
        new IndicatorKpisResponse(
            count(kpis.bookings()),
            new IndicatorOccupancyKpiResponse(
                occupancy(kpis.occupancy().value()),
                occupancy(kpis.occupancy().previous()),
                occupancy(kpis.occupancy().valueToDate()),
                occupancy(kpis.occupancy().previousToDate()),
                kpis.occupancy().evolution().stream()
                    .map(
                        p ->
                            new IndicatorOccupancyPointResponse(
                                p.from(), p.to(), occupancy(p.value()), p.partial()))
                    .toList()),
            new IndicatorMinutesKpiResponse(
                round(kpis.reservedMinutes().value()),
                round(kpis.reservedMinutes().previous()),
                round(kpis.reservedMinutes().valueToDate()),
                round(kpis.reservedMinutes().previousToDate()),
                kpis.reservedMinutes().evolution().stream()
                    .map(
                        p ->
                            new IndicatorMinutesPointResponse(
                                p.from(), p.to(), round(p.value()), p.partial()))
                    .toList()),
            count(kpis.people()),
            count(kpis.paidCents())),
        panel.occupancyByFacility().stream()
            .map(
                f ->
                    new IndicatorFacilityOccupancyResponse(
                        f.facilityId(),
                        f.name(),
                        round(f.occupancy().reservedMinutes()),
                        f.occupancy().openMinutes()))
            .toList(),
        panel.dailyOccupancy().stream()
            .map(
                b ->
                    new IndicatorBucketOccupancyResponse(
                        b.from(),
                        b.to(),
                        round(b.occupancy().reservedMinutes()),
                        b.occupancy().openMinutes(),
                        b.partial()))
            .toList(),
        panel.peakHours().stream()
            .map(
                h ->
                    new IndicatorPeakHourResponse(
                        h.isoWeekday(),
                        h.hour(),
                        round(h.occupancy().reservedMinutes()),
                        h.occupancy().openMinutes()))
            .toList(),
        panel.cancellations().stream()
            .map(
                c ->
                    new IndicatorCancellationsResponse(
                        c.from(), c.to(), c.inTime(), c.late(), c.byMunicipality(), c.partial()))
            .toList(),
        panel.revenue().stream()
            .map(
                r ->
                    new IndicatorRevenueResponse(
                        r.facilityId(),
                        r.name(),
                        r.paidCents(),
                        r.pendingCents(),
                        r.toRefundCents(),
                        r.refundedCents()))
            .toList(),
        panel.people().stream()
            .map(p -> new IndicatorPeopleResponse(p.from(), p.to(), p.people(), p.partial()))
            .toList());
  }

  static IndicatorFiltersResponse filters(IndicatorFilters filters) {
    return new IndicatorFiltersResponse(
        filters.facilities().stream()
            .map(
                f ->
                    new IndicatorFilterFacilityResponse(
                        f.id(), f.name(), IndicatorFacilityStatusValue.valueOf(f.status())))
            .toList(),
        filters.uses().stream()
            .map(
                u -> new IndicatorFilterUseResponse(u.id(), u.facilityId(), u.name(), u.archived()))
            .toList());
  }

  private static IndicatorCountKpiResponse count(Kpi<Long> kpi) {
    return new IndicatorCountKpiResponse(
        kpi.value(),
        kpi.previous(),
        kpi.valueToDate(),
        kpi.previousToDate(),
        kpi.evolution().stream()
            .map(p -> new IndicatorCountPointResponse(p.from(), p.to(), p.value(), p.partial()))
            .toList());
  }

  private static IndicatorOccupancyResponse occupancy(Occupancy occupancy) {
    return new IndicatorOccupancyResponse(
        round(occupancy.reservedMinutes()), occupancy.openMinutes());
  }

  private static IndicatorUnitValue unit(PeriodUnit unit) {
    return IndicatorUnitValue.valueOf(unit.name());
  }

  /** Dous decimais: os pesos 1/n deixan restos binarios que non din nada. */
  static double round(double minutes) {
    return Math.round(minutes * CENTS) / CENTS;
  }
}
