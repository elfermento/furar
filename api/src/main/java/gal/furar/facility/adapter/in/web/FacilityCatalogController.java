// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web;

import gal.furar.facility.adapter.in.web.dto.CatalogMunicipalityResponse;
import gal.furar.facility.adapter.in.web.dto.FacilityCatalogResponse;
import gal.furar.facility.application.port.in.FacilityCatalog;
import gal.furar.facility.application.port.in.ReadFacilityCatalogUseCase;
import gal.furar.shared.security.PublicEndpoint;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.web.Locales;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portada común (ADR-0030): as instalacións de todos os concellos, sen concello no URL nin sesión.
 * Só datos que cada concello xa publica no seu listado.
 */
@RestController
@RequestMapping("/api/v1/catalog/facilities")
@Tag(name = "catalog", description = "Portada común")
class FacilityCatalogController {

  private final ReadFacilityCatalogUseCase catalog;

  FacilityCatalogController(ReadFacilityCatalogUseCase catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  @PublicEndpoint
  @Operation(
      operationId = "listCatalogFacilities",
      summary =
          "Instalacións que ve a veciñanza en todos os concellos, por páxinas de concellos en orde"
              + " de nome: só saen os que teñen os datos legais completos e algunha instalación"
              + " activa á vista")
  FacilityCatalogResponse list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "" + ReadFacilityCatalogUseCase.DEFAULT_PAGE_SIZE)
          @Min(1)
          @Max(50)
          int size,
      HttpServletRequest request) {
    FacilityCatalog result = catalog.catalog(Locales.language(request), page, size);
    return new FacilityCatalogResponse(
        result.page(),
        result.size(),
        result.hasNext(),
        result.items().stream().map(FacilityCatalogController::entry).toList());
  }

  /** As rutas das imaxes saen do concello en curso: cada entrada mapéase dentro do seu. */
  private static CatalogMunicipalityResponse entry(FacilityCatalog.Entry entry) {
    String slug = Objects.requireNonNull(entry.municipality().slug(), "slug");
    return TenantContext.runAs(
        entry.municipality(),
        () ->
            new CatalogMunicipalityResponse(
                slug,
                entry.name(),
                entry.openData(),
                entry.facilities().stream()
                    .map(f -> PublicFacilityController.toResponse(slug, f))
                    .toList()));
  }
}
