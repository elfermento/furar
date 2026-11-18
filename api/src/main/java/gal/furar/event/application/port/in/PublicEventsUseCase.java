// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.port.in;

import gal.furar.event.domain.EventId;
import java.util.List;

/** Os eventos que ve a veciñanza, sen sesión: «Próximos eventos» na portada e a ficha. */
public interface PublicEventsUseCase {

  /**
   * Os publicados e sen cancelar que aínda non pasaron (unha actividade, ata a última sesión), por
   * data.
   */
  List<EventView> upcoming();

  /** Un publicado e sen cancelar (tamén xa pasado); se non, {@code event.not-found}. */
  EventView published(EventId id);
}
