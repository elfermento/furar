// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident;

import gal.furar.shared.tenancy.TenantAware;
import java.util.Objects;
import java.util.UUID;

/**
 * Un aviso da veciñanza pasou a resolto (ADR-0036): toca o correo á persoa. Sen persoas nin texto.
 *
 * @param municipalityId concello
 * @param incidentId a incidencia
 */
public record IncidentResolved(UUID municipalityId, UUID incidentId) implements TenantAware {

  public IncidentResolved {
    Objects.requireNonNull(municipalityId, "municipalityId");
    Objects.requireNonNull(incidentId, "incidentId");
  }
}
