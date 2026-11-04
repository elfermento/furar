// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Consultas filtradas polo concello automaticamente ({@code @TenantId}). */
public interface CustomFieldValueOptionJpaRepository
    extends JpaRepository<CustomFieldValueOptionEntity, CustomFieldValueOptionEntity.Key> {

  List<CustomFieldValueOptionEntity> findByValueIdIn(Collection<UUID> valueIds);

  /** Opcións marcadas nalgún valor de selección múltiple dos campos indicados. */
  @Query(
      "select distinct o.optionId from CustomFieldValueOptionEntity o"
          + " where o.fieldId in :fieldIds")
  List<UUID> chosenOptions(Collection<UUID> fieldIds);
}
