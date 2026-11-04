// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import gal.furar.facility.application.port.out.schedule.PublicHolidayRepository;
import gal.furar.facility.application.port.out.schedule.SeasonRepository;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.PublicHoliday;
import gal.furar.facility.domain.schedule.ScheduleError;
import gal.furar.facility.domain.schedule.Season;
import gal.furar.facility.domain.schedule.SeasonId;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.TenantContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Tempadas e festivos en memoria, separadas por concello coma as reais. Cada escritura queda en
 * {@code calls} (compartida coa instalación) para comprobar que o bloqueo vai primeiro.
 */
public class InMemorySeasonRepository implements SeasonRepository, PublicHolidayRepository {

  private final Map<UUID, Map<SeasonId, Season>> byMunicipality = new LinkedHashMap<>();
  private final List<String> calls;
  public final List<PublicHoliday> holidays = new ArrayList<>();

  public InMemorySeasonRepository(List<String> calls) {
    this.calls = calls;
  }

  private Map<SeasonId, Season> current() {
    return byMunicipality.computeIfAbsent(TenantContext.require().id(), k -> new LinkedHashMap<>());
  }

  @Override
  public List<Season> findByFacility(FacilityId facilityId) {
    return current().values().stream()
        .filter(s -> s.facilityId().equals(facilityId))
        .sorted(Comparator.comparing(Season::startDate))
        .toList();
  }

  @Override
  public List<Season> findByFacilities(Collection<FacilityId> facilityIds) {
    return current().values().stream()
        .filter(s -> facilityIds.contains(s.facilityId()))
        .sorted(Comparator.comparing(Season::startDate))
        .toList();
  }

  @Override
  public Optional<Season> findById(FacilityId facilityId, SeasonId seasonId) {
    return Optional.ofNullable(current().get(seasonId))
        .filter(s -> s.facilityId().equals(facilityId));
  }

  @Override
  public Season insert(Season season) {
    calls.add("insert-season");
    Season stored = copy(season, 0L);
    current().put(season.id(), stored);
    return stored;
  }

  @Override
  public Season update(Season season) {
    calls.add("update-season");
    Season existing = current().get(season.id());
    if (existing == null) {
      throw new DomainException(ScheduleError.SEASON_NOT_FOUND);
    }
    if (!Objects.equals(existing.version(), season.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    Season stored = copy(season, Objects.requireNonNull(season.version()) + 1);
    current().put(season.id(), stored);
    return stored;
  }

  @Override
  public void delete(Season season) {
    calls.add("delete-season");
    current().remove(season.id());
  }

  @Override
  public List<PublicHoliday> findBetween(LocalDate from, LocalDate to) {
    return holidays.stream()
        .filter(h -> !h.date().isBefore(from) && !h.date().isAfter(to))
        .sorted(Comparator.comparing(PublicHoliday::date))
        .toList();
  }

  private static Season copy(Season s, long version) {
    return Season.rehydrate(
        s.id(),
        s.facilityId(),
        s.name(),
        s.startDate(),
        s.endDate(),
        s.week(),
        version,
        s.createdAt(),
        s.updatedAt());
  }
}
