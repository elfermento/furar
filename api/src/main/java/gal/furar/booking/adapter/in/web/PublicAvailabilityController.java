// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.AvailabilityResponse;
import gal.furar.booking.adapter.in.web.dto.FacilitiesAvailabilityResponse;
import gal.furar.booking.adapter.in.web.dto.FacilityFreeSlotsResponse;
import gal.furar.booking.adapter.in.web.dto.SlotStartResponse;
import gal.furar.booking.application.port.in.FacilitiesAvailability;
import gal.furar.booking.application.port.in.ReadAvailabilityUseCase;
import gal.furar.booking.application.port.in.UseAvailability;
import gal.furar.shared.security.PublicEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ocos libres para a veciñanza, sen sesión (plan do dominio 2, §2.2): os dun uso nun día (ficha e
 * páxina de reservar) e os dunha páxina de instalacións (portada e listado). Sen {@code date}, hoxe
 * en Europe/Madrid.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/public")
@Tag(name = "bookings", description = "Reservas")
class PublicAvailabilityController {

  /** Instalacións por páxina se non se pide outra cousa: unha grella da portada. */
  static final String DEFAULT_PAGE_SIZE = "12";

  private final ReadAvailabilityUseCase availability;

  PublicAvailabilityController(ReadAvailabilityUseCase availability) {
    this.availability = availability;
  }

  @GetMapping("/facilities/{id}/availability")
  @PublicEndpoint
  @Operation(
      operationId = "getUseAvailability",
      summary =
          "Ocos libres dun uso nun día: inicio real e no reloxo do concello, franxas seguidas"
              + " posibles e partes ou prazas libres; só os que a antelación permite; 404 se a"
              + " instalación non se ve ou o uso non é dela")
  AvailabilityResponse ofUse(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestParam UUID useId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate date) {
    UseAvailability result = availability.ofUse(id, useId, date);
    return new AvailabilityResponse(
        result.date(),
        result.today(),
        result.now(),
        result.bookable(),
        result.slots().stream().map(BookingWebMapper::slot).toList());
  }

  @GetMapping("/availability")
  @PublicEndpoint
  @Operation(
      operationId = "listFacilitiesAvailability",
      summary =
          "Ocos libres dun día por instalación que se reserva por internet, por páxinas en orde"
              + " alfabética: cantos hai e as tres primeiras horas")
  FacilitiesAvailabilityResponse ofPage(
      @PathVariable String slug,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate date,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(50) int size) {
    FacilitiesAvailability result = availability.ofPage(date, page, size);
    return new FacilitiesAvailabilityResponse(
        result.date(),
        result.today(),
        result.now(),
        result.page(),
        result.size(),
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
