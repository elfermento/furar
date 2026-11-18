// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.out.persistence;

import gal.furar.event.application.port.out.RegistrationRepository;
import gal.furar.event.domain.EventError;
import gal.furar.event.domain.EventId;
import gal.furar.event.domain.Registration;
import gal.furar.event.domain.RegistrationId;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/** As inscricións no concello actual (A7b): sen concello falla antes de consultar. */
@Component
class RegistrationPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements RegistrationRepository {

  private final RegistrationSql sql;

  RegistrationPersistenceAdapter(RegistrationSql sql) {
    this.sql = sql;
  }

  @Override
  public boolean lockEvent(EventId id) {
    return sql.lockEvent(tenant().id(), id.value());
  }

  @Override
  public Map<UUID, Counts> counts(Collection<EventId> ids) {
    UUID municipality = tenant().id();
    if (ids.isEmpty()) {
      return Map.of();
    }
    return sql.counts(municipality, ids.stream().map(EventId::value).distinct().toList()).stream()
        .collect(
            Collectors.toMap(
                RegistrationSql.CountRow::eventId,
                r -> new Counts(r.confirmed(), r.pending(), r.notified(), r.waiting())));
  }

  @Override
  public Optional<Registration> find(RegistrationId id) {
    return sql.find(tenant().id(), id.value());
  }

  @Override
  public Optional<Registration> findByPerson(
      EventId event, String subject, @Nullable UUID dependentId) {
    return dependentId == null
        ? sql.findByPerson(tenant().id(), event.value(), subject)
        : sql.findByDependent(tenant().id(), event.value(), subject, dependentId);
  }

  @Override
  public List<Registration> listByAccount(EventId event, String subject) {
    return sql.listByAccount(tenant().id(), event.value(), subject);
  }

  @Override
  public List<Registration> waitlist(EventId event) {
    return sql.waitlist(tenant().id(), event.value());
  }

  @Override
  public int countActiveWaits(String subject, Instant now) {
    return sql.countActiveWaits(tenant().id(), subject, now);
  }

  @Override
  public List<Registration> noticesToMail(Instant now) {
    return sql.noticesToMail(tenant().id(), now);
  }

  @Override
  public void markNoticeMailed(RegistrationId id, Instant at) {
    sql.markNoticeMailed(tenant().id(), id.value(), at);
  }

  @Override
  public List<EventId> eventsWithWaiting(Instant now) {
    return sql.eventsWithWaiting(tenant().id(), now).stream().map(EventId::new).toList();
  }

  @Override
  public int deleteClosedWaits(Instant now, Instant cancelledBefore) {
    return sql.deleteClosedWaits(tenant().id(), now, cancelledBefore);
  }

  @Override
  public List<Registration> listForEvent(EventId event) {
    return sql.listForEvent(tenant().id(), event.value());
  }

  @Override
  public List<Registration> listForPerson(String subject) {
    return sql.listForPerson(tenant().id(), subject);
  }

  @Override
  public long countForEvent(EventId event) {
    return sql.countForEvent(tenant().id(), event.value());
  }

  @Override
  public long countPendingFrom(Instant now) {
    return sql.countPendingFrom(tenant().id(), now);
  }

  @Override
  public Registration insert(Registration registration) {
    try {
      sql.insert(tenant().id(), registration);
    } catch (DuplicateKeyException e) {
      // A mesma persoa, dúas veces á vez: o índice único decide.
      throw new DomainException(EventError.ALREADY_REGISTERED);
    }
    return registration;
  }

  @Override
  public Registration update(Registration registration) {
    if (sql.update(tenant().id(), registration) == 0) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return sql.find(tenant().id(), registration.id().value())
        .orElseThrow(() -> new DomainException(EventError.REGISTRATION_NOT_FOUND));
  }

  @Override
  public void delete(RegistrationId id) {
    sql.delete(tenant().id(), id.value());
  }

  @Override
  public int anonymizeEndedBefore(Instant cutoff, Instant now) {
    return sql.anonymizeEndedBefore(tenant().id(), cutoff, now);
  }
}
