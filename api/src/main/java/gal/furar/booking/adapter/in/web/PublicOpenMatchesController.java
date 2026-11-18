// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.OpenMatchCardResponse;
import gal.furar.booking.adapter.in.web.dto.OpenMatchResponse;
import gal.furar.booking.application.port.in.PublicOpenMatchesUseCase;
import gal.furar.shared.security.PublicEndpoint;
import gal.furar.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * As partidas abertas en público, sen sesión (ADR-0040, punto 9): na portada do concello, na ficha
 * da instalación e na súa páxina. Co nome de quen organiza e ningún outro. Os buscadores non as
 * indexan ({@code X-Robots-Tag: noindex}).
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/public/open-matches")
@Tag(name = "bookings", description = "Reservas")
class PublicOpenMatchesController {

  private final PublicOpenMatchesUseCase matches;

  PublicOpenMatchesController(PublicOpenMatchesUseCase matches) {
    this.matches = matches;
  }

  @GetMapping
  @PublicEndpoint
  @Operation(
      operationId = "listPublicOpenMatches",
      summary =
          "As partidas abertas con sitio, da que empeza antes; dunha instalación ou de todas. Coa"
              + " función apagada, ningunha")
  ResponseEntity<PageResponse<OpenMatchCardResponse>> list(
      @PathVariable String slug,
      @RequestParam(required = false) @Nullable UUID facilityId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ResponseEntity.ok()
        .headers(OpenMatchWebMapper.noindex())
        .body(PageResponse.from(matches.list(facilityId, page, size), OpenMatchWebMapper::card));
  }

  @GetMapping("/{id}")
  @PublicEndpoint
  @Operation(
      operationId = "getPublicOpenMatch",
      summary =
          "A páxina dunha partida aberta (tamén completa): pechada, cancelada, oculta, empezada"
              + " ou coa función apagada, 404 booking.match-not-found")
  ResponseEntity<OpenMatchResponse> get(@PathVariable String slug, @PathVariable UUID id) {
    return ResponseEntity.ok()
        .headers(OpenMatchWebMapper.noindex())
        .body(OpenMatchWebMapper.page(matches.find(id)));
  }
}
