// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.adapter.out.persistence;

import gal.furar.customfields.CustomFieldValueStore; // @cf
import gal.furar.@module@.application.port.out.@Name@Repository;
import gal.furar.@module@.domain.@Name@;
import gal.furar.@module@.domain.@Name@Error;
import gal.furar.@module@.domain.@Name@Id;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.CustomFieldValues; // @cf
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Adaptador JPA, sempre dentro dun concello (A7b): sen concello falla antes de consultar.
 * Campos personalizados (ADR-0011): os valores van nas táboas tipadas de customfields; gárdanse // @cf
 * na mesma transacción que a fila, que sobe de versión, e lense dunha consulta por lista. // @cf
 */
@Component
class @Name@PersistenceAdapter extends TenantScopedPersistenceAdapter
    implements @Name@Repository {

  private final @Name@JpaRepository jpa;
  private final CustomFieldValueStore customFields; // @cf

  @Name@PersistenceAdapter(
      CustomFieldValueStore customFields, // @cf
      @Name@JpaRepository jpa) {
    this.jpa = jpa;
    this.customFields = customFields; // @cf
  }

  @Override
  public Optional<@Name@> findById(@Name@Id id) {
    tenant();
    return jpa.findById(id.value()).map(e -> toDomain(List.of(e)).getFirst());
  }

  @Override
  public List<@Name@> findAllById(List<@Name@Id> ids) {
    tenant();
    Map<UUID, @Name@> found =
        toDomain(jpa.findAllById(ids.stream().map(@Name@Id::value).toList())).stream()
            .collect(Collectors.toMap(f -> f.id().value(), Function.identity()));
    return ids.stream().map(id -> found.get(id.value())).filter(Objects::nonNull).toList();
  }

  @Override
  public PageResult<@Name@> findPage(int page, int size) {
    tenant();
    Page<@Name@Entity> result =
        jpa.findAll(PageRequest.of(page, size, Sort.by("createdAt", "id")));
    return new PageResult<>(
        toDomain(result.getContent()),
        page,
        size,
        result.getTotalElements());
  }

  @Override
  public List<@Name@> findAll() {
    tenant();
    return toDomain(jpa.findAll(Sort.by("createdAt", "id")));
  }

  @Override
  public @Name@ insert(@Name@ @name@) {
    tenant();
    @Name@Entity saved = jpa.saveAndFlush(@Name@PersistenceMapper.newEntity(@name@));
    customFields.replace(@Name@.CUSTOM_FIELD_TARGET, saved.getId(), @name@.customFields()); // @cf
    return @Name@PersistenceMapper.toDomain(
        @name@.customFields(), // @cf
        saved);
  }

  @Override
  public @Name@ update(@Name@ @name@) {
    tenant();
    @Name@Entity entity =
        jpa.findById(@name@.id().value())
            .orElseThrow(() -> new DomainException(@Name@Error.NOT_FOUND));
    if (!Objects.equals(entity.version(), @name@.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    @Name@PersistenceMapper.apply(entity, @name@);
    @Name@Entity saved = jpa.saveAndFlush(entity);
    customFields.replace(@Name@.CUSTOM_FIELD_TARGET, saved.getId(), @name@.customFields()); // @cf
    return @Name@PersistenceMapper.toDomain(
        @name@.customFields(), // @cf
        saved);
  }

  /** Entidades → dominio; os campos personalizados, dunha consulta por lista (ARQ-6). */
  private List<@Name@> toDomain(List<@Name@Entity> entities) {
    // @cf{
    Map<UUID, CustomFieldValues> values =
        customFields.loadAll(
            @Name@.CUSTOM_FIELD_TARGET, entities.stream().map(@Name@Entity::getId).toList());
    // @cf}
    return entities.stream()
        .map(
            e ->
                @Name@PersistenceMapper.toDomain(
                    values.getOrDefault(e.getId(), CustomFieldValues.empty()), // @cf
                    e))
        .toList();
  }
}
