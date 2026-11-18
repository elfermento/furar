// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas filtradas polo concello automaticamente ({@code @TenantId}). */
public interface CustomFieldJpaRepository extends JpaRepository<CustomFieldEntity, UUID> {

  List<CustomFieldEntity> findAllByOrderByCodeAsc();

  List<CustomFieldEntity> findByIdInOrderByCodeAsc(Collection<UUID> ids);

  boolean existsByCode(String code);
}
