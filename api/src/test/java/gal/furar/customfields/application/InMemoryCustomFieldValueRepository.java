// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application;

import gal.furar.customfields.application.port.out.CustomFieldValueRepository;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.tenancy.TenantContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Doble en memoria dos valores, por concello e destino. */
public class InMemoryCustomFieldValueRepository implements CustomFieldValueRepository {

  private final Map<String, CustomFieldValues> rows = new HashMap<>();

  private static String key(CustomFieldTarget target, UUID entityId) {
    return TenantContext.require().id() + "/" + target.code() + "/" + entityId;
  }

  @Override
  public Map<UUID, CustomFieldValues> findByEntities(
      CustomFieldTarget target, Collection<UUID> ids) {
    Map<UUID, CustomFieldValues> result = new LinkedHashMap<>();
    for (UUID id : ids) {
      CustomFieldValues values = rows.get(key(target, id));
      if (values != null) {
        result.put(id, values);
      }
    }
    return result;
  }

  @Override
  public void replace(CustomFieldTarget target, UUID entityId, CustomFieldValues values) {
    if (values.isEmpty()) {
      rows.remove(key(target, entityId));
    } else {
      rows.put(key(target, entityId), values);
    }
  }
}
