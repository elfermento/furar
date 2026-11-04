// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import gal.furar.facility.application.port.out.schedule.ScheduleExceptionRepository;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.ScheduleError;
import gal.furar.facility.domain.schedule.ScheduleException;
import gal.furar.facility.domain.schedule.ScheduleExceptionId;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.TenantContext;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Excepcións en memoria, separadas por concello coma as reais e cunha por instalación e data coma
 * na base. Cada escritura queda en {@code calls} (compartida coa instalación).
 */
public class InMemoryScheduleExceptionRepository implements ScheduleExceptionRepository {

  private final Map<UUID, Map<ScheduleExceptionId, ScheduleException>> byMunicipality =
      new LinkedHashMap<>();
  private final List<String> calls;

  public InMemoryScheduleExceptionRepository(List<String> calls) {
    this.calls = calls;
  }

  private Map<ScheduleExceptionId, ScheduleException> current() {
    return byMunicipality.computeIfAbsent(TenantContext.require().id(), k -> new LinkedHashMap<>());
  }

  @Override
  public List<ScheduleException> findByFacility(FacilityId facilityId) {
    return current().values().stream()
        .filter(e -> e.facilityId().equals(facilityId))
        .sorted(Comparator.comparing(ScheduleException::date))
        .toList();
  }

  @Override
  public List<ScheduleException> findBetween(FacilityId facilityId, LocalDate from, LocalDate to) {
    return findByFacility(facilityId).stream()
        .filter(e -> !e.date().isBefore(from) && !e.date().isAfter(to))
        .toList();
  }

  @Override
  public List<ScheduleException> findBetween(
      Collection<FacilityId> facilityIds, LocalDate from, LocalDate to) {
    return current().values().stream()
        .filter(e -> facilityIds.contains(e.facilityId()))
        .filter(e -> !e.date().isBefore(from) && !e.date().isAfter(to))
        .sorted(Comparator.comparing(ScheduleException::date))
        .toList();
  }

  @Override
  public Optional<ScheduleException> findById(FacilityId facilityId, ScheduleExceptionId id) {
    return Optional.ofNullable(current().get(id)).filter(e -> e.facilityId().equals(facilityId));
  }

  @Override
  public ScheduleException insert(ScheduleException exception) {
    calls.add("insert-exception");
    refuseTaken(exception);
    ScheduleException stored = copy(exception, 0L);
    current().put(exception.id(), stored);
    return stored;
  }

  @Override
  public ScheduleException update(ScheduleException exception) {
    calls.add("update-exception");
    ScheduleException existing = current().get(exception.id());
    if (existing == null) {
      throw new DomainException(ScheduleError.EXCEPTION_NOT_FOUND);
    }
    if (!Objects.equals(existing.version(), exception.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    refuseTaken(exception);
    ScheduleException stored = copy(exception, Objects.requireNonNull(exception.version()) + 1);
    current().put(exception.id(), stored);
    return stored;
  }

  @Override
  public void delete(ScheduleException exception) {
    calls.add("delete-exception");
    current().remove(exception.id());
  }

  private void refuseTaken(ScheduleException exception) {
    boolean taken =
        findBetween(exception.facilityId(), exception.date(), exception.date()).stream()
            .anyMatch(e -> !e.id().equals(exception.id()));
    if (taken) {
      throw new DomainException(ScheduleError.EXCEPTION_DATE_TAKEN);
    }
  }

  private static ScheduleException copy(ScheduleException e, long version) {
    return ScheduleException.rehydrate(
        e.id(),
        e.facilityId(),
        e.date(),
        e.interval(),
        e.reason(),
        version,
        e.createdAt(),
        e.updatedAt());
  }
}
