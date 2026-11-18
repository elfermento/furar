// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.CatalogOpenMatchesMunicipalityResponse;
import gal.furar.booking.adapter.in.web.dto.CatalogOpenMatchesResponse;
import gal.furar.booking.application.port.in.ReadCatalogOpenMatchesUseCase;
import gal.furar.booking.application.port.in.ReadCatalogOpenMatchesUseCase.CatalogOpenMatches;
import gal.furar.shared.security.PublicEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * As partidas abertas da portada común (ADR-0040; ADR-0030), sen concello no URL nin sesión: as
 * mesmas que dá a lista de cada concello, por páxinas de concellos. Os buscadores non as indexan
 * ({@code X-Robots-Tag: noindex}).
 */
@RestController
@RequestMapping("/api/v1/catalog/open-matches")
@Tag(name = "catalog", description = "Portada común")
class CatalogOpenMatchesController {

  private final ReadCatalogOpenMatchesUseCase matches;

  CatalogOpenMatchesController(ReadCatalogOpenMatchesUseCase matches) {
    this.matches = matches;
  }

  @GetMapping
  @PublicEndpoint
  @Operation(
      operationId = "listCatalogOpenMatches",
      summary =
          "As partidas abertas con sitio de cada concello da portada común coa función acesa,"
              + " por páxinas de concellos coa orde do catálogo; só saen os concellos con algunha")
  ResponseEntity<CatalogOpenMatchesResponse> ofCatalog(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "" + ReadCatalogOpenMatchesUseCase.DEFAULT_PAGE_SIZE)
          @Min(1)
          @Max(50)
          int size) {
    CatalogOpenMatches result = matches.ofCatalog(page, size);
    return ResponseEntity.ok()
        .headers(OpenMatchWebMapper.noindex())
        .body(
            new CatalogOpenMatchesResponse(
                result.page(),
                result.size(),
                result.hasNext(),
                result.municipalities().stream()
                    .map(
                        m ->
                            new CatalogOpenMatchesMunicipalityResponse(
                                m.slug(),
                                m.name(),
                                m.items().stream().map(OpenMatchWebMapper::card).toList()))
                    .toList()));
  }
}
