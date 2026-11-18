// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas filtradas polo concello automaticamente ({@code @TenantId}). */
public interface FacilityUsePeriodJpaRepository
    extends JpaRepository<FacilityUsePeriodEntity, UUID> {

  List<FacilityUsePeriodEntity> findByUseIdInOrderByFromDateAsc(Collection<UUID> useIds);

  /** Todas, tamén as arquivadas (eliminar a instalación). */
  List<FacilityUsePeriodEntity> findByUseIdIn(Collection<UUID> useIds);
}
