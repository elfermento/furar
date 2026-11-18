// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas filtradas polo concello automaticamente ({@code @TenantId}). */
public interface LibraryPhotoJpaRepository extends JpaRepository<LibraryPhotoEntity, UUID> {

  /** A biblioteca: sen as pendentes, da máis nova á máis vella. */
  List<LibraryPhotoEntity> findByPendingUntilIsNullOrderByCreatedAtDescIdDesc();

  /** As pendentes caducadas. */
  List<LibraryPhotoEntity> findByPendingUntilBefore(Instant now);
}
