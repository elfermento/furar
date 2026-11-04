// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence.schedule;

import gal.furar.facility.application.port.out.RequirementProbe;
import gal.furar.facility.domain.Requirement;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Requisito {@code SCHEDULE} do borrador (ADR-0022, punto 3; P8): unha tempada con algún tramo que
 * remate hoxe ou despois, ou que non remate, en Europe/Madrid. Por lote.
 */
@Component
class ScheduleRequirementProbe extends TenantScopedPersistenceAdapter implements RequirementProbe {

  private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

  private final SeasonJpaRepository seasons;
  private final Clock clock;

  ScheduleRequirementProbe(SeasonJpaRepository seasons, Clock clock) {
    this.seasons = seasons;
    this.clock = clock;
  }

  @Override
  public Requirement requirement() {
    return Requirement.SCHEDULE;
  }

  @Override
  public Set<UUID> satisfiedBy(Collection<UUID> facilityIds) {
    tenant();
    if (facilityIds.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        seasons.facilitiesWithSchedule(facilityIds, LocalDate.now(clock.withZone(ZONE))));
  }
}
