// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Consultas filtradas polo concello automaticamente ({@code @TenantId}). */
public interface FacilityUseJpaRepository extends JpaRepository<FacilityUseEntity, UUID> {

  List<FacilityUseEntity> findByFacilityIdAndArchivedAtIsNullOrderByCreatedAtAscIdAsc(
      UUID facilityId);

  List<FacilityUseEntity> findByFacilityIdOrderByCreatedAtAscIdAsc(UUID facilityId);

  Optional<FacilityUseEntity> findByIdAndFacilityId(UUID id, UUID facilityId);

  /** Das instalacións pedidas, as que teñen algún uso vivo (borrador, requisito USES). */
  @Query(
      "select distinct u.facilityId from FacilityUseEntity u"
          + " where u.facilityId in :ids and u.archivedAt is null")
  List<UUID> facilitiesWithActiveUses(Collection<UUID> ids);
}
