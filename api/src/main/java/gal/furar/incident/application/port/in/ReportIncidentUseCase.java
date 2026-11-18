// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.application.port.in;

import gal.furar.incident.domain.IncidentId;

/**
 * A veciñanza avisa dun problema nunha instalación que ve no público, desde a ficha ou desde unha
 * reserva súa (ADR-0036). Pide sesión e o correo verificado; a persoa non volve ver o aviso.
 */
public interface ReportIncidentUseCase {

  IncidentId report(ReportIncidentCommand command);
}
