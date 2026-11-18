// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.out.persistence;

import gal.furar.event.application.port.out.EventRepository;
import gal.furar.event.domain.Event;
import gal.furar.event.domain.EventError;
import gal.furar.event.domain.EventId;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** Adaptador JPA, sempre dentro dun concello (A7b): sen concello falla antes de consultar. */
@Component
class EventPersistenceAdapter extends TenantScopedPersistenceAdapter implements EventRepository {

  private final EventJpaRepository jpa;

  EventPersistenceAdapter(EventJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<Event> findById(EventId id) {
    tenant();
    return jpa.findById(id.value()).map(EventPersistenceMapper::toDomain);
  }

  @Override
  public List<Event> findAllById(Collection<EventId> ids) {
    tenant();
    return jpa.findAllById(ids.stream().map(EventId::value).distinct().toList()).stream()
        .map(EventPersistenceMapper::toDomain)
        .toList();
  }

  @Override
  public List<Event> findAll() {
    tenant();
    return jpa.findAll(Sort.by("startsAt", "id")).stream()
        .map(EventPersistenceMapper::toDomain)
        .toList();
  }

  @Override
  public List<Event> findVisibleFrom(Instant now) {
    tenant();
    return jpa
        .findByPublishedTrueAndCancelledAtIsNullAndEndsAtAfterOrderByStartsAtAscIdAsc(now)
        .stream()
        .map(EventPersistenceMapper::toDomain)
        .toList();
  }

  @Override
  public Event insert(Event event) {
    tenant();
    EventEntity saved = jpa.saveAndFlush(EventPersistenceMapper.newEntity(event));
    return EventPersistenceMapper.toDomain(saved);
  }

  @Override
  public Event update(Event event) {
    tenant();
    EventEntity entity =
        jpa.findById(event.id().value())
            .orElseThrow(() -> new DomainException(EventError.NOT_FOUND));
    if (!Objects.equals(entity.version(), event.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    EventPersistenceMapper.apply(entity, event);
    EventEntity saved = jpa.saveAndFlush(entity);
    return EventPersistenceMapper.toDomain(saved);
  }

  @Override
  public void delete(EventId id) {
    tenant();
    jpa.deleteById(id.value());
    jpa.flush();
  }
}
