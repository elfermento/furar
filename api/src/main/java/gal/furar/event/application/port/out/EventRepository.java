// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.port.out;

import gal.furar.event.domain.Event;
import gal.furar.event.domain.EventId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Porto de saída. Sempre dentro do concello actual: o adaptador non recibe o concello. */
public interface EventRepository {

  Optional<Event> findById(EventId id);

  /** Os que existan; sen orde garantida. */
  List<Event> findAllById(Collection<EventId> ids);

  /** Todos os do concello, por data do evento (portabilidade, ADR-0018, e a lista da xestión). */
  List<Event> findAll();

  /**
   * Os publicados e sen cancelar que aínda non pasaron (un evento dun día, antes de que empece;
   * unha actividade, antes de que remate a última sesión), por data.
   */
  List<Event> findVisibleFrom(Instant now);

  Event insert(Event event);

  /** Garda os cambios se a versión coincide coa gardada; se non, {@code common.stale-version}. */
  Event update(Event event);

  void delete(EventId id);
}
