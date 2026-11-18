// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.application.port.out.RequirementProbe;
import gal.furar.facility.domain.Requirement;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Borrador calculado por lote (ADR-0022, punto 3): unha consulta por requisito e páxina, nunca por
 * instalación.
 */
public final class FacilityDrafts {

  private final List<RequirementProbe> probes;

  public FacilityDrafts(List<RequirementProbe> probes) {
    this.probes = List.copyOf(probes);
  }

  /** O que lle falta a cada instalación pedida. */
  public Map<UUID, Set<Requirement>> missing(Collection<UUID> facilityIds) {
    Map<UUID, EnumSet<Requirement>> met = new HashMap<>();
    facilityIds.forEach(id -> met.put(id, EnumSet.noneOf(Requirement.class)));
    if (!facilityIds.isEmpty()) {
      for (RequirementProbe probe : probes) {
        for (UUID id : probe.satisfiedBy(facilityIds)) {
          EnumSet<Requirement> set = met.get(id);
          if (set != null) {
            set.add(probe.requirement());
          }
        }
      }
    }
    Map<UUID, Set<Requirement>> missing = new HashMap<>();
    met.forEach((id, set) -> missing.put(id, Requirement.missing(set)));
    return missing;
  }

  public Set<Requirement> missing(UUID facilityId) {
    return missing(List.of(facilityId)).get(facilityId);
  }
}
