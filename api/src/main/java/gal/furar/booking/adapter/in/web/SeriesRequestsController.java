// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.CreateSeriesRequest;
import gal.furar.booking.adapter.in.web.dto.PendingCountResponse;
import gal.furar.booking.adapter.in.web.dto.RejectSeriesRequestRequest;
import gal.furar.booking.adapter.in.web.dto.SeriesConflictsProblemResponse;
import gal.furar.booking.adapter.in.web.dto.SeriesRequestCardResponse;
import gal.furar.booking.adapter.in.web.dto.SeriesRequestStatusValue;
import gal.furar.booking.adapter.in.web.dto.SeriesResponse;
import gal.furar.booking.application.port.in.ManageSeriesRequestsUseCase;
import gal.furar.booking.domain.IdempotencyKey;
import gal.furar.booking.domain.season.SeriesRequest;
import gal.furar.shared.web.VersionedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * As solicitudes de tempada das entidades (ADR-0039, punto 11; permiso {@code
 * booking.organizations}): ver, contar as pendentes, aprobar (creando a tempada) e rexeitar.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/booking-series-requests")
@Tag(name = "bookings", description = "Reservas")
class SeriesRequestsController {

  private final ManageSeriesRequestsUseCase requests;

  SeriesRequestsController(ManageSeriesRequestsUseCase requests) {
    this.requests = requests;
  }

  @GetMapping
  @Operation(
      operationId = "listSeriesRequests",
      summary = "As solicitudes de tempada dese estado (por defecto, as pendentes), da máis vella")
  List<SeriesRequestCardResponse> list(
      @PathVariable String slug,
      @RequestParam(defaultValue = "PENDING") SeriesRequestStatusValue status) {
    return requests.list(SeriesRequest.Status.valueOf(status.name())).stream()
        .map(SeasonWebMapper::card)
        .toList();
  }

  @GetMapping("/pending-count")
  @Operation(
      operationId = "countPendingSeriesRequests",
      summary = "Cantas solicitudes de tempada hai pendentes (o número da pestana «Entidades»)")
  PendingCountResponse pendingCount(@PathVariable String slug) {
    return new PendingCountResponse(requests.pendingCount());
  }

  @GetMapping("/{id}")
  @Operation(
      operationId = "getSeriesRequest",
      summary = "Unha solicitude de tempada, coa nota e o correo de quen a pediu")
  SeriesRequestCardResponse get(@PathVariable String slug, @PathVariable UUID id) {
    return SeasonWebMapper.card(requests.get(id));
  }

  @PostMapping("/{id}/approve")
  @Operation(
      operationId = "approveSeriesRequest",
      summary =
          "Aprobar a solicitude creando a tempada cos datos (axustables) do xestor: coma crear"
              + " unha tempada. Xa decidida, 409 booking.series-request-decided")
  @ApiResponse(responseCode = "201", description = "Aprobada e creada", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "200",
      description = "Xa creada antes con esta clave e esta entrada",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "409",
      description = BookingSeriesController.CONFLICTS + "; booking.series-request-decided",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = SeriesConflictsProblemResponse.class)))
  @VersionedResource
  ResponseEntity<SeriesResponse> approve(
      @PathVariable String slug,
      @PathVariable UUID id,
      @Parameter(
              name = IdempotencyKey.HEADER,
              in = ParameterIn.HEADER,
              required = true,
              description = "Clave opaca por intención de aprobar (un UUID)",
              schema =
                  @Schema(type = "string", minLength = 1, maxLength = IdempotencyKey.MAX_LENGTH))
          @RequestHeader(name = IdempotencyKey.HEADER, required = false)
          @Nullable String idempotencyKey,
      @Valid @RequestBody CreateSeriesRequest request) {
    IdempotencyKey key = IdempotencyKey.parse(idempotencyKey);
    return BookingSeriesController.created(
        slug, requests.approve(id, SeasonWebMapper.command(request, key)));
  }

  @PostMapping("/{id}/reject")
  @Operation(
      operationId = "rejectSeriesRequest",
      summary = "Rexeitar a solicitude cunha mensaxe, que lle chega á entidade por correo")
  SeriesRequestCardResponse reject(
      @PathVariable String slug,
      @PathVariable UUID id,
      @Valid @RequestBody RejectSeriesRequestRequest request) {
    return SeasonWebMapper.card(requests.reject(id, request.message()));
  }
}
