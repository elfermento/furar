// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.in.CatalogAvailability;
import gal.furar.booking.application.port.in.FacilitiesAvailability;
import gal.furar.booking.application.port.in.ReadAvailabilityUseCase;
import gal.furar.booking.application.port.in.ReadCatalogAvailabilityUseCase;
import gal.furar.booking.domain.SlotGrid;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.tenancy.CatalogMunicipalities;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Ocos da portada común (ADR-0030): percorre os concellos cos datos legais completos e, en cada un,
 * calcula os seus ocos no seu propio concello e na súa propia transacción ({@link
 * TenantContext#runAs}), coa Row Level Security de sempre (ADR-0006). Por iso este método non abre
 * transacción. Un número fixo de lecturas por concello; os concellos van por páxinas.
 */
@Service
public class CatalogAvailabilityService implements ReadCatalogAvailabilityUseCase {

  private final CatalogMunicipalities municipalities;
  private final ReadAvailabilityUseCase availability;
  private final Clock clock;

  public CatalogAvailabilityService(
      CatalogMunicipalities municipalities, ReadAvailabilityUseCase availability, Clock clock) {
    this.municipalities = municipalities;
    this.availability = availability;
    this.clock = clock;
  }

  @Override
  @PublicUseCase
  public CatalogAvailability ofCatalog(
      @Nullable LocalDate date, int page, int size, Set<String> sports) {
    if (page < 0 || size < 1 || size > CatalogMunicipalities.MAX_PAGE_SIZE) {
      throw new DomainException(
          CommonError.INVALID_PARAMETER, Map.of("max", CatalogMunicipalities.MAX_PAGE_SIZE));
    }
    Instant now = clock.instant();
    LocalDate today = now.atZone(SlotGrid.ZONE).toLocalDate();
    LocalDate day = date == null ? today : date;
    CatalogMunicipalities.Page listed = municipalities.page(page, size);
    List<FacilitiesAvailability.Item> items = new ArrayList<>();
    for (CatalogMunicipalities.Listed municipality : listed.items()) {
      items.addAll(
          TenantContext.runAs(
                  municipality.tenant(), () -> availability.ofAllFacilities(day, sports))
              .items());
    }
    return new CatalogAvailability(day, today, now, page, size, listed.hasNext(), items);
  }
}
