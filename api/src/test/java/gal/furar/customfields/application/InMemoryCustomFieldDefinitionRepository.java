// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application;

import gal.furar.customfields.application.port.out.CustomFieldDefinitionRepository;
import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.customfields.domain.CustomFieldDefinitionId;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.TenantContext;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Doble en memoria por concello, coa semántica de versión e de concello do adaptador JPA. */
public class InMemoryCustomFieldDefinitionRepository implements CustomFieldDefinitionRepository {

  private final Map<UUID, Map<CustomFieldDefinitionId, CustomFieldDefinition>> byTenant =
      new LinkedHashMap<>();

  private Map<CustomFieldDefinitionId, CustomFieldDefinition> store() {
    return byTenant.computeIfAbsent(TenantContext.require().id(), k -> new LinkedHashMap<>());
  }

  @Override
  public Optional<CustomFieldDefinition> findById(CustomFieldDefinitionId id) {
    return Optional.ofNullable(store().get(id));
  }

  /** Campos con valores gardados, para probar que non se eliminan nin cambian de sitio. */
  public final Set<CustomFieldDefinitionId> withValues = new HashSet<>();

  /** Opcións con valores gardados, por campo. */
  public final Map<CustomFieldDefinitionId, Set<String>> optionsWithValues = new HashMap<>();

  @Override
  public List<CustomFieldDefinition> findByTarget(CustomFieldTarget target) {
    return findAll().stream().filter(d -> d.usedIn(target)).toList();
  }

  @Override
  public List<CustomFieldDefinition> findAll() {
    return store().values().stream()
        .sorted(Comparator.comparing(CustomFieldDefinition::code))
        .toList();
  }

  @Override
  public boolean existsByCode(String code) {
    return store().values().stream().anyMatch(d -> d.code().equals(code));
  }

  @Override
  public boolean hasValues(CustomFieldDefinitionId id) {
    return withValues.contains(id);
  }

  @Override
  public Set<CustomFieldDefinitionId> withValues(Collection<CustomFieldDefinitionId> ids) {
    Set<CustomFieldDefinitionId> result = new HashSet<>(ids);
    result.retainAll(withValues);
    return result;
  }

  @Override
  public Map<CustomFieldDefinitionId, Set<String>> optionsWithValues(
      Collection<CustomFieldDefinitionId> ids) {
    Map<CustomFieldDefinitionId, Set<String>> result = new HashMap<>();
    ids.forEach(
        id -> {
          if (optionsWithValues.containsKey(id)) {
            result.put(id, optionsWithValues.get(id));
          }
        });
    return result;
  }

  @Override
  public void delete(CustomFieldDefinition definition) {
    store().remove(definition.id());
  }

  @Override
  public CustomFieldDefinition insert(CustomFieldDefinition d) {
    CustomFieldDefinition saved = withVersion(d, 0);
    store().put(d.id(), saved);
    return saved;
  }

  @Override
  public CustomFieldDefinition update(CustomFieldDefinition d) {
    CustomFieldDefinition current = store().get(d.id());
    if (current == null || !Objects.equals(current.version(), d.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    CustomFieldDefinition saved = withVersion(d, d.version() + 1);
    store().put(d.id(), saved);
    return saved;
  }

  private static CustomFieldDefinition withVersion(CustomFieldDefinition d, long version) {
    return CustomFieldDefinition.rehydrate(
        d.id(), d.code(), d.type(), d.spec(), d.status(), version, d.createdAt(), d.updatedAt());
  }
}
