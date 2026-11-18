// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.port.in;

import gal.furar.event.domain.EventId;
import java.util.List;

/**
 * Os eventos na xestión («Instalacións › Eventos»): Xestión e a plataforma dan de alta, editan,
 * publican, cancelan e eliminan; Recepción só os ve. Sen persoas: as inscricións van en {@link
 * EventRegistrationsUseCase}.
 */
public interface ManageEventsUseCase {

  /** Todos os do concello (tamén borradores, cancelados e pasados), do máis novo ao máis vello. */
  List<EventView> list();

  EventView get(EventId id);

  EventView create(EventCommand command);

  /** Cambiar data, hora ou lugar avisa por correo a quen estea apuntado. */
  EventView update(EventId id, long expectedVersion, EventCommand command);

  /** Cancela e avisa por correo a quen estea apuntado. */
  EventView cancel(EventId id, long expectedVersion);

  /** Só sen ninguén apuntado nin pedindo praza ({@code event.has-registrations}). */
  void delete(EventId id);
}
