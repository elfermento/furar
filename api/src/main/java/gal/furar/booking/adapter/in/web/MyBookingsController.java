// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.ChangeMyBookingRequest;
import gal.furar.booking.adapter.in.web.dto.CreateMyBookingRequest;
import gal.furar.booking.adapter.in.web.dto.MyBookingResponse;
import gal.furar.booking.adapter.in.web.dto.MyBookingScopeValue;
import gal.furar.booking.adapter.in.web.dto.SlotTakenProblemResponse;
import gal.furar.booking.application.port.in.ChangeBookingCommand;
import gal.furar.booking.application.port.in.ChangeMyBookingUseCase;
import gal.furar.booking.application.port.in.MyBooking;
import gal.furar.booking.application.port.in.MyBookingsUseCase;
import gal.furar.booking.application.port.in.ReserveCommand;
import gal.furar.booking.application.port.in.ReserveUseCase;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.IdempotencyKey;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.PageResponse;
import gal.furar.shared.web.VersionedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * As reservas da persoa con sesión (plan do dominio 2, §2.2; ADR-0025, punto 3): reservar, ver,
 * cambiar e cancelar as súas. Sen rol no concello: só sesión e correo verificado; outra persoa →
 * 404.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/me/bookings")
@Tag(name = "bookings", description = "Reservas")
class MyBookingsController {

  private static final String PROBLEM = "#/components/schemas/Problem";

  static final MediaType CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

  private final ReserveUseCase reserve;
  private final MyBookingsUseCase mine;
  private final ChangeMyBookingUseCase changes;

  MyBookingsController(
      ReserveUseCase reserve, MyBookingsUseCase mine, ChangeMyBookingUseCase changes) {
    this.reserve = reserve;
    this.mine = mine;
    this.changes = changes;
  }

  @PostMapping
  @Operation(
      operationId = "createMyBooking",
      summary =
          "Reservar un oco: sen parte nun uso por partes, a primeira libre; prezo e prazo para"
              + " cancelar conxelados. Idempotente coa cabeceira Idempotency-Key: a mesma clave e"
              + " a mesma petición devolven a mesma reserva (201, mesmo Location, ETag da versión"
              + " de agora) sen crear outra durante polo menos 24 horas")
  @ApiResponse(
      responseCode = "201",
      description = "Reservada, ou xa reservada antes con esta clave e esta petición",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description =
          "booking.idempotency-key-missing (sen Idempotency-Key);"
              + " booking.idempotency-key-invalid (mal formada); validation.failed",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "422",
      description =
          "booking.idempotency-key-reused (a mesma clave cunha petición distinta); as normas"
              + " (booking.too-soon, booking.too-far, booking.too-long, booking.cap-reached…)",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "409",
      description =
          "booking.slot-taken coas alternativas; booking.part-taken; booking.seat-taken-by-you",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = SlotTakenProblemResponse.class)))
  @VersionedResource
  ResponseEntity<MyBookingResponse> create(
      @PathVariable String slug,
      @Parameter(
              name = IdempotencyKey.HEADER,
              in = ParameterIn.HEADER,
              required = true,
              description =
                  "Clave opaca por intención de reservar (un UUID): os reintentos da mesma"
                      + " intención mándana igual; unha intención nova, outra",
              schema =
                  @Schema(
                      type = "string",
                      minLength = 1,
                      maxLength = IdempotencyKey.MAX_LENGTH,
                      example = "8e03978e-40d5-43e8-bc93-6894a57f9324"))
          @RequestHeader(name = IdempotencyKey.HEADER, required = false)
          @Nullable String idempotencyKey,
      @Valid @RequestBody CreateMyBookingRequest request) {
    // Obrigatoria, pero lida aquí: así falta e mal formada teñen o seu código (ADR-0025, p. 19).
    IdempotencyKey key = IdempotencyKey.parse(idempotencyKey);
    MyBooking created =
        reserve.reserve(
            new ReserveCommand(
                request.facilityId(),
                request.useId(),
                request.start(),
                request.slots(),
                request.partId(),
                key,
                request.customFields()));
    URI location =
        URI.create(
            "/api/v1/municipalities/" + slug + "/me/bookings/" + created.booking().id().value());
    return ResponseEntity.created(location)
        .eTag(ETags.of(BookingWebMapper.version(created)))
        .body(BookingWebMapper.mine(created));
  }

  @GetMapping
  @Operation(
      operationId = "listMyBookings",
      summary =
          "As miñas reservas no concello: as próximas (da máis próxima) ou o historial"
              + " (realizadas e canceladas, da máis recente)")
  PageResponse<MyBookingResponse> list(
      @PathVariable String slug,
      @RequestParam(defaultValue = "UPCOMING") MyBookingScopeValue scope,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(
        mine.list(scope == MyBookingScopeValue.UPCOMING, page, size), BookingWebMapper::mine);
  }

  @GetMapping("/{id}")
  @Operation(operationId = "getMyBooking", summary = "Unha reserva miña (doutra persoa: 404)")
  @VersionedResource
  ResponseEntity<MyBookingResponse> get(@PathVariable String slug, @PathVariable UUID id) {
    MyBooking view = mine.get(new BookingId(id));
    return ResponseEntity.ok()
        .eTag(ETags.of(BookingWebMapper.version(view)))
        .body(BookingWebMapper.mine(view));
  }

  @PutMapping("/{id}/slot")
  @Operation(
      operationId = "changeMyBooking",
      summary =
          "Cambiar a reserva a outro oco da mesma instalación (If-Match): mesma reserva e código;"
              + " prezo, normas e prazo novos. Se falla, a reserva queda como estaba")
  @ApiResponse(responseCode = "200", description = "Cambiada", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "409",
      description =
          "booking.slot-taken coas alternativas; booking.part-taken; booking.seat-taken-by-you;"
              + " booking.not-cancellable",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = SlotTakenProblemResponse.class)))
  @VersionedResource
  ResponseEntity<MyBookingResponse> change(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody ChangeMyBookingRequest request) {
    MyBooking changed =
        changes.change(
            new ChangeBookingCommand(
                new BookingId(id),
                ETags.parseIfMatch(ifMatch),
                request.useId(),
                request.start(),
                request.slots(),
                request.partId()));
    return ResponseEntity.ok()
        .eTag(ETags.of(BookingWebMapper.version(changed)))
        .body(BookingWebMapper.mine(changed));
  }

  @PostMapping("/{id}/cancel")
  @Operation(
      operationId = "cancelMyBooking",
      summary =
          "Cancelar a reserva dentro do prazo (If-Match); se estaba pagada, queda «a devolver»")
  @ApiResponse(responseCode = "200", description = "Cancelada", useReturnTypeSchema = true)
  @VersionedResource
  ResponseEntity<MyBookingResponse> cancel(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
    MyBooking cancelled = changes.cancel(new BookingId(id), ETags.parseIfMatch(ifMatch));
    return ResponseEntity.ok()
        .eTag(ETags.of(BookingWebMapper.version(cancelled)))
        .body(BookingWebMapper.mine(cancelled));
  }

  @GetMapping(value = "/{id}/calendar.ics", produces = "text/calendar")
  @Operation(
      operationId = "getMyBookingCalendar",
      summary = "«Engadir ao calendario»: a reserva como evento iCalendar (RFC 5545)")
  @ApiResponse(
      responseCode = "200",
      description = "Evento",
      content =
          @Content(
              mediaType = "text/calendar",
              schema = @Schema(type = "string", format = "binary")))
  ResponseEntity<String> calendar(@PathVariable String slug, @PathVariable UUID id) {
    String ics = mine.calendar(new BookingId(id));
    return ResponseEntity.ok()
        .contentType(CALENDAR)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("reserva.ics").build().toString())
        .body(ics);
  }
}
