// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.application.port.in.FacilityCatalog;
import gal.furar.facility.application.port.in.ListPublicFacilitiesUseCase;
import gal.furar.facility.application.port.in.PublicFacility;
import gal.furar.facility.application.port.in.ReadFacilityCatalogUseCase;
import gal.furar.facility.domain.FacilityStatus;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.tenancy.CatalogMunicipalities;
import gal.furar.shared.tenancy.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Portada común (ADR-0030): percorre os concellos cos datos legais completos ({@link
 * CatalogMunicipalities}, que implementa {@code municipality}) e, en cada un, le o seu listado
 * público no seu propio concello e na súa propia transacción ({@link TenantContext#runAs}), coa Row
 * Level Security de sempre: non hai lectura que vexa dous concellos á vez (ADR-0006). Por iso este
 * método non abre transacción. Custa un número fixo de lecturas por concello, e os concellos van
 * por páxinas.
 */
@Service
public class FacilityCatalogService implements ReadFacilityCatalogUseCase {

  private final CatalogMunicipalities municipalities;
  private final ListPublicFacilitiesUseCase facilities;

  public FacilityCatalogService(
      CatalogMunicipalities municipalities, ListPublicFacilitiesUseCase facilities) {
    this.municipalities = municipalities;
    this.facilities = facilities;
  }

  @Override
  @PublicUseCase
  public FacilityCatalog catalog(Language language, int page, int size) {
    if (page < 0 || size < 1 || size > CatalogMunicipalities.MAX_PAGE_SIZE) {
      throw new DomainException(
          CommonError.INVALID_PARAMETER, Map.of("max", CatalogMunicipalities.MAX_PAGE_SIZE));
    }
    CatalogMunicipalities.Page listed = municipalities.page(page, size);
    List<FacilityCatalog.Entry> entries = new ArrayList<>();
    for (CatalogMunicipalities.Listed municipality : listed.items()) {
      List<PublicFacility> visible =
          TenantContext.runAs(municipality.tenant(), () -> facilities.listPublic(language));
      if (open(visible)) {
        entries.add(
            new FacilityCatalog.Entry(
                municipality.tenant(), municipality.name(), municipality.openData(), visible));
      }
    }
    return new FacilityCatalog(page, size, listed.hasNext(), entries);
  }

  /**
   * O segundo paso de «Para abrir as reservas»: algunha instalación activa que vexa a veciñanza. Só
   * con pechadas temporalmente, o concello aínda non sae.
   */
  static boolean open(List<PublicFacility> visible) {
    return visible.stream().anyMatch(f -> f.value().status() == FacilityStatus.ACTIVE);
  }
}
