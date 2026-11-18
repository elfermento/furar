// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.application.port.in;

import java.util.UUID;

/**
 * Os correos dunha incidencia despois do commit, no concello do evento e fóra de toda transacción
 * (ADR-0036): «Nova incidencia» á xestión activa e «Resolvemos o teu aviso» á persoa.
 */
public interface SendIncidentMails {

  void reported(UUID incidentId);

  void resolved(UUID incidentId);
}
