// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule;

import gal.furar.facility.adapter.in.web.dto.AffectedBookingsProblemResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.CreateSeasonRequest;
import gal.furar.facility.adapter.in.web.schedule.dto.DeleteSeasonRequest;
import gal.furar.facility.adapter.in.web.schedule.dto.ScheduleResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.SeasonResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.UpdateSeasonRequest;
import gal.furar.facility.application.port.in.schedule.CreateSeasonCommand;
import gal.furar.facility.application.port.in.schedule.DeleteSeasonCommand;
import gal.furar.facility.application.port.in.schedule.ManageSeasonsUseCase;
import gal.furar.facility.application.port.in.schedule.ReadScheduleUseCase;
import gal.furar.facility.application.port.in.schedule.UpdateSeasonCommand;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.Season;
import gal.furar.facility.domain.schedule.SeasonId;
import gal.furar.facility.domain.schedule.SeasonSpec;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Horario dunha instalación (xestión, D1-15a): tempadas coa súa semana. Cada tempada leva a súa
 * versión ({@code ETag} e {@code If-Match} da tempada, ADR-0022 punto 7) e cada resposta devolve a
 * tempada enteira. Editar ou borrar unha tempada que deixa reservas fóra do horario pode volver 409
 * {@code facility.affected-bookings}: reenviar con {@code decisions[]} (punto 6; P6).
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/facilities/{facilityId}")
@Tag(name = "facilities", description = "Instalacións")
class SeasonController {

  private final ManageSeasonsUseCase manage;
  private final ReadScheduleUseCase read;

  SeasonController(ManageSeasonsUseCase manage, ReadScheduleUseCase read) {
    this.manage = manage;
    this.read = read;
  }

  @GetMapping("/schedule")
  @Operation(
      operationId = "getFacilitySchedule",
      summary =
          "Horario: tempadas coa semana, a vixente hoxe, excepcións, hoxe e agora, días sen tempada e"
              + " os festivos de Galicia do ano (por defecto, o de hoxe)")
  ScheduleResponse schedule(
      @PathVariable String slug,
      @PathVariable UUID facilityId,
      @RequestParam(required = false) @Nullable Integer year) {
    return ScheduleWebMapper.toResponse(read.schedule(new FacilityId(facilityId), year));
  }

  @GetMapping("/seasons/{seasonId}")
  @Operation(operationId = "getFacilitySeason", summary = "Unha tempada coa súa semana")
  @VersionedResource
  ResponseEntity<SeasonResponse> get(
      @PathVariable String slug, @PathVariable UUID facilityId, @PathVariable UUID seasonId) {
    return withETag(
        ResponseEntity.ok(), read.season(new FacilityId(facilityId), new SeasonId(seasonId)));
  }

  @PostMapping("/seasons")
  @Operation(
      operationId = "createFacilitySeason",
      summary = "Nova tempada (baleira ou copiando a semana doutra); 409 season-overlap se choca")
  @ApiResponse(responseCode = "201", description = "Creada", useReturnTypeSchema = true)
  @VersionedResource
  ResponseEntity<SeasonResponse> create(
      @PathVariable String slug,
      @PathVariable UUID facilityId,
      @Valid @RequestBody CreateSeasonRequest request) {
    UUID copyFrom = request.copyFrom();
    Season created =
        manage.create(
            new CreateSeasonCommand(
                new FacilityId(facilityId),
                new SeasonSpec(request.name(), request.startDate(), request.endDate()),
                copyFrom == null ? null : new SeasonId(copyFrom)));
    URI location =
        URI.create(
            "/api/v1/municipalities/"
                + slug
                + "/facilities/"
                + facilityId
                + "/seasons/"
                + created.id().value());
    return withETag(ResponseEntity.created(location), created);
  }

  @PutMapping("/seasons/{seasonId}")
  @Operation(
      operationId = "updateFacilitySeason",
      summary =
          "Editar a tempada enteira e substituír a súa semana (If-Match da tempada); 409"
              + " season-overlap se choca")
  @ApiResponse(responseCode = "200", description = "Gardada", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "409",
      description = "Reservas afectadas sen decisión: reenviar con decisions[]",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = AffectedBookingsProblemResponse.class)))
  @VersionedResource
  ResponseEntity<SeasonResponse> update(
      @PathVariable String slug,
      @PathVariable UUID facilityId,
      @PathVariable UUID seasonId,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody UpdateSeasonRequest request) {
    Season updated =
        manage.update(
            new UpdateSeasonCommand(
                new FacilityId(facilityId),
                new SeasonId(seasonId),
                ETags.parseIfMatch(ifMatch),
                new SeasonSpec(request.name(), request.startDate(), request.endDate()),
                ScheduleWebMapper.week(request.week()),
                ScheduleWebMapper.decisions(request.decisions())));
    return withETag(ResponseEntity.ok(), updated);
  }

  @DeleteMapping("/seasons/{seasonId}")
  @Operation(
      operationId = "deleteFacilitySeason",
      summary = "Borrar a tempada (If-Match da tempada); corpo con decisions[] só tras un 409")
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
      @PathVariable UUID seasonId,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody(required = false) @Nullable DeleteSeasonRequest request) {
    manage.delete(
        new DeleteSeasonCommand(
            new FacilityId(facilityId),
            new SeasonId(seasonId),
            ETags.parseIfMatch(ifMatch),
            ScheduleWebMapper.decisions(request == null ? null : request.decisions())));
    return ResponseEntity.noContent().build();
  }

  private static ResponseEntity<SeasonResponse> withETag(
      ResponseEntity.BodyBuilder builder, Season season) {
    Long version = season.version();
    return builder
        .eTag(ETags.of(version == null ? 0 : version))
        .body(ScheduleWebMapper.toResponse(season));
  }
}
