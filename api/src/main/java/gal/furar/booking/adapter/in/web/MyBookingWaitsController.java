// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.BookingWaitResponse;
import gal.furar.booking.adapter.in.web.dto.CreateBookingWaitRequest;
import gal.furar.booking.adapter.in.web.dto.SimilarSlotsResponse;
import gal.furar.booking.application.port.in.SimilarSlotsUseCase;
import gal.furar.booking.application.port.in.WaitUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A lista de espera da persoa con sesión (ADR-0033, punto 6): pedir que se lle avise se queda libre
 * unha hora ocupada, ver as súas esperas e deixar de esperar; e os ocos parecidos coas súas normas.
 * Sen rol no concello: só sesión e correo verificado; a espera doutra conta → 404.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/me")
@Tag(name = "bookings", description = "Reservas")
class MyBookingWaitsController {

  private static final String PROBLEM = "#/components/schemas/Problem";

  private final WaitUseCase waits;
  private final SimilarSlotsUseCase similar;

  MyBookingWaitsController(WaitUseCase waits, SimilarSlotsUseCase similar) {
    this.waits = waits;
    this.similar = similar;
  }

  @GetMapping("/booking-waits")
  @Operation(
      operationId = "listMyBookingWaits",
      summary = "As esperas activas da persoa neste concello, por inicio (como moito 3)")
  List<BookingWaitResponse> list(@PathVariable String slug) {
    return waits.mine().stream().map(BookingWebMapper::waiting).toList();
  }

  @PostMapping("/booking-waits")
  @Operation(
      operationId = "createMyBookingWait",
      summary =
          "Esperar por unha hora ocupada dun uso: avísase por correo, a todas as persoas á vez, se"
              + " queda libre; sen quenda nin reserva automática. Repetir non cambia nada")
  @ApiResponse(responseCode = "201", description = "Agora espera", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "200",
      description = "Xa esperaba por esa hora",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "403",
      description = "booking.staff-account; booking.blocked-no-show",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "409",
      description =
          "booking.wait-slot-free (a hora xa está libre); booking.wait-own-booking (xa ten reserva"
              + " dese uso nesa franxa)",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "422",
      description =
          "booking.wait-limit (3 esperas activas); booking.facility-not-bookable;"
              + " booking.use-not-available; booking.slot-invalid; booking.too-soon;"
              + " booking.too-far",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  ResponseEntity<BookingWaitResponse> create(
      @PathVariable String slug, @Valid @RequestBody CreateBookingWaitRequest request) {
    WaitUseCase.Joined joined = waits.join(request.facilityId(), request.useId(), request.start());
    return ResponseEntity.status(joined.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(BookingWebMapper.waiting(joined.entry()));
  }

  @DeleteMapping("/booking-waits/{id}")
  @Operation(
      operationId = "deleteMyBookingWait",
      summary = "Deixar de esperar; 404 booking.wait-not-found se xa non existe ou é doutra conta")
  @ApiResponse(responseCode = "204", description = "Xa non espera")
  ResponseEntity<Void> delete(@PathVariable String slug, @PathVariable UUID id) {
    waits.leave(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/facilities/{id}/similar-slots")
  @Operation(
      operationId = "listMySimilarSlots",
      summary =
          "Os ocos parecidos a unha hora ocupada, tamén coas normas da persoa (o límite por persoa"
              + " e a súa praza). Unha conta de traballo ou bloqueada por faltas recibe a lista"
              + " baleira")
  SimilarSlotsResponse similar(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestParam UUID useId,
      @RequestParam Instant start,
      @RequestParam(defaultValue = "1") @Min(1) @Max(16) int slots) {
    return BookingWebMapper.similar(similar.forMe(id, useId, start, slots));
  }
}
