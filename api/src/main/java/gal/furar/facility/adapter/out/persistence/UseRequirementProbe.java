// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import gal.furar.facility.application.port.out.RequirementProbe;
import gal.furar.facility.domain.Requirement;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Requisito {@code USES} do borrador (ADR-0022, punto 3): polo menos un uso vivo. Por lote. */
@Component
class UseRequirementProbe extends TenantScopedPersistenceAdapter implements RequirementProbe {

  private final FacilityUseJpaRepository uses;

  UseRequirementProbe(FacilityUseJpaRepository uses) {
    this.uses = uses;
  }

  @Override
  public Requirement requirement() {
    return Requirement.USES;
  }

  @Override
  public Set<UUID> satisfiedBy(Collection<UUID> facilityIds) {
    tenant();
    if (facilityIds.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(uses.facilitiesWithActiveUses(facilityIds));
  }
}
