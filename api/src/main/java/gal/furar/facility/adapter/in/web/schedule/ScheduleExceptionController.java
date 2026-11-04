// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule;

import gal.furar.facility.adapter.in.web.dto.AffectedBookingsProblemResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.DeleteScheduleExceptionRequest;
import gal.furar.facility.adapter.in.web.schedule.dto.ScheduleExceptionRequest;
import gal.furar.facility.adapter.in.web.schedule.dto.ScheduleExceptionResponse;
import gal.furar.facility.application.port.in.schedule.CreateExceptionCommand;
import gal.furar.facility.application.port.in.schedule.DeleteExceptionCommand;
import gal.furar.facility.application.port.in.schedule.ManageExceptionsUseCase;
import gal.furar.facility.application.port.in.schedule.UpdateExceptionCommand;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.ScheduleException;
import gal.furar.facility.domain.schedule.ScheduleExceptionId;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.VersionedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Excepcións do horario dunha instalación (xestión, D1-15b). Cada excepción leva a súa versión
 * ({@code ETag} e {@code If-Match} da excepción, ADR-0022 punto 7) e cada resposta devolve a
 * excepción enteira; a lista vai en {@code GET …/schedule}. Unha excepción que quita horas pode
 * volver 409 {@code facility.affected-bookings}: reenviar con {@code decisions[]} (punto 6; P6).
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/facilities/{facilityId}/schedule-exceptions")
@Tag(name = "facilities", description = "Instalacións")
class ScheduleExceptionController {

  private final ManageExceptionsUseCase manage;

  ScheduleExceptionController(ManageExceptionsUseCase manage) {
    this.manage = manage;
  }

  @GetMapping("/{exceptionId}")
  @Operation(operationId = "getFacilityScheduleException", summary = "Unha excepción do horario")
  @VersionedResource
  ResponseEntity<ScheduleExceptionResponse> get(
      @PathVariable String slug, @PathVariable UUID facilityId, @PathVariable UUID exceptionId) {
    return withETag(
        ResponseEntity.ok(),
        manage.exception(new FacilityId(facilityId), new ScheduleExceptionId(exceptionId)));
  }

  @PostMapping
  @Operation(
      operationId = "createFacilityScheduleException",
      summary = "Nova excepción dun día; 409 exception-date-taken se o día xa ten unha")
  @ApiResponse(responseCode = "201", description = "Creada", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "409",
      description =
          "Reservas afectadas sen decisión (reenviar con decisions[]) ou data xa usada"
              + " (exception-date-taken)",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = AffectedBookingsProblemResponse.class)))
  @VersionedResource
  ResponseEntity<ScheduleExceptionResponse> create(
      @PathVariable String slug,
      @PathVariable UUID facilityId,
      @Valid @RequestBody ScheduleExceptionRequest request) {
    ScheduleException created =
        manage.create(
            new CreateExceptionCommand(
                new FacilityId(facilityId),
                ScheduleWebMapper.spec(request),
                ScheduleWebMapper.decisions(request.decisions())));
    URI location =
        URI.create(
            "/api/v1/municipalities/"
                + slug
                + "/facilities/"
                + facilityId
                + "/schedule-exceptions/"
                + created.id().value());
    return withETag(ResponseEntity.created(location), created);
  }

  @PutMapping("/{exceptionId}")
  @Operation(
      operationId = "updateFacilityScheduleException",
      summary = "Editar a excepción enteira (If-Match da excepción)")
  @ApiResponse(responseCode = "200", description = "Gardada", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "409",
      description =
          "Reservas afectadas sen decisión (reenviar con decisions[]) ou data xa usada"
              + " (exception-date-taken)",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = AffectedBookingsProblemResponse.class)))
  @VersionedResource
  ResponseEntity<ScheduleExceptionResponse> update(
      @PathVariable String slug,
      @PathVariable UUID facilityId,
      @PathVariable UUID exceptionId,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody ScheduleExceptionRequest request) {
    ScheduleException updated =
        manage.update(
            new UpdateExceptionCommand(
                new FacilityId(facilityId),
                new ScheduleExceptionId(exceptionId),
                ETags.parseIfMatch(ifMatch),
                ScheduleWebMapper.spec(request),
                ScheduleWebMapper.decisions(request.decisions())));
    return withETag(ResponseEntity.ok(), updated);
  }

  @DeleteMapping("/{exceptionId}")
  @Operation(
      operationId = "deleteFacilityScheduleException",
      summary = "Borrar a excepción (If-Match da excepción); corpo con decisions[] só tras un 409")
  @ApiResponse(responseCode = "204", description = "Borrada")
  @ApiResponse(
      responseCode = "409",
      description = "Reservas afectadas sen decisión: reenviar con decisions[]",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = AffectedBookingsProblemResponse.class)))
  @VersionedResource
  ResponseEntity<Void> delete(
      @PathVariable String slug,
      @PathVariable UUID facilityId,
      @PathVariable UUID exceptionId,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody(required = false) @Nullable DeleteScheduleExceptionRequest request) {
    manage.delete(
        new DeleteExceptionCommand(
            new FacilityId(facilityId),
            new ScheduleExceptionId(exceptionId),
            ETags.parseIfMatch(ifMatch),
            ScheduleWebMapper.decisions(request == null ? null : request.decisions())));
    return ResponseEntity.noContent().build();
  }

  private static ResponseEntity<ScheduleExceptionResponse> withETag(
      ResponseEntity.BodyBuilder builder, ScheduleException exception) {
    Long version = exception.version();
    return builder
        .eTag(ETags.of(version == null ? 0 : version))
        .body(ScheduleWebMapper.toResponse(exception));
  }
}
