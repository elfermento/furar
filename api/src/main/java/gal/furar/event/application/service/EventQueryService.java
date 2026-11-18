// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.service;

import gal.furar.event.EventQueries;
import gal.furar.event.EventSummary;
import gal.furar.event.application.port.out.EventRepository;
import gal.furar.event.domain.Event;
import gal.furar.event.domain.EventId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa a API pública síncrona ({@link EventQueries}) para outros módulos. Sempre no concello
 * actual (o repositorio falla sen el); sen permisos propios porque non é un caso de uso.
 */
@Service
class EventQueryService implements EventQueries {

  private final EventRepository repository;

  EventQueryService(EventRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<EventSummary> find(UUID id) {
    return repository.findById(new EventId(id)).map(EventQueryService::summary);
  }

  @Override
  @Transactional(readOnly = true)
  public List<EventSummary> findAll(List<UUID> ids) {
    Map<UUID, Event> found =
        repository.findAllById(ids.stream().map(EventId::new).toList()).stream()
            .collect(Collectors.toMap(e -> e.id().value(), Function.identity()));
    return ids.stream().filter(found::containsKey).map(id -> summary(found.get(id))).toList();
  }

  private static EventSummary summary(Event event) {
    return new EventSummary(
        event.id().value(), event.details().name().gl(), event.details().startsAt());
  }
}
