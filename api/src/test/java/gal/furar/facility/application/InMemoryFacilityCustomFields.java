// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import gal.furar.facility.application.port.out.FacilityCustomFields;
import gal.furar.facility.domain.FacilityId;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.DomainException;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Campos propios en memoria para os tests dos casos de uso: lembra o que chega e o que se garda. A
 * validación de verdade próbase en {@code FacilityCustomFieldsIT}.
 */
class InMemoryFacilityCustomFields implements FacilityCustomFields {

  final Map<FacilityId, CustomFieldValues> stored = new HashMap<>();
  @Nullable Map<String, ?> lastRaw;
  @Nullable DomainException rejection;

  @Override
  public CustomFieldValues validate(FacilityId id, Map<String, ?> raw) {
    lastRaw = raw;
    if (rejection != null) {
      throw rejection;
    }
    return CustomFieldValues.empty();
  }

  @Override
  public void replace(FacilityId id, CustomFieldValues values) {
    stored.put(id, values);
  }

  @Override
  public void removeAll(FacilityId id) {
    stored.remove(id);
  }

  @Override
  public CustomFieldValues stored(FacilityId id) {
    return stored.getOrDefault(id, CustomFieldValues.empty());
  }

  @Override
  public CustomFieldValues restore(Map<String, ?> raw) {
    lastRaw = raw;
    return CustomFieldValues.empty();
  }

  @Override
  public CustomFieldValues visible(FacilityId id) {
    return stored.getOrDefault(id, CustomFieldValues.empty());
  }
}
