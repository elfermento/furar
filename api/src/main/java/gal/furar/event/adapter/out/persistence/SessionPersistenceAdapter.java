// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.out.persistence;

import gal.furar.event.application.port.out.SessionRepository;
import gal.furar.event.domain.EventId;
import gal.furar.event.domain.EventSession;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** As sesións e a asistencia no concello actual (A7b): sen concello falla antes de consultar. */
@Component
class SessionPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements SessionRepository {

  private final SessionSql sql;
  private final Clock clock;

  SessionPersistenceAdapter(SessionSql sql, Clock clock) {
    this.sql = sql;
    this.clock = clock;
  }

  @Override
  public void insertAll(List<EventSession> sessions) {
    sql.insertAll(tenant().id(), sessions, clock.instant());
  }

  @Override
  public List<EventSession> sessionsOf(EventId event) {
    return sql.listForEvent(tenant().id(), event.value());
  }

  @Override
  public Optional<EventSession> find(EventId event, UUID sessionId) {
    return sql.find(tenant().id(), event.value(), sessionId);
  }

  @Override
  public List<EventSession> upcoming(Collection<EventId> events, Instant now) {
    return sql.upcoming(tenant().id(), events.stream().map(EventId::value).toList(), now);
  }

  @Override
  public Map<UUID, UUID> byBookings(Collection<UUID> bookingIds) {
    Map<UUID, UUID> links = new HashMap<>();
    sql.byBookings(tenant().id(), bookingIds).forEach(l -> links.put(l.bookingId(), l.sessionId()));
    return links;
  }

  @Override
  public Optional<EventSession> findByBooking(UUID bookingId) {
    return sql.findByBooking(tenant().id(), bookingId);
  }

  @Override
  public void cancel(Collection<UUID> sessionIds, Instant now) {
    sql.cancel(tenant().id(), sessionIds, now);
  }

  @Override
  public Map<UUID, Boolean> marks(UUID sessionId) {
    Map<UUID, Boolean> marks = new HashMap<>();
    sql.marks(tenant().id(), sessionId).forEach(m -> marks.put(m.registrationId(), m.present()));
    return marks;
  }

  @Override
  public void mark(
      UUID sessionId, Map<UUID, @Nullable Boolean> marks, Instant now, @Nullable String byName) {
    UUID m = tenant().id();
    marks.forEach(
        (registration, present) -> {
          if (present == null) {
            sql.deleteMark(m, sessionId, registration);
          } else {
            if (sql.updateMark(m, sessionId, registration, present, now) == 0) {
              sql.insertMark(m, sessionId, registration, present, now);
            }
          }
        });
    sql.taken(m, sessionId, now, byName);
  }

  @Override
  public List<Today> on(LocalDate date) {
    return sql.on(tenant().id(), date);
  }

  @Override
  public Optional<Saved> saved(UUID sessionId) {
    return sql.saved(tenant().id(), sessionId);
  }

  @Override
  public Map<UUID, Tally> tallies(EventId event) {
    Map<UUID, Tally> tallies = new HashMap<>();
    sql.tallies(tenant().id(), event.value())
        .forEach(t -> tallies.put(t.key(), new Tally(t.present(), t.absent())));
    return tallies;
  }

  @Override
  public Map<UUID, Summary> summary(EventId event) {
    UUID m = tenant().id();
    int taken = sql.takenCount(m, event.value());
    Map<UUID, Summary> summary = new HashMap<>();
    sql.byRegistration(m, event.value())
        .forEach(t -> summary.put(t.key(), new Summary(taken, t.present(), t.absent())));
    return summary;
  }
}
