// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.customfields;

import gal.furar.customfields.CustomFieldColumn;
import gal.furar.customfields.CustomFieldColumns;
import gal.furar.customfields.CustomFieldProjector;
import gal.furar.customfields.CustomFieldValidator;
import gal.furar.customfields.CustomFieldValueStore;
import gal.furar.customfields.ValidationContext;
import gal.furar.facility.PublicCustomFields;
import gal.furar.facility.application.port.out.FacilityCustomFields;
import gal.furar.facility.domain.FacilityId;
import gal.furar.shared.domain.Audience;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.security.Audiences;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Campos propios da instalación pola API pública de {@code customfields} (A19). A audiencia é
 * persoal se quen le ou escribe pode ver datos internos do concello e, se non, pública: unha
 * instalación non ten titular.
 */
@Component
class FacilityCustomFieldsAdapter implements FacilityCustomFields {

  private final CustomFieldValidator validator;
  private final CustomFieldProjector projector;
  private final CustomFieldValueStore store;
  private final CustomFieldColumns columns;
  private final CurrentActor currentActor;

  FacilityCustomFieldsAdapter(
      CustomFieldValidator validator,
      CustomFieldProjector projector,
      CustomFieldValueStore store,
      CustomFieldColumns columns,
      CurrentActor currentActor) {
    this.validator = validator;
    this.projector = projector;
    this.store = store;
    this.columns = columns;
    this.currentActor = currentActor;
  }

  @Override
  public CustomFieldValues validate(FacilityId id, Map<String, ?> raw) {
    return validator.validate(
        CustomFieldTarget.FACILITY,
        raw,
        store.load(CustomFieldTarget.FACILITY, id.value()),
        ValidationContext.of(audience()));
  }

  @Override
  public void replace(FacilityId id, CustomFieldValues values) {
    store.replace(CustomFieldTarget.FACILITY, id.value(), values);
  }

  @Override
  public void removeAll(FacilityId id) {
    store.deleteAll(CustomFieldTarget.FACILITY, id.value());
  }

  @Override
  public CustomFieldValues stored(FacilityId id) {
    return store.load(CustomFieldTarget.FACILITY, id.value());
  }

  @Override
  public CustomFieldValues restore(Map<String, ?> raw) {
    return validator.restore(CustomFieldTarget.FACILITY, raw);
  }

  @Override
  public CustomFieldValues visible(FacilityId id) {
    return projector.project(
        CustomFieldTarget.FACILITY, store.load(CustomFieldTarget.FACILITY, id.value()), audience());
  }

  @Override
  public PublicCustomFields publicFields(Collection<FacilityId> ids) {
    List<PublicCustomFields.Definition> definitions =
        columns.columns(CustomFieldTarget.FACILITY, Audience.PUBLIC).stream()
            .filter(CustomFieldColumn::active)
            .map(FacilityCustomFieldsAdapter::definition)
            .toList();
    if (definitions.isEmpty() || ids.isEmpty()) {
      return new PublicCustomFields(definitions, Map.of());
    }
    Set<String> codes =
        definitions.stream()
            .map(PublicCustomFields.Definition::code)
            .collect(Collectors.toUnmodifiableSet());
    Map<UUID, CustomFieldValues> stored =
        store.loadAll(
            CustomFieldTarget.FACILITY, ids.stream().map(FacilityId::value).distinct().toList());
    List<UUID> keys = new ArrayList<>(stored.keySet());
    List<CustomFieldValues> projected =
        projector.projectAll(
            CustomFieldTarget.FACILITY, keys.stream().map(stored::get).toList(), Audience.PUBLIC);
    Map<UUID, Map<String, Object>> values = new HashMap<>();
    for (int i = 0; i < keys.size(); i++) {
      Map<String, Object> shown = new LinkedHashMap<>();
      projected
          .get(i)
          .toJson()
          .forEach(
              (code, value) -> {
                if (codes.contains(code) && value != null) {
                  shown.put(code, value);
                }
              });
      if (!shown.isEmpty()) {
        values.put(keys.get(i), shown);
      }
    }
    return new PublicCustomFields(definitions, values);
  }

  private static PublicCustomFields.Definition definition(CustomFieldColumn column) {
    return new PublicCustomFields.Definition(
        column.code(),
        column.label(),
        column.type(),
        column.options().entrySet().stream()
            .map(e -> new PublicCustomFields.Option(e.getKey(), e.getValue()))
            .toList());
  }

  private Audience audience() {
    String slug = TenantContext.current().map(TenantRef::slug).orElse(null);
    return Audiences.forFacilities(currentActor.current(), slug);
  }
}
