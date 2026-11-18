// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.CatalogAvailabilityResponse;
import gal.furar.booking.adapter.in.web.dto.FacilityFreeSlotsResponse;
import gal.furar.booking.adapter.in.web.dto.SlotStartResponse;
import gal.furar.booking.application.port.in.CatalogAvailability;
import gal.furar.booking.application.port.in.ReadCatalogAvailabilityUseCase;
import gal.furar.shared.security.PublicEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ocos libres da portada común (ADR-0030), sen concello no URL nin sesión: os mesmos que dá o
 * listado de cada concello. Sen {@code date}, hoxe en Europe/Madrid.
 */
@RestController
@RequestMapping("/api/v1/catalog/availability")
@Tag(name = "catalog", description = "Portada común")
class CatalogAvailabilityController {

  private final ReadCatalogAvailabilityUseCase availability;

  CatalogAvailabilityController(ReadCatalogAvailabilityUseCase availability) {
    this.availability = availability;
  }

  @GetMapping
  @PublicEndpoint
  @Operation(
      operationId = "listCatalogAvailability",
      summary =
          "Ocos libres dun día por instalación que se reserva por internet, por páxinas de"
              + " concellos coa orde do catálogo de instalacións: cantos hai e as tres primeiras"
              + " horas")
  CatalogAvailabilityResponse ofCatalog(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate date,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "" + ReadCatalogAvailabilityUseCase.DEFAULT_PAGE_SIZE)
          @Min(1)
          @Max(50)
          int size,
      @Parameter(
              description =
                  "Claves de deporte (código do catálogo ou id do deporte do concello), repetibles:"
                      + " só contan os ocos dos usos que teñen algún deles")
          @RequestParam(name = "sport", required = false)
          @Nullable List<String> sports) {
    CatalogAvailability result =
        availability.ofCatalog(date, page, size, PublicAvailabilityController.sports(sports));
    return new CatalogAvailabilityResponse(
        result.date(),
        result.today(),
        result.now(),
        result.page(),
        result.size(),
        result.hasNext(),
        result.items().stream()
            .map(
                item ->
                    new FacilityFreeSlotsResponse(
                        item.facilityId(),
                        item.freeCount(),
                        item.firstStarts().stream()
                            .map(s -> new SlotStartResponse(s.start(), s.startMinute()))
                            .toList()))
            .toList());
  }
}
