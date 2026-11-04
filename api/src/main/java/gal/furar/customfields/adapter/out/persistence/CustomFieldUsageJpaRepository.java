// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas filtradas polo concello automaticamente ({@code @TenantId}). */
public interface CustomFieldUsageJpaRepository extends JpaRepository<CustomFieldUsageEntity, UUID> {

  List<CustomFieldUsageEntity> findByFieldIdIn(Collection<UUID> fieldIds);

  List<CustomFieldUsageEntity> findByTarget(String target);
}
