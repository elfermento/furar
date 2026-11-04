// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence.schedule;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Consultas filtradas polo concello automaticamente ({@code @TenantId}). */
public interface SeasonJpaRepository extends JpaRepository<SeasonEntity, UUID> {

  List<SeasonEntity> findByFacilityIdOrderByStartDateAsc(UUID facilityId);

  List<SeasonEntity> findByFacilityIdInOrderByStartDateAsc(Collection<UUID> facilityIds);

  Optional<SeasonEntity> findByIdAndFacilityId(UUID id, UUID facilityId);

  /**
   * Das instalacións pedidas, as que teñen unha tempada con algún tramo que remata hoxe ou despois,
   * ou que non remata (borrador, requisito SCHEDULE; P8).
   */
  @Query(
      "select distinct s.facilityId from SeasonEntity s"
          + " where s.facilityId in :ids and (s.endDate is null or s.endDate >= :today)"
          + " and exists (select 1 from SeasonHoursEntity h where h.seasonId = s.id)")
  List<UUID> facilitiesWithSchedule(Collection<UUID> ids, LocalDate today);
}
