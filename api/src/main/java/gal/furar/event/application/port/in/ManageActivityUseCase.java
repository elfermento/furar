// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.port.in;

import gal.furar.booking.ActivityPlan;
import gal.furar.event.domain.EventId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * As sesións dunha actividade con horario na xestión (ADR-0041): a vista previa antes de crear, a
 * lista, cancelar unha sesión e rematar a actividade antes.
 */
public interface ManageActivityUseCase {

  /**
   * As sesións que crearía ese horario e os días que non se crean (nunha instalación, coas regras
   * das tempadas). Non escribe nin bloquea.
   */
  ActivityPlan preview(@Nullable UUID facilityId, EventCommand.ScheduleCommand schedule);

  /** As sesións dunha actividade, por data, con cantas presenzas e faltas hai en cada unha. */
  List<SessionView> sessions(EventId id);

  /**
   * Cancela unha sesión que aínda non empezou nin ten lista: solta a hora e avisa por correo ás
   * persoas con praza.
   */
  SessionView cancelSession(EventId id, UUID sessionId);

  /**
   * Remata a actividade desde un día: cancela as sesións dese día en diante que non empezaron nin
   * teñen lista, solta as súas horas e avisa por correo ás persoas con praza.
   */
  EventView end(EventId id, LocalDate from);
}
