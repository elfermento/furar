// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence.schedule;

import gal.furar.facility.application.port.out.schedule.ScheduleExceptionRepository;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.ScheduleError;
import gal.furar.facility.domain.schedule.ScheduleException;
import gal.furar.facility.domain.schedule.ScheduleExceptionId;
import gal.furar.facility.domain.schedule.TimeRange;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Adaptador JPA das excepcións do horario, sempre dentro dun concello (A7b). */
@Component
class ScheduleExceptionPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements ScheduleExceptionRepository {

  /** Unha excepción por instalación e data (o servizo xa o comproba baixo o bloqueo). */
  static final String DATE_UNIQUE = "schedule_exception_date_uk";

  private final ScheduleExceptionJpaRepository exceptions;

  ScheduleExceptionPersistenceAdapter(ScheduleExceptionJpaRepository exceptions) {
    this.exceptions = exceptions;
  }

  @Override
  public List<ScheduleException> findByFacility(FacilityId facilityId) {
    tenant();
    return exceptions.findByFacilityIdOrderByDateAsc(facilityId.value()).stream()
        .map(ScheduleExceptionPersistenceAdapter::toDomain)
        .toList();
  }

  @Override
  public List<ScheduleException> findBetween(FacilityId facilityId, LocalDate from, LocalDate to) {
    tenant();
    return exceptions
        .findByFacilityIdAndDateBetweenOrderByDateAsc(facilityId.value(), from, to)
        .stream()
        .map(ScheduleExceptionPersistenceAdapter::toDomain)
        .toList();
  }

  @Override
  public List<ScheduleException> findBetween(
      Collection<FacilityId> facilityIds, LocalDate from, LocalDate to) {
    tenant();
    if (facilityIds.isEmpty()) {
      return List.of();
    }
    return exceptions
        .findByFacilityIdInAndDateBetweenOrderByDateAsc(
            facilityIds.stream().map(FacilityId::value).toList(), from, to)
        .stream()
        .map(ScheduleExceptionPersistenceAdapter::toDomain)
        .toList();
  }

  @Override
  public Optional<ScheduleException> findById(FacilityId facilityId, ScheduleExceptionId id) {
    tenant();
    return exceptions
        .findByIdAndFacilityId(id.value(), facilityId.value())
        .map(ScheduleExceptionPersistenceAdapter::toDomain);
  }

  @Override
  public ScheduleException insert(ScheduleException exception) {
    tenant();
    return translated(
        () -> {
          ScheduleExceptionEntity entity =
              new ScheduleExceptionEntity(
                  exception.id().value(), exception.facilityId().value(), exception.createdAt());
          apply(entity, exception);
          return toDomain(exceptions.saveAndFlush(entity));
        });
  }

  @Override
  public ScheduleException update(ScheduleException exception) {
    tenant();
    ScheduleExceptionEntity entity =
        exceptions
            .findByIdAndFacilityId(exception.id().value(), exception.facilityId().value())
            .orElseThrow(() -> new DomainException(ScheduleError.EXCEPTION_NOT_FOUND));
    if (!Objects.equals(entity.version(), exception.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return translated(
        () -> {
          apply(entity, exception);
          return toDomain(exceptions.saveAndFlush(entity));
        });
  }

  @Override
  public void delete(ScheduleException exception) {
    tenant();
    exceptions
        .findByIdAndFacilityId(exception.id().value(), exception.facilityId().value())
        .ifPresent(
            entity -> {
              exceptions.delete(entity);
              exceptions.flush();
            });
  }

  private static void apply(ScheduleExceptionEntity entity, ScheduleException exception) {
    TimeRange interval = exception.interval();
    entity.apply(
        exception.date(),
        interval == null ? null : interval.startMinute(),
        interval == null ? null : interval.endMinute(),
        exception.reason(),
        exception.updatedAt());
  }

  private static ScheduleException toDomain(ScheduleExceptionEntity e) {
    Integer start = e.startMinute();
    Integer end = e.endMinute();
    return ScheduleException.rehydrate(
        new ScheduleExceptionId(e.getId()),
        new FacilityId(e.facilityId()),
        e.date(),
        start == null || end == null ? null : new TimeRange(start, end),
        e.reason(),
        e.version() == null ? 0 : e.version(),
        e.createdAt(),
        e.updatedAt());
  }

  /** A data repetida da base tradúcese ao código do dominio; o resto sae como está. */
  private static ScheduleException translated(Supplier<ScheduleException> write) {
    try {
      return write.get();
    } catch (DataIntegrityViolationException e) {
      if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(DATE_UNIQUE)) {
        throw new DomainException(ScheduleError.EXCEPTION_DATE_TAKEN);
      }
      throw e;
    }
  }
}
