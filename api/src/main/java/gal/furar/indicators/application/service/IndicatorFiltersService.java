// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.application.service;

import gal.furar.booking.BookingStatistics;
import gal.furar.booking.StatsQuery;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.UseSummary;
import gal.furar.indicators.IndicatorsPermission;
import gal.furar.indicators.application.port.in.IndicatorFilters;
import gal.furar.indicators.application.port.in.ReadIndicatorFiltersUseCase;
import gal.furar.indicators.domain.ReportPeriod;
import gal.furar.shared.security.RequiresPermission;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Os filtros do panel (plan do dominio 3, §2.3): as instalacións e os usos do concello. As
 * instalacións de baixa e os usos arquivados só saen se teñen algunha reserva (en calquera estado)
 * entre hai {@value #YEARS_BACK} anos e dentro de {@value IndicatorsService#YEARS_AHEAD}: unha
 * lectura acoutada ({@link StatsQuery} non pasa de cinco anos). Os máis vellos seguen valendo como
 * filtro se chegan na URL.
 */
@Service
class IndicatorFiltersService implements ReadIndicatorFiltersUseCase {

  /** Anos cara atrás nos que se buscan reservas das instalacións de baixa e usos arquivados. */
  static final int YEARS_BACK = 3;

  private final BookingStatistics statistics;
  private final FacilityQueries facilities;
  private final Clock clock;

  IndicatorFiltersService(BookingStatistics statistics, FacilityQueries facilities, Clock clock) {
    this.statistics = statistics;
    this.facilities = facilities;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true, timeout = IndicatorsService.TIMEOUT_SECONDS)
  @RequiresPermission(IndicatorsPermission.Code.VIEW)
  public IndicatorFilters filters() {
    LocalDate today = LocalDate.now(clock.withZone(ReportPeriod.ZONE));
    Set<UUID> booked =
        statistics.useIds(
            new StatsQuery(
                today.minusYears(YEARS_BACK),
                today.plusYears(IndicatorsService.YEARS_AHEAD),
                null,
                Set.of()));
    List<FacilitySummary> all = facilities.all();
    List<UseSummary> uses = facilities.allUses();
    Set<UUID> facilitiesWithBookings = new HashSet<>();
    uses.stream()
        .filter(u -> booked.contains(u.id()))
        .forEach(u -> facilitiesWithBookings.add(u.facilityId()));
    Map<UUID, FacilitySummary> shown = new LinkedHashMap<>();
    all.stream()
        .filter(f -> !f.withdrawn() || facilitiesWithBookings.contains(f.id()))
        .forEach(f -> shown.put(f.id(), f));
    List<IndicatorFilters.Facility> facilityList =
        Names.sorted(
            shown.values().stream()
                .map(f -> new IndicatorFilters.Facility(f.id(), f.name(), f.status()))
                .toList(),
            IndicatorFilters.Facility::name);
    List<IndicatorFilters.Use> useList =
        Names.sorted(
            uses.stream()
                .filter(u -> shown.containsKey(u.facilityId()))
                .filter(
                    u ->
                        booked.contains(u.id())
                            || (!u.archived() && !shown.get(u.facilityId()).withdrawn()))
                .map(u -> new IndicatorFilters.Use(u.id(), u.facilityId(), u.name(), u.archived()))
                .toList(),
            IndicatorFilters.Use::name);
    return new IndicatorFilters(facilityList, useList);
  }
}
