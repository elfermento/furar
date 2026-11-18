// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas filtradas polo concello automaticamente ({@code @TenantId}). */
public interface FacilityUsePartJpaRepository extends JpaRepository<FacilityUsePartEntity, UUID> {

  /** As partes vivas dos usos pedidos, na súa orde. */
  List<FacilityUsePartEntity> findByUseIdInAndArchivedAtIsNullOrderByPositionAsc(
      Collection<UUID> useIds);

  /** As partes arquivadas dos usos pedidos (portabilidade). */
  List<FacilityUsePartEntity> findByUseIdInAndArchivedAtIsNotNull(Collection<UUID> useIds);

  /** Todas, tamén as arquivadas (eliminar a instalación). */
  List<FacilityUsePartEntity> findByUseIdIn(Collection<UUID> useIds);
}
