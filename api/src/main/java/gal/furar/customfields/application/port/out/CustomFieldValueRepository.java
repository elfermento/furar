// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.port.out;

import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValues;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Valores tipados do concello actual (táboas {@code custom_field_value*}). */
public interface CustomFieldValueRepository {

  /** Valores das entidades indicadas; as que non teñen ningún non aparecen. */
  Map<UUID, CustomFieldValues> findByEntities(CustomFieldTarget target, Collection<UUID> ids);

  /** Borra os valores da entidade nese destino e garda os indicados (xa validados). */
  void replace(CustomFieldTarget target, UUID entityId, CustomFieldValues values);
}
