// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.out;

import gal.furar.facility.domain.Requirement;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Quen sabe se un requisito do borrador está cumprido (ADR-0022, punto 3): un bean por requisito,
 * cada un no seu subpaquete (deportes, usos, horario), cunha consulta por lote. Sen bean para un
 * requisito, ese requisito falta sempre.
 */
public interface RequirementProbe {

  Requirement requirement();

  /** Das instalacións pedidas, as que o cumpren. */
  Set<UUID> satisfiedBy(Collection<UUID> facilityIds);
}
