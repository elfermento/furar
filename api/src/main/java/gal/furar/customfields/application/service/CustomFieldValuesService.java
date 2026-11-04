// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.service;

import gal.furar.customfields.CustomFieldProjector;
import gal.furar.customfields.CustomFieldValidator;
import gal.furar.customfields.CustomFieldValueStore;
import gal.furar.customfields.ValidationContext;
import gal.furar.customfields.application.port.out.CustomFieldDefinitionRepository;
import gal.furar.customfields.application.port.out.CustomFieldValueRepository;
import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.customfields.domain.ValueProjector;
import gal.furar.customfields.domain.ValuesValidator;
import gal.furar.shared.domain.Audience;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValue;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.DomainException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa a API pública do módulo: validación, proxección e almacén dos valores coas definicións
 * do concello actual. Únese á transacción do caso de uso que o chama; as escrituras esíxena.
 */
@Service
public class CustomFieldValuesService
    implements CustomFieldValidator, CustomFieldProjector, CustomFieldValueStore {

  private final CustomFieldDefinitionRepository definitions;
  private final CustomFieldValueRepository values;
  private final CustomFieldTargets targets;

  public CustomFieldValuesService(
      CustomFieldDefinitionRepository definitions,
      CustomFieldValueRepository values,
      CustomFieldTargets targets) {
    this.definitions = definitions;
    this.values = values;
    this.targets = targets;
  }

  @Override
  @Transactional(readOnly = true)
  public CustomFieldValues validate(
      CustomFieldTarget target,
      Map<String, ?> raw,
      CustomFieldValues previous,
      ValidationContext context) {
    CustomFieldValues validated =
        CustomFieldValues.trusted(
            ValuesValidator.validate(
                target, definitions.findByTarget(target), raw, previous, context.writer()));
    ValuesValidator.checkSize(validated);
    return validated;
  }

  @Override
  @Transactional(readOnly = true)
  public CustomFieldValues restore(CustomFieldTarget target, Map<String, ?> raw) {
    CustomFieldValues restored =
        CustomFieldValues.trusted(
            ValuesValidator.restore(target, definitions.findByTarget(target), raw));
    ValuesValidator.checkSize(restored);
    return restored;
  }

  @Override
  @Transactional(readOnly = true)
  public CustomFieldValues project(
      CustomFieldTarget target, CustomFieldValues stored, Audience viewer) {
    if (stored.isEmpty()) {
      return stored;
    }
    return CustomFieldValues.trusted(
        ValueProjector.project(target, definitions.findByTarget(target), stored, viewer));
  }

  @Override
  @Transactional(readOnly = true)
  public List<CustomFieldValues> projectAll(
      CustomFieldTarget target, List<CustomFieldValues> stored, Audience viewer) {
    if (stored.stream().allMatch(CustomFieldValues::isEmpty)) {
      return List.copyOf(stored);
    }
    List<CustomFieldDefinition> used = definitions.findByTarget(target);
    return stored.stream()
        .map(
            v ->
                v.isEmpty()
                    ? v
                    : CustomFieldValues.trusted(ValueProjector.project(target, used, v, viewer)))
        .toList();
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void replace(CustomFieldTarget target, UUID entityId, CustomFieldValues newValues) {
    if (!targets.ownedByCurrentMunicipality(target, entityId)) {
      throw new DomainException(CommonError.NOT_FOUND);
    }
    ValuesValidator.checkSize(newValues);
    values.replace(target, entityId, newValues);
  }

  @Override
  @Transactional(readOnly = true)
  public CustomFieldValues load(CustomFieldTarget target, UUID entityId) {
    return values
        .findByEntities(target, List.of(entityId))
        .getOrDefault(entityId, CustomFieldValues.empty());
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, CustomFieldValues> loadAll(
      CustomFieldTarget target, Collection<UUID> entityIds) {
    if (entityIds.isEmpty()) {
      return Map.of();
    }
    return values.findByEntities(target, Set.copyOf(entityIds));
  }

  @Override
  @Transactional(readOnly = true)
  public CustomFieldValues personalData(CustomFieldTarget target, UUID entityId) {
    return keep(load(target, entityId), personalCodes(target)::contains);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void erasePersonalData(CustomFieldTarget target, UUID entityId) {
    Set<String> personal = personalCodes(target);
    replace(target, entityId, keep(load(target, entityId), c -> !personal.contains(c)));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void deleteAll(CustomFieldTarget target, UUID entityId) {
    replace(target, entityId, CustomFieldValues.empty());
  }

  private Set<String> personalCodes(CustomFieldTarget target) {
    return definitions.findByTarget(target).stream()
        .filter(CustomFieldDefinition::personalData)
        .map(CustomFieldDefinition::code)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static CustomFieldValues keep(CustomFieldValues stored, Predicate<String> code) {
    Map<String, CustomFieldValue> kept = new LinkedHashMap<>();
    stored
        .asMap()
        .forEach(
            (c, v) -> {
              if (code.test(c)) {
                kept.put(c, v);
              }
            });
    return CustomFieldValues.trusted(kept);
  }
}
