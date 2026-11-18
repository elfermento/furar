// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.application.port.in;

import java.util.List;

/** A lista de incidencias da xestión e o número de novas (o contador do menú). */
public interface ListIncidentsUseCase {

  /** Incidencias como moito nunha lista. */
  int LIMIT = 1000;

  /** As máis recentes ({@value #LIMIT} como moito), da máis nova á máis vella, sen a reserva. */
  List<IncidentView> list();

  /** Cantas hai en estado Nova. */
  long countNew();
}
