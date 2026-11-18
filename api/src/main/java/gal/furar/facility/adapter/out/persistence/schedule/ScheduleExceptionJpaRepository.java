// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence.schedule;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas filtradas polo concello automaticamente ({@code @TenantId}). */
public interface ScheduleExceptionJpaRepository
    extends JpaRepository<ScheduleExceptionEntity, UUID> {

  List<ScheduleExceptionEntity> findByFacilityIdOrderByDateAsc(UUID facilityId);

  List<ScheduleExceptionEntity> findByFacilityIdAndDateBetweenOrderByDateAsc(
      UUID facilityId, LocalDate from, LocalDate to);

  List<ScheduleExceptionEntity> findByFacilityIdInAndDateBetweenOrderByDateAsc(
      Collection<UUID> facilityIds, LocalDate from, LocalDate to);

  Optional<ScheduleExceptionEntity> findByIdAndFacilityId(UUID id, UUID facilityId);
}
