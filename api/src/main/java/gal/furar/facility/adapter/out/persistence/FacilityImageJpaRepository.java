// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas filtradas polo concello automaticamente ({@code @TenantId}). */
public interface FacilityImageJpaRepository extends JpaRepository<FacilityImageEntity, UUID> {

  /** Na orde da galería. */
  List<FacilityImageEntity> findByFacilityIdInOrderByPositionAsc(Collection<UUID> facilityIds);

  /** As imaxes que son fotos da biblioteca (para saber quen usa cada unha). */
  List<FacilityImageEntity> findByPhotoIdIsNotNull();

  List<FacilityImageEntity> findByPhotoId(UUID photoId);
}
