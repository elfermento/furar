// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Consultas filtradas polo concello automaticamente ({@code @TenantId}). */
public interface FacilitySportJpaRepository extends JpaRepository<FacilitySportEntity, UUID> {

  /** Na orde da tarxeta Datos. */
  List<FacilitySportEntity> findByFacilityIdInOrderByPositionAsc(Collection<UUID> facilityIds);

  /** Das instalacións pedidas, as que teñen algún deporte (borrador, unha consulta por lote). */
  @Query("select distinct s.facilityId from FacilitySportEntity s where s.facilityId in :ids")
  List<UUID> facilitiesWithSports(Collection<UUID> ids);
}
