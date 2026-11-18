// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.AvailabilityResponse;
import gal.furar.booking.adapter.in.web.dto.BusySlotResponse;
import gal.furar.booking.adapter.in.web.dto.FacilitiesAvailabilityResponse;
import gal.furar.booking.adapter.in.web.dto.FacilityFreeSlotsResponse;
import gal.furar.booking.adapter.in.web.dto.SimilarSlotsResponse;
import gal.furar.booking.adapter.in.web.dto.SlotStartResponse;
import gal.furar.booking.application.port.in.FacilitiesAvailability;
import gal.furar.booking.application.port.in.ReadAvailabilityUseCase;
import gal.furar.booking.application.port.in.SimilarSlotsUseCase;
import gal.furar.booking.application.port.in.UseAvailability;
import gal.furar.shared.security.PublicEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ocos libres para a veciñanza, sen sesión: os dun uso nun día (ficha e páxina de reservar, coas
 * horas ocupadas: ADR-0033) e os dunha páxina de instalacións (portada e listado); e os ocos
 * parecidos a unha hora ocupada. Sen {@code date}, hoxe en Europe/Madrid.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/public")
@Tag(name = "bookings", description = "Reservas")
class PublicAvailabilityController {

  /** Instalacións por páxina se non se pide outra cousa: unha grella da portada. */
  static final String DEFAULT_PAGE_SIZE = "12";

  private final ReadAvailabilityUseCase availability;
  private final SimilarSlotsUseCase similar;

  PublicAvailabilityController(ReadAvailabilityUseCase availability, SimilarSlotsUseCase similar) {
    this.availability = availability;
    this.similar = similar;
  }

  @GetMapping("/facilities/{id}/availability")
  @PublicEndpoint
  @Operation(
      operationId = "getUseAvailability",
      summary =
          "Ocos libres dun uso nun día: inicio real e no reloxo do concello, franxas seguidas"
              + " posibles e partes ou prazas libres; só os que a antelación permite; se se reserva"
              + " por internet, tamén as horas ocupadas (busy), sen ningún dato de quen as ocupa;"
              + " 404 se a instalación non se ve ou o uso non é dela")
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
        result.slots().stream().map(BookingWebMapper::slot).toList(),
        result.busy().stream().map(b -> new BusySlotResponse(b.start(), b.startMinute())).toList());
  }

  @GetMapping("/facilities/{id}/similar-slots")
  @PublicEndpoint
  @Operation(
      operationId = "listSimilarSlots",
      summary =
          "Ocos parecidos a unha hora ocupada dun uso, por regras e como moito un por regra: a"
              + " franxa anterior ou a seguinte nesta instalación, outra instalación co mesmo uso á mesma hora, máis"
              + " tarde ese día e a mesma hora a semana seguinte. Só ocos que se poden reservar"
              + " coas normas do uso; baleira se a hora xa pasou, está libre ou non hai. 404 se a"
              + " instalación non se ve ou o uso non é dela; 422 booking.slot-invalid se a hora non"
              + " é da grella do uso")
  SimilarSlotsResponse similar(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestParam UUID useId,
      @RequestParam Instant start,
      @RequestParam(defaultValue = "1") @Min(1) @Max(16) int slots) {
    return BookingWebMapper.similar(similar.forAnyone(id, useId, start, slots));
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
      @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(50) int size,
      @Parameter(
              description =
                  "Claves de deporte (código do catálogo ou id do deporte do concello), repetibles:"
                      + " só contan os ocos dos usos que teñen algún deles")
          @RequestParam(name = "sport", required = false)
          @Nullable List<String> sports) {
    FacilitiesAvailability result = availability.ofPage(date, page, size, sports(sports));
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

  /** Os deportes do filtro ({@code ?sport=a&sport=b}); sen el, ningún. */
  static Set<String> sports(@Nullable List<String> sports) {
    return sports == null ? Set.of() : Set.copyOf(sports);
  }
}
