// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application;

import gal.furar.municipality.application.port.out.MunicipalityRepository;
import gal.furar.municipality.domain.Municipality;
import gal.furar.municipality.domain.MunicipalityError;
import gal.furar.municipality.domain.MunicipalityId;
import gal.furar.municipality.domain.Slug;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.PageResult;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Doble en memoria do porto de saída, coa mesma semántica de versión que o adaptador JPA. */
public class InMemoryMunicipalityRepository implements MunicipalityRepository {

  final Map<MunicipalityId, Municipality> store = new LinkedHashMap<>();

  @Override
  public Optional<Municipality> findById(MunicipalityId id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public boolean existsBySlug(Slug slug) {
    return store.values().stream().anyMatch(m -> m.slug().equals(slug));
  }

  @Override
  public PageResult<Municipality> findPage(int page, int size) {
    List<Municipality> sorted =
        store.values().stream().sorted(Comparator.comparing(Municipality::name)).toList();
    List<Municipality> items = sorted.stream().skip((long) page * size).limit(size).toList();
    return new PageResult<>(items, page, size, sorted.size());
  }

  @Override
  public Municipality insert(Municipality m) {
    if (existsBySlug(m.slug())) {
      throw new DomainException(MunicipalityError.SLUG_TAKEN, Map.of("slug", m.slug().value()));
    }
    Municipality saved = withVersion(m, 0);
    store.put(m.id(), saved);
    return saved;
  }

  @Override
  public Municipality update(Municipality m) {
    Municipality current = store.get(m.id());
    if (current == null || !Objects.equals(current.version(), m.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    Municipality saved = withVersion(m, m.version() + 1);
    store.put(m.id(), saved);
    return saved;
  }

  private static Municipality withVersion(Municipality m, long version) {
    return Municipality.rehydrate(
        m.id(),
        m.slug(),
        m.name(),
        m.languages(),
        m.defaultLanguage(),
        m.theme(),
        m.legal(),
        m.legalTexts(),
        m.sportsContact(),
        m.paymentNotes(),
        version,
        m.createdAt(),
        m.updatedAt());
  }
}
