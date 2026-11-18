// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.application;

import gal.furar.@module@.application.port.out.@Name@Repository;
import gal.furar.@module@.domain.@Name@;
import gal.furar.@module@.domain.@Name@Id;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.tenancy.TenantContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Porto de saída en memoria, separado por concello coma o real. */
public class InMemory@Name@Repository implements @Name@Repository {

  private final Map<UUID, Map<@Name@Id, @Name@>> byMunicipality = new LinkedHashMap<>();

  private Map<@Name@Id, @Name@> current() {
    return byMunicipality.computeIfAbsent(
        TenantContext.require().id(), k -> new LinkedHashMap<>());
  }

  @Override
  public Optional<@Name@> findById(@Name@Id id) {
    return Optional.ofNullable(current().get(id));
  }

  @Override
  public List<@Name@> findAllById(List<@Name@Id> ids) {
    return ids.stream().map(current()::get).filter(Objects::nonNull).toList();
  }

  @Override
  public PageResult<@Name@> findPage(int page, int size) {
    List<@Name@> all = new ArrayList<>(current().values());
    int from = Math.min(page * size, all.size());
    return new PageResult<>(
        all.subList(from, Math.min(from + size, all.size())), page, size, all.size());
  }

  @Override
  public List<@Name@> findAll() {
    return List.copyOf(current().values());
  }

  @Override
  public @Name@ insert(@Name@ @name@) {
    return store(@name@, 0);
  }

  @Override
  public @Name@ update(@Name@ @name@) {
    @Name@ stored = current().get(@name@.id());
    if (stored == null || !Objects.equals(stored.version(), @name@.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return store(@name@, @name@.version() + 1);
  }

  private @Name@ store(@Name@ @name@, long version) {
    @Name@ saved =
        @Name@.rehydrate(
            @name@.id(),
            @name@.name(),
            @name@.customFields(), // @cf
            version,
            @name@.createdAt(),
            @name@.updatedAt());
    current().put(saved.id(), saved);
    return saved;
  }
}
