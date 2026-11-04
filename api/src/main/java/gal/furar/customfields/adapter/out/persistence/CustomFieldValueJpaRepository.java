// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Consultas filtradas polo concello automaticamente ({@code @TenantId}). */
public interface CustomFieldValueJpaRepository extends JpaRepository<CustomFieldValueEntity, UUID> {

  List<CustomFieldValueEntity> findByTargetAndEntityIdIn(String target, Collection<UUID> entityIds);

  boolean existsByFieldId(UUID fieldId);

  /** Dos campos indicados, os que teñen algún valor. */
  @Query("select distinct v.fieldId from CustomFieldValueEntity v where v.fieldId in :fieldIds")
  List<UUID> fieldsWithValues(Collection<UUID> fieldIds);

  /** Opcións escollidas nalgún valor de selección única dos campos indicados. */
  @Query(
      "select distinct v.optionId from CustomFieldValueEntity v"
          + " where v.fieldId in :fieldIds and v.optionId is not null")
  List<UUID> chosenOptions(Collection<UUID> fieldIds);
}
