// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.out.persistence;

import gal.furar.customfields.application.port.out.CustomFieldValueRepository;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValue;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Adaptador JPA dos valores tipados (A7b). Substituír = borrar os da entidade, {@code flush} e
 * inserir os novos: Hibernate executaría os INSERT antes ca os DELETE e chocarían co único
 * (entidade, campo).
 */
@Component
class CustomFieldValuePersistenceAdapter extends TenantScopedPersistenceAdapter
    implements CustomFieldValueRepository {

  private final CustomFieldJpaRepository fields;
  private final CustomFieldOptionJpaRepository options;
  private final CustomFieldUsageJpaRepository usages;
  private final CustomFieldValueJpaRepository values;
  private final CustomFieldValueOptionJpaRepository choices;

  CustomFieldValuePersistenceAdapter(
      CustomFieldJpaRepository fields,
      CustomFieldOptionJpaRepository options,
      CustomFieldUsageJpaRepository usages,
      CustomFieldValueJpaRepository values,
      CustomFieldValueOptionJpaRepository choices) {
    this.fields = fields;
    this.options = options;
    this.usages = usages;
    this.values = values;
    this.choices = choices;
  }

  @Override
  public Map<UUID, CustomFieldValues> findByEntities(
      CustomFieldTarget target, Collection<UUID> ids) {
    tenant();
    List<CustomFieldValueEntity> rows = values.findByTargetAndEntityIdIn(target.code(), ids);
    if (rows.isEmpty()) {
      return Map.of();
    }
    Set<UUID> fieldIds =
        rows.stream().map(CustomFieldValueEntity::fieldId).collect(Collectors.toSet());
    Map<UUID, String> codes = new HashMap<>();
    fields.findAllById(fieldIds).forEach(f -> codes.put(f.getId(), f.code()));
    Map<UUID, String> optionCodes = new HashMap<>();
    options
        .findByFieldIdInOrderByCodeAsc(fieldIds)
        .forEach(o -> optionCodes.put(o.getId(), o.code()));
    List<UUID> multi =
        rows.stream()
            .filter(v -> v.fieldType().equals("MULTI_SELECT"))
            .map(CustomFieldValueEntity::getId)
            .toList();
    Map<UUID, List<String>> chosen = new HashMap<>();
    if (!multi.isEmpty()) {
      choices
          .findByValueIdIn(multi)
          .forEach(
              c ->
                  chosen
                      .computeIfAbsent(c.valueId(), k -> new ArrayList<>())
                      .add(optionCodes.get(c.optionId())));
    }
    Map<UUID, Map<String, CustomFieldValue>> byEntity = new LinkedHashMap<>();
    for (CustomFieldValueEntity row : rows) {
      byEntity
          .computeIfAbsent(row.entityId(), k -> new TreeMap<>())
          .put(codes.get(row.fieldId()), value(row, optionCodes, chosen));
    }
    Map<UUID, CustomFieldValues> result = new LinkedHashMap<>();
    byEntity.forEach((entity, map) -> result.put(entity, CustomFieldValues.fromStorage(map)));
    return result;
  }

  private static CustomFieldValue value(
      CustomFieldValueEntity row, Map<UUID, String> optionCodes, Map<UUID, List<String>> chosen) {
    return switch (row.fieldType()) {
      case "TEXT", "LONG_TEXT" -> new CustomFieldValue.Text(row.textValue());
      case "NUMBER" -> new CustomFieldValue.Number(row.numberValue());
      case "DATE" -> new CustomFieldValue.Date(row.dateValue());
      case "BOOLEAN" -> new CustomFieldValue.Bool(row.boolValue());
      case "SELECT" -> new CustomFieldValue.Choice(optionCodes.get(row.optionId()));
      case "MULTI_SELECT" ->
          new CustomFieldValue.Choices(
              chosen.getOrDefault(row.getId(), List.of()).stream().sorted().toList());
      default -> throw new IllegalStateException("Unknown custom field type " + row.fieldType());
    };
  }

  @Override
  public void replace(CustomFieldTarget target, UUID entityId, CustomFieldValues newValues) {
    tenant();
    List<CustomFieldValueEntity> old =
        values.findByTargetAndEntityIdIn(target.code(), List.of(entityId));
    if (!old.isEmpty()) {
      choices.deleteAll(
          choices.findByValueIdIn(old.stream().map(CustomFieldValueEntity::getId).toList()));
      choices.flush();
      values.deleteAll(old);
      values.flush();
    }
    if (newValues.isEmpty()) {
      return;
    }
    List<UUID> fieldIds =
        usages.findByTarget(target.code()).stream().map(CustomFieldUsageEntity::fieldId).toList();
    Map<String, CustomFieldEntity> byCode = new HashMap<>();
    fields.findAllById(fieldIds).forEach(f -> byCode.put(f.code(), f));
    Map<UUID, Map<String, UUID>> optionIds = new HashMap<>();
    options
        .findByFieldIdInOrderByCodeAsc(fieldIds)
        .forEach(
            o ->
                optionIds
                    .computeIfAbsent(o.fieldId(), k -> new HashMap<>())
                    .put(o.code(), o.getId()));
    List<CustomFieldValueOptionEntity> marked = new ArrayList<>();
    for (Map.Entry<String, CustomFieldValue> entry : newValues.asMap().entrySet()) {
      CustomFieldEntity field = byCode.get(entry.getKey());
      if (field == null) {
        throw new IllegalStateException("No custom field " + entry.getKey() + " in " + target);
      }
      Map<String, UUID> fieldOptions = optionIds.getOrDefault(field.getId(), Map.of());
      var row =
          new CustomFieldValueEntity(
              Ids.newV7(), field.getId(), field.type(), target.code(), entityId);
      switch (entry.getValue()) {
        case CustomFieldValue.Text(String text) -> row.text(text);
        case CustomFieldValue.Number(var number) -> row.number(number);
        case CustomFieldValue.Date(var date) -> row.date(date);
        case CustomFieldValue.Bool(boolean bool) -> row.bool(bool);
        case CustomFieldValue.Choice(String code) -> row.option(option(fieldOptions, code));
        case CustomFieldValue.Choices(List<String> codes) ->
            codes.forEach(
                code ->
                    marked.add(
                        new CustomFieldValueOptionEntity(
                            row.getId(), field.getId(), option(fieldOptions, code))));
      }
      values.save(row);
    }
    values.flush();
    choices.saveAll(marked);
    choices.flush();
  }

  private static UUID option(Map<String, UUID> fieldOptions, String code) {
    UUID id = fieldOptions.get(code);
    if (id == null) {
      throw new IllegalStateException("No custom field option " + code);
    }
    return id;
  }
}
