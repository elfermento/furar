// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.service;

import gal.furar.municipality.application.port.out.MunicipalityRepository;
import gal.furar.municipality.domain.LegalDetails;
import gal.furar.municipality.domain.Municipality;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.tenancy.CatalogMunicipalities;
import gal.furar.shared.tenancy.TenantRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa {@link CatalogMunicipalities} para a portada común (ADR-0030): le unha páxina de
 * concellos por nome e queda cos que teñen os datos legais completos ({@link
 * LegalDetails#readyToOpen()}). A táboa {@code municipality} é da plataforma, sen concello.
 */
@Service
class CatalogMunicipalitiesService implements CatalogMunicipalities {

  private final MunicipalityRepository repository;

  CatalogMunicipalitiesService(MunicipalityRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public Page page(int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("page >= 0 and size in 1.." + MAX_PAGE_SIZE);
    }
    PageResult<Municipality> all = repository.findPage(page, size);
    return new Page(
        all.items().stream()
            .filter(m -> m.legal().readyToOpen())
            .map(
                m ->
                    new Listed(
                        new TenantRef(m.id().value(), m.slug().value()), m.name(), m.openData()))
            .toList(),
        (long) (page + 1) * size < all.totalItems());
  }
}
