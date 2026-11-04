// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.port.out;

import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.customfields.domain.CustomFieldDefinitionId;
import gal.furar.shared.domain.CustomFieldTarget;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Definicións do concello actual, cos seus usos e opcións. */
public interface CustomFieldDefinitionRepository {

  Optional<CustomFieldDefinition> findById(CustomFieldDefinitionId id);

  /** As usadas no destino (activas e desactivadas), por código. */
  List<CustomFieldDefinition> findByTarget(CustomFieldTarget target);

  /** Todas as do concello, por código. */
  List<CustomFieldDefinition> findAll();

  boolean existsByCode(String code);

  /** Se hai algún valor gardado do campo. */
  boolean hasValues(CustomFieldDefinitionId id);

  /** Dos campos indicados, os que teñen algún valor gardado. */
  Set<CustomFieldDefinitionId> withValues(Collection<CustomFieldDefinitionId> ids);

  /** Por campo, os códigos das opcións escollidas nalgún valor (só os campos que teñen). */
  Map<CustomFieldDefinitionId, Set<String>> optionsWithValues(
      Collection<CustomFieldDefinitionId> ids);

  /** Borra o campo, as opcións e o uso; quen chama xa comprobou que non ten valores. */
  void delete(CustomFieldDefinition definition);

  CustomFieldDefinition insert(CustomFieldDefinition definition);

  /** Garda os cambios; falla con {@code common.stale-version} se a versión non coincide. */
  CustomFieldDefinition update(CustomFieldDefinition definition);
}
