// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident;

import gal.furar.shared.tenancy.TenantAware;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrou unha incidencia nova (ADR-0036). Sen persoas nin texto: quen escoita le o que precise.
 *
 * @param municipalityId concello
 * @param incidentId a incidencia
 */
public record IncidentReported(UUID municipalityId, UUID incidentId) implements TenantAware {

  public IncidentReported {
    Objects.requireNonNull(municipalityId, "municipalityId");
    Objects.requireNonNull(incidentId, "incidentId");
  }
}
