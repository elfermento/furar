// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.CreateSeriesRequest;
import gal.furar.booking.adapter.in.web.dto.EndSeriesRequest;
import gal.furar.booking.adapter.in.web.dto.SeriesConflictsProblemResponse;
import gal.furar.booking.adapter.in.web.dto.SeriesPaymentRequest;
import gal.furar.booking.adapter.in.web.dto.SeriesPreviewResponse;
import gal.furar.booking.adapter.in.web.dto.SeriesResponse;
import gal.furar.booking.adapter.in.web.dto.SeriesScheduleRequest;
import gal.furar.booking.adapter.in.web.dto.SeriesStatusValue;
import gal.furar.booking.application.port.in.ManageSeriesUseCase;
import gal.furar.booking.application.port.in.SeriesCreated;
import gal.furar.booking.application.port.in.SeriesView;
import gal.furar.booking.domain.IdempotencyKey;
import gal.furar.booking.domain.season.Series;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.VersionedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
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
 * As tempadas das entidades (ADR-0039, puntos 8 e 11; permiso {@code booking.organizations}): vista
 * previa cos choques, crear as sesións libres, listas, rematar e o pago.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/booking-series")
@Tag(name = "bookings", description = "Reservas")
class BookingSeriesController {

  static final String CONFLICTS =
      "booking.series-conflicts-changed coa vista previa nova en preview (non se escribiu nada)";

  private final ManageSeriesUseCase series;

  BookingSeriesController(ManageSeriesUseCase series) {
    this.series = series;
  }

  @PostMapping("/preview")
  @Operation(
      operationId = "previewBookingSeries",
      summary =
          "Vista previa dunha tempada: cantas sesións se crean e que días non (festivos, uso non"
              + " vixente, pechados e os choques, cada un co que hai). Non escribe nin bloquea")
  SeriesPreviewResponse preview(
      @PathVariable String slug, @Valid @RequestBody SeriesScheduleRequest request) {
    return SeasonWebMapper.preview(series.preview(SeasonWebMapper.command(request)));
  }

  @PostMapping
  @Operation(
      operationId = "createBookingSeries",
      summary =
          "Crear a tempada e as súas sesións nos días libres, nunha transacción baixo o bloqueo da"
              + " instalación. skip son as datas con choque da vista previa: se mentres tanto"
              + " cambiaron, 409 coa vista previa nova. Idempotente coa cabeceira Idempotency-Key"
              + " (mesma clave e entrada: a mesma tempada, 200)")
  @ApiResponse(responseCode = "201", description = "Creada", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "200",
      description = "Xa creada antes con esta clave e esta entrada",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "409",
      description = CONFLICTS,
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = SeriesConflictsProblemResponse.class)))
  @VersionedResource
  ResponseEntity<SeriesResponse> create(
      @PathVariable String slug,
      @Parameter(
              name = IdempotencyKey.HEADER,
              in = ParameterIn.HEADER,
              required = true,
              description = "Clave opaca por intención de crear (un UUID)",
              schema =
                  @Schema(type = "string", minLength = 1, maxLength = IdempotencyKey.MAX_LENGTH))
          @RequestHeader(name = IdempotencyKey.HEADER, required = false)
          @Nullable String idempotencyKey,
      @Valid @RequestBody CreateSeriesRequest request) {
    IdempotencyKey key = IdempotencyKey.parse(idempotencyKey);
    return created(slug, series.create(SeasonWebMapper.command(request, key)));
  }

  /** 201 con Location se a creou agora; 200 se xa estaba. */
  static ResponseEntity<SeriesResponse> created(String slug, SeriesCreated created) {
    SeriesResponse body = SeasonWebMapper.series(created.view());
    if (!created.created()) {
      return ResponseEntity.ok().eTag(ETags.of(body.version())).body(body);
    }
    return ResponseEntity.created(
            URI.create("/api/v1/municipalities/" + slug + "/booking-series/" + body.id()))
        .eTag(ETags.of(body.version()))
        .body(body);
  }

  @GetMapping
  @Operation(
      operationId = "listBookingSeries",
      summary =
          "As tempadas activas (sen rematar e con días por diante) ou as rematadas, da primeira"
              + " data á última")
  List<SeriesResponse> list(
      @PathVariable String slug, @RequestParam(defaultValue = "ACTIVE") SeriesStatusValue status) {
    return series.list(status == SeriesStatusValue.ENDED).stream()
        .map(SeasonWebMapper::series)
        .toList();
  }

  @GetMapping("/{id}")
  @Operation(operationId = "getBookingSeries", summary = "Unha tempada")
  @VersionedResource
  ResponseEntity<SeriesResponse> get(@PathVariable String slug, @PathVariable UUID id) {
    return entity(series.get(id));
  }

  @PostMapping("/{id}/end")
  @Operation(
      operationId = "endBookingSeries",
      summary =
          "Rematar a tempada desde un día (de hoxe á última data): cancela as sesións que quedan"
              + " (as horas quedan libres) e manda un só correo á entidade. Repetilo non cancela"
              + " nada máis")
  @VersionedResource
  ResponseEntity<SeriesResponse> end(
      @PathVariable String slug,
      @PathVariable UUID id,
      @Valid @RequestBody EndSeriesRequest request) {
    return entity(series.end(id, request.from()));
  }

  @PutMapping("/{id}/payment")
  @Operation(
      operationId = "changeBookingSeriesPayment",
      summary = "O pago da tempada enteira (If-Match): gratis, pendente de pago ou pagada")
  @VersionedResource
  ResponseEntity<SeriesResponse> payment(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody SeriesPaymentRequest request) {
    return entity(
        series.payment(
            id,
            ETags.parseIfMatch(ifMatch),
            Series.PaymentStatus.valueOf(request.status().name())));
  }

  private static ResponseEntity<SeriesResponse> entity(SeriesView view) {
    SeriesResponse body = SeasonWebMapper.series(view);
    return ResponseEntity.ok().eTag(ETags.of(body.version())).body(body);
  }
}
