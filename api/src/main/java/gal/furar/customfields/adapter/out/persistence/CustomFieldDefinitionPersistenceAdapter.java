// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.out.persistence;

import gal.furar.customfields.application.port.out.CustomFieldDefinitionRepository;
import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.customfields.domain.CustomFieldDefinitionId;
import gal.furar.customfields.domain.CustomFieldError;
import gal.furar.customfields.domain.FieldOption;
import gal.furar.customfields.domain.FieldUsage;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Adaptador JPA das definicións (campo, opcións e usos), sempre dentro dun concello (A7b).
 * Hibernate executa INSERT antes que UPDATE e DELETE nun mesmo {@code flush}; por iso os pasos que
 * dependen da orde (marca de dato persoal fronte a visibilidade dos usos) van con {@code flush}
 * explícito.
 */
@Component
class CustomFieldDefinitionPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements CustomFieldDefinitionRepository {

  private final CustomFieldJpaRepository fields;
  private final CustomFieldOptionJpaRepository options;
  private final CustomFieldUsageJpaRepository usages;
  private final CustomFieldValueJpaRepository values;
  private final CustomFieldValueOptionJpaRepository valueOptions;

  CustomFieldDefinitionPersistenceAdapter(
      CustomFieldJpaRepository fields,
      CustomFieldOptionJpaRepository options,
      CustomFieldUsageJpaRepository usages,
      CustomFieldValueJpaRepository values,
      CustomFieldValueOptionJpaRepository valueOptions) {
    this.fields = fields;
    this.options = options;
    this.usages = usages;
    this.values = values;
    this.valueOptions = valueOptions;
  }

  @Override
  public Optional<CustomFieldDefinition> findById(CustomFieldDefinitionId id) {
    tenant();
    return fields.findById(id.value()).map(e -> toDomain(List.of(e)).getFirst());
  }

  @Override
  public List<CustomFieldDefinition> findByTarget(CustomFieldTarget target) {
    tenant();
    List<UUID> ids =
        usages.findByTarget(target.code()).stream().map(CustomFieldUsageEntity::fieldId).toList();
    return ids.isEmpty() ? List.of() : toDomain(fields.findByIdInOrderByCodeAsc(ids));
  }

  @Override
  public List<CustomFieldDefinition> findAll() {
    tenant();
    return toDomain(fields.findAllByOrderByCodeAsc());
  }

  @Override
  public boolean existsByCode(String code) {
    tenant();
    return fields.existsByCode(code);
  }

  @Override
  public boolean hasValues(CustomFieldDefinitionId id) {
    tenant();
    return values.existsByFieldId(id.value());
  }

  @Override
  public Set<CustomFieldDefinitionId> withValues(Collection<CustomFieldDefinitionId> ids) {
    tenant();
    if (ids.isEmpty()) {
      return Set.of();
    }
    return values.fieldsWithValues(raw(ids)).stream()
        .map(CustomFieldDefinitionId::new)
        .collect(Collectors.toUnmodifiableSet());
  }

  /** As opcións escollidas en seleccións únicas e múltiples: dúas consultas e as opcións. */
  @Override
  public Map<CustomFieldDefinitionId, Set<String>> optionsWithValues(
      Collection<CustomFieldDefinitionId> ids) {
    tenant();
    if (ids.isEmpty()) {
      return Map.of();
    }
    List<UUID> fieldIds = raw(ids);
    Set<UUID> chosen = new HashSet<>(values.chosenOptions(fieldIds));
    chosen.addAll(valueOptions.chosenOptions(fieldIds));
    if (chosen.isEmpty()) {
      return Map.of();
    }
    Map<CustomFieldDefinitionId, Set<String>> result = new HashMap<>();
    for (CustomFieldOptionEntity option : options.findByFieldIdInOrderByCodeAsc(fieldIds)) {
      if (chosen.contains(option.getId())) {
        result
            .computeIfAbsent(new CustomFieldDefinitionId(option.fieldId()), k -> new HashSet<>())
            .add(option.code());
      }
    }
    return result;
  }

  @Override
  public void delete(CustomFieldDefinition definition) {
    tenant();
    List<UUID> id = List.of(definition.id().value());
    usages.deleteAll(usages.findByFieldIdIn(id));
    options.deleteAll(options.findByFieldIdInOrderByCodeAsc(id));
    usages.flush();
    options.flush();
    fields.deleteById(definition.id().value());
    fields.flush();
  }

  private static List<UUID> raw(Collection<CustomFieldDefinitionId> ids) {
    return ids.stream().map(CustomFieldDefinitionId::value).toList();
  }

  @Override
  public CustomFieldDefinition insert(CustomFieldDefinition definition) {
    tenant();
    try {
      fields.saveAndFlush(CustomFieldPersistenceMapper.newEntity(definition));
    } catch (DataIntegrityViolationException e) {
      throw new DomainException(CustomFieldError.CODE_TAKEN, Map.of("code", definition.code()));
    }
    UUID id = definition.id().value();
    for (FieldOption o : definition.spec().options()) {
      options.save(newOption(definition, o));
    }
    for (FieldUsage u : definition.spec().usages()) {
      usages.save(newUsage(id, u, definition.personalData()));
    }
    options.flush();
    return findById(definition.id()).orElseThrow();
  }

  @Override
  public CustomFieldDefinition update(CustomFieldDefinition definition) {
    tenant();
    UUID id = definition.id().value();
    CustomFieldEntity entity =
        fields
            .findById(id)
            .orElseThrow(() -> new DomainException(CustomFieldError.DEFINITION_NOT_FOUND));
    if (!Objects.equals(entity.version(), definition.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    Map<String, CustomFieldUsageEntity> stored =
        byKey(usages.findByFieldIdIn(List.of(id)), CustomFieldUsageEntity::target);
    // A copia de personal_data nos usos segue á do campo (on update cascade) e a base rexeita un
    // uso público dun dato persoal: ao marcar, primeiro deixan de ser públicos os usos; ao
    // desmarcar, primeiro cambia o campo.
    if (definition.personalData() && !entity.personalData()) {
      updateUsages(definition, stored);
      CustomFieldPersistenceMapper.apply(entity, definition);
      fields.flush();
    } else {
      CustomFieldPersistenceMapper.apply(entity, definition);
      fields.flush();
      updateUsages(definition, stored);
    }
    Map<String, FieldUsage> wanted = new HashMap<>();
    definition.spec().usages().forEach(u -> wanted.put(u.target().code(), u));
    // Cambio de sitio: o uso vello vai antes ca o novo (un uso por campo, único na base).
    stored.forEach(
        (target, usage) -> {
          if (!wanted.containsKey(target)) {
            usages.delete(usage);
          }
        });
    usages.flush();
    wanted.forEach(
        (target, usage) -> {
          if (!stored.containsKey(target)) {
            usages.save(newUsage(id, usage, definition.personalData()));
          }
        });
    Map<String, CustomFieldOptionEntity> storedOptions =
        byKey(options.findByFieldIdInOrderByCodeAsc(List.of(id)), CustomFieldOptionEntity::code);
    // As opcións quitadas non teñen valores (compróbao o caso de uso; a FK dos valores tamén).
    Set<String> keptCodes = new HashSet<>();
    definition.spec().options().forEach(o -> keptCodes.add(o.code()));
    storedOptions.forEach(
        (code, option) -> {
          if (!keptCodes.contains(code)) {
            options.delete(option);
          }
        });
    options.flush();
    for (FieldOption o : definition.spec().options()) {
      CustomFieldOptionEntity existing = storedOptions.get(o.code());
      if (existing == null) {
        options.save(newOption(definition, o));
      } else {
        existing.apply(CustomFieldPersistenceMapper.text(o.label()), o.inactive());
      }
    }
    fields.flush();
    return findById(definition.id()).orElseThrow();
  }

  private void updateUsages(
      CustomFieldDefinition definition, Map<String, CustomFieldUsageEntity> stored) {
    for (FieldUsage u : definition.spec().usages()) {
      CustomFieldUsageEntity existing = stored.get(u.target().code());
      if (existing != null) {
        existing.apply(u.required(), u.visibility().name());
      }
    }
    usages.flush();
  }

  private static CustomFieldOptionEntity newOption(CustomFieldDefinition d, FieldOption o) {
    var entity =
        new CustomFieldOptionEntity(Ids.newV7(), d.id().value(), d.type().name(), o.code());
    entity.apply(CustomFieldPersistenceMapper.text(o.label()), o.inactive());
    return entity;
  }

  private static CustomFieldUsageEntity newUsage(UUID fieldId, FieldUsage u, boolean personal) {
    var entity = new CustomFieldUsageEntity(Ids.newV7(), fieldId, u.target().code(), personal);
    entity.apply(u.required(), u.visibility().name());
    return entity;
  }

  /** Cada campo coas súas opcións e usos: tres consultas para toda a lista. */
  private List<CustomFieldDefinition> toDomain(List<CustomFieldEntity> entities) {
    if (entities.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = entities.stream().map(CustomFieldEntity::getId).toList();
    Map<UUID, List<CustomFieldOptionEntity>> optionsByField =
        group(options.findByFieldIdInOrderByCodeAsc(ids), CustomFieldOptionEntity::fieldId);
    Map<UUID, List<CustomFieldUsageEntity>> usagesByField =
        group(usages.findByFieldIdIn(ids), CustomFieldUsageEntity::fieldId);
    List<CustomFieldDefinition> result = new ArrayList<>();
    for (CustomFieldEntity e : entities) {
      result.add(
          CustomFieldPersistenceMapper.toDomain(
              e,
              optionsByField.getOrDefault(e.getId(), List.of()),
              usagesByField.getOrDefault(e.getId(), List.of())));
    }
    return result;
  }

  private static <T> Map<UUID, List<T>> group(Collection<T> rows, Function<T, UUID> key) {
    return rows.stream().collect(Collectors.groupingBy(key));
  }

  private static <T> Map<String, T> byKey(Collection<T> rows, Function<T, String> key) {
    Map<String, T> map = new HashMap<>();
    rows.forEach(r -> map.put(key.apply(r), r));
    return map;
  }
}
