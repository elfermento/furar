// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.application.port.out.SeriesStore;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.season.Series;
import gal.furar.booking.domain.season.SeriesPlanner;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Tempadas no concello actual: falla sen concello ou sen transacción (ADR-0006). */
@Component
class SeriesPersistenceAdapter extends TenantScopedPersistenceAdapter implements SeriesStore {

  private static final String CREATED = "CREATED";
  private static final String CANCELLED = "CANCELLED_BY_MUNICIPALITY";

  private final SeriesSql sql;

  SeriesPersistenceAdapter(SeriesSql sql) {
    this.sql = sql;
  }

  @Override
  public Optional<Series> find(UUID id) {
    return sql.find(tenant().id(), id);
  }

  @Override
  public Optional<Series> findForUpdate(UUID id) {
    return sql.findForUpdate(tenant().id(), id);
  }

  @Override
  public Optional<Series> findByKey(String idempotencyKey) {
    return sql.findByKey(tenant().id(), idempotencyKey);
  }

  @Override
  public List<Series> list(boolean ended, LocalDate today) {
    return sql.list(tenant().id(), ended, today);
  }

  @Override
  public List<Series> ofOrganization(UUID organizationId) {
    return sql.ofOrganization(tenant().id(), organizationId);
  }

  @Override
  public Map<UUID, Counts> counts(Collection<UUID> seriesIds) {
    return sql.counts(tenant().id(), seriesIds).stream()
        .collect(
            Collectors.toMap(
                SeriesSql.CountRow::seriesId, r -> new Counts(r.confirmed(), r.cancelled())));
  }

  @Override
  public List<LocalDate> sessionDates(UUID seriesId) {
    return sql.sessionDates(tenant().id(), seriesId);
  }

  @Override
  public List<SeriesPlanner.Occupant> occupants(UUID facilityId, Instant from, Instant to) {
    return sql.occupants(tenant().id(), facilityId, from, to);
  }

  @Override
  public Set<String> takenCodes(Collection<String> codes) {
    return Set.copyOf(sql.takenCodes(tenant().id(), codes));
  }

  @Override
  public void insert(Series series) {
    sql.insert(tenant().id(), series);
  }

  @Override
  public void update(Series before, Series after) {
    if (!sql.update(tenant().id(), after, Objects.requireNonNull(before.version(), "version"))) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
  }

  @Override
  public List<Booking> insertSessions(List<Booking> sessions, String actorSubject) {
    if (sessions.isEmpty()) {
      return List.of();
    }
    UUID m = tenant().id();
    try {
      Set<UUID> written = Set.copyOf(sql.insertBookings(m, sessions));
      List<Booking> stored = new ArrayList<>();
      List<Booking> refused = new ArrayList<>();
      for (Booking x : sessions) {
        (written.contains(x.id().value()) ? stored : refused).add(x);
      }
      if (!stored.isEmpty()) {
        sql.insertOccupancies(m, stored);
        sql.insertEvents(
            m,
            stored.stream().map(x -> x.id().value()).toList(),
            CREATED,
            stored.getFirst().createdAt(),
            actorSubject);
      }
      return refused;
    } catch (DataIntegrityViolationException e) {
      String message = String.valueOf(e.getMostSpecificCause().getMessage());
      if (message.contains("booking_occupancy_part")) {
        throw new DomainException(BookingError.PART_TAKEN);
      }
      if (message.contains("booking_occupancy_")) {
        throw new DomainException(BookingError.SLOT_TAKEN);
      }
      throw e;
    }
  }

  @Override
  public List<Booking> activitySessions(
      UUID activityId, Collection<UUID> bookingIds, Instant after) {
    return sql.activitySessions(tenant().id(), activityId, bookingIds, after);
  }

  @Override
  public void renameActivity(UUID activityId, String name) {
    sql.renameActivity(tenant().id(), activityId, name);
  }

  @Override
  public List<Booking> confirmedSessions(UUID seriesId, Instant after) {
    return sql.confirmedSessions(tenant().id(), seriesId, after);
  }

  @Override
  public void cancelSessions(List<Booking> before, List<Booking> after, String actorSubject) {
    if (after.isEmpty()) {
      return;
    }
    UUID m = tenant().id();
    List<UUID> ids = after.stream().map(x -> x.id().value()).toList();
    Instant at = after.getFirst().updatedAt();
    // Baixo o bloqueo da instalación ninguén máis as toca: se non son todas, algo cambiou.
    if (sql.cancelBookings(m, ids, at, actorSubject) != ids.size()) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    sql.deactivateOccupancies(m, ids);
    sql.insertEvents(m, ids, CANCELLED, at, actorSubject);
  }
}
