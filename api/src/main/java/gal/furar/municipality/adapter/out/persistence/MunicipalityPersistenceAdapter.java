// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.out.persistence;

import gal.furar.municipality.application.port.out.MunicipalityRepository;
import gal.furar.municipality.domain.Municipality;
import gal.furar.municipality.domain.MunicipalityError;
import gal.furar.municipality.domain.MunicipalityId;
import gal.furar.municipality.domain.Slug;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.tenancy.TenancyError;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Adaptador JPA dos concellos. A táboa {@code municipality} está fóra do filtro de concello ({@code
 * TenancyAllowlist}), así que non herda de {@code TenantScopedPersistenceAdapter}.
 */
@Component
class MunicipalityPersistenceAdapter implements MunicipalityRepository {

  private final MunicipalityJpaRepository jpa;

  MunicipalityPersistenceAdapter(MunicipalityJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<Municipality> findById(MunicipalityId id) {
    return jpa.findById(id.value()).map(MunicipalityPersistenceMapper::toDomain);
  }

  @Override
  public boolean existsBySlug(Slug slug) {
    return jpa.existsBySlug(slug.value());
  }

  @Override
  public PageResult<Municipality> findPage(int page, int size) {
    Page<MunicipalityEntity> result =
        jpa.findAll(PageRequest.of(page, size, Sort.by("name", "slug")));
    return new PageResult<>(
        result.getContent().stream().map(MunicipalityPersistenceMapper::toDomain).toList(),
        page,
        size,
        result.getTotalElements());
  }

  @Override
  public Municipality insert(Municipality municipality) {
    try {
      return MunicipalityPersistenceMapper.toDomain(
          jpa.saveAndFlush(MunicipalityPersistenceMapper.newEntity(municipality)));
    } catch (DataIntegrityViolationException e) {
      throw new DomainException(
          MunicipalityError.SLUG_TAKEN, Map.of("slug", municipality.slug().value()));
    }
  }

  @Override
  public Municipality update(Municipality municipality) {
    MunicipalityEntity entity =
        jpa.findById(municipality.id().value())
            .orElseThrow(() -> new DomainException(TenancyError.MUNICIPALITY_NOT_FOUND));
    if (!Objects.equals(entity.version(), municipality.version())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    MunicipalityPersistenceMapper.apply(entity, municipality);
    return MunicipalityPersistenceMapper.toDomain(jpa.saveAndFlush(entity));
  }
}
