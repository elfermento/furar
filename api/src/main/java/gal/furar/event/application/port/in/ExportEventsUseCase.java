// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.port.in;

import java.util.List;

/**
 * Todos os do concello, con todos os seus valores e os nomes do uso e da parte (ADR-0041), para o
 * ficheiro furar-export (ADR-0018).
 */
public interface ExportEventsUseCase {

  List<EventView> exportAll();
}
