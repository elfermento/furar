// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.in.OpenMatchView;
import gal.furar.booking.application.port.in.PublicOpenMatchesUseCase;
import gal.furar.booking.application.port.in.ReadCatalogOpenMatchesUseCase;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.tenancy.CatalogMunicipalities;
import gal.furar.shared.tenancy.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * As partidas abertas da portada común (ADR-0040): percorre os concellos cos datos legais completos
 * e, en cada un, le as súas partidas no seu propio concello e na súa propia transacción ({@link
 * TenantContext#runAs}), coa Row Level Security de sempre (ADR-0006). Por iso este método non abre
 * transacción. Os concellos coa función apagada ou sen partidas non saen.
 */
@Service
public class CatalogOpenMatchesService implements ReadCatalogOpenMatchesUseCase {

  private final CatalogMunicipalities municipalities;
  private final PublicOpenMatchesUseCase matches;

  public CatalogOpenMatchesService(
      CatalogMunicipalities municipalities, PublicOpenMatchesUseCase matches) {
    this.municipalities = municipalities;
    this.matches = matches;
  }

  @Override
  @PublicUseCase
  public CatalogOpenMatches ofCatalog(int page, int size) {
    if (page < 0 || size < 1 || size > CatalogMunicipalities.MAX_PAGE_SIZE) {
      throw new DomainException(
          CommonError.INVALID_PARAMETER, Map.of("max", CatalogMunicipalities.MAX_PAGE_SIZE));
    }
    CatalogMunicipalities.Page listed = municipalities.page(page, size);
    List<Municipality> found = new ArrayList<>();
    for (CatalogMunicipalities.Listed municipality : listed.items()) {
      List<OpenMatchView> items =
          TenantContext.runAs(
              municipality.tenant(), () -> matches.list(null, 0, MAX_PER_MUNICIPALITY).items());
      if (!items.isEmpty()) {
        found.add(
            new Municipality(
                Objects.requireNonNull(municipality.tenant().slug(), "slug"),
                municipality.name(),
                items));
      }
    }
    return new CatalogOpenMatches(page, size, listed.hasNext(), found);
  }
}
