// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.application.port.in;

/**
 * O persoal anota el mesmo unha incidencia desde a xestión («Nova incidencia»), en calquera
 * instalación do concello e sen reserva.
 */
public interface RecordIncidentUseCase {

  IncidentView record(ReportIncidentCommand command);
}
