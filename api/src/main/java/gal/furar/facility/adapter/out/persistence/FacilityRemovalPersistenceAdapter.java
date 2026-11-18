// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import gal.furar.facility.adapter.out.persistence.schedule.BlockJpaRepository;
import gal.furar.facility.adapter.out.persistence.schedule.ScheduleExceptionJpaRepository;
import gal.furar.facility.adapter.out.persistence.schedule.SeasonEntity;
import gal.furar.facility.adapter.out.persistence.schedule.SeasonHoursJpaRepository;
import gal.furar.facility.adapter.out.persistence.schedule.SeasonJpaRepository;
import gal.furar.facility.application.port.out.FacilityRemoval;
import gal.furar.facility.domain.FacilityId;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Borra a instalación e o que depende dela, das follas á raíz (as FK sen cascada mándano): partes e
 * períodos, usos (tamén os arquivados), horas e tempadas, excepcións, bloqueos, lista de imaxes,
 * deportes e servizos e, ao final, a instalación. Por entidades, filtradas polo concello ({@code
 * TenantId}) e coa RLS da transacción (A7b).
 */
@Component
class FacilityRemovalPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements FacilityRemoval {

  private final FacilityJpaRepository facilities;
  private final FacilityUseJpaRepository uses;
  private final FacilityUsePartJpaRepository parts;
  private final FacilityUsePeriodJpaRepository periods;
  private final SeasonJpaRepository seasons;
  private final SeasonHoursJpaRepository hours;
  private final ScheduleExceptionJpaRepository exceptions;
  private final BlockJpaRepository blocks;
  private final FacilityImageJpaRepository images;
  private final FacilitySportJpaRepository sports;
  private final FacilityAmenityJpaRepository amenities;

  FacilityRemovalPersistenceAdapter(
      FacilityJpaRepository facilities,
      FacilityUseJpaRepository uses,
      FacilityUsePartJpaRepository parts,
      FacilityUsePeriodJpaRepository periods,
      SeasonJpaRepository seasons,
      SeasonHoursJpaRepository hours,
      ScheduleExceptionJpaRepository exceptions,
      BlockJpaRepository blocks,
      FacilityImageJpaRepository images,
      FacilitySportJpaRepository sports,
      FacilityAmenityJpaRepository amenities) {
    this.facilities = facilities;
    this.uses = uses;
    this.parts = parts;
    this.periods = periods;
    this.seasons = seasons;
    this.hours = hours;
    this.exceptions = exceptions;
    this.blocks = blocks;
    this.images = images;
    this.sports = sports;
    this.amenities = amenities;
  }

  @Override
  public void remove(FacilityId id) {
    tenant();
    UUID facility = id.value();
    List<FacilityUseEntity> facilityUses = uses.findByFacilityIdOrderByCreatedAtAscIdAsc(facility);
    List<UUID> useIds = facilityUses.stream().map(FacilityUseEntity::getId).toList();
    if (!useIds.isEmpty()) {
      parts.deleteAll(parts.findByUseIdIn(useIds));
      periods.deleteAll(periods.findByUseIdIn(useIds));
      parts.flush();
    }
    uses.deleteAll(facilityUses);
    List<SeasonEntity> facilitySeasons = seasons.findByFacilityIdOrderByStartDateAsc(facility);
    List<UUID> seasonIds = facilitySeasons.stream().map(SeasonEntity::getId).toList();
    if (!seasonIds.isEmpty()) {
      hours.deleteAll(hours.findBySeasonIdIn(seasonIds));
    }
    seasons.deleteAll(facilitySeasons);
    exceptions.deleteAll(exceptions.findByFacilityIdOrderByDateAsc(facility));
    blocks.deleteAll(blocks.findByFacilityIdOrderByStartsAtAsc(facility));
    images.deleteAll(images.findByFacilityIdInOrderByPositionAsc(List.of(facility)));
    sports.deleteAll(sports.findByFacilityIdInOrderByPositionAsc(List.of(facility)));
    amenities.deleteAll(amenities.findByFacilityIdIn(List.of(facility)));
    uses.flush();
    facilities.findById(facility).ifPresent(facilities::delete);
    facilities.flush();
  }
}
