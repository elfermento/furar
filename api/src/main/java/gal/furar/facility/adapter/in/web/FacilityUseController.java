// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web;

import gal.furar.facility.adapter.in.web.dto.AffectedBookingsProblemResponse;
import gal.furar.facility.adapter.in.web.dto.SaveUseRequest;
import gal.furar.facility.adapter.in.web.dto.UseResponse;
import gal.furar.facility.application.port.in.ArchiveUseCommand;
import gal.furar.facility.application.port.in.CreateUseCommand;
import gal.furar.facility.application.port.in.ManageFacilityUsesUseCase;
import gal.furar.facility.application.port.in.ReadFacilityUsesUseCase;
import gal.furar.facility.application.port.in.UpdateUseCommand;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityUse;
import gal.furar.facility.domain.UseId;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.VersionedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Usos dunha instalación (xestión, D1-12). Cada uso leva a súa versión ({@code ETag} e {@code
 * If-Match} do uso, ADR-0022 punto 7) e cada resposta devolve o uso enteiro. Un uso que pasa a
 * bloquear os demais pode volver 409 {@code facility.affected-bookings}: reenviar con {@code
 * decisions[]} (punto 6).
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/facilities/{facilityId}/uses")
@Tag(name = "facilities", description = "Instalacións")
class FacilityUseController {

  private final ManageFacilityUsesUseCase manage;
  private final ReadFacilityUsesUseCase read;

  FacilityUseController(ManageFacilityUsesUseCase manage, ReadFacilityUsesUseCase read) {
    this.manage = manage;
    this.read = read;
  }

  @GetMapping
  @Operation(
      operationId = "listFacilityUses",
      summary = "Usos vivos da instalación, por orde de alta")
  List<UseResponse> list(@PathVariable String slug, @PathVariable UUID facilityId) {
    return read.list(new FacilityId(facilityId)).stream().map(UseWebMapper::toResponse).toList();
  }

  @GetMapping("/{useId}")
  @Operation(operationId = "getFacilityUse", summary = "Un uso (tamén arquivado)")
  @VersionedResource
  ResponseEntity<UseResponse> get(
      @PathVariable String slug, @PathVariable UUID facilityId, @PathVariable UUID useId) {
    return withETag(ResponseEntity.ok(), read.get(new FacilityId(facilityId), new UseId(useId)));
  }

  @PostMapping
  @Operation(operationId = "createFacilityUse", summary = "Novo uso")
  @ApiResponse(responseCode = "201", description = "Creado", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "409",
      description = "Reservas afectadas sen decisión: reenviar con decisions[]",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = AffectedBookingsProblemResponse.class)))
  @VersionedResource
  ResponseEntity<UseResponse> create(
      @PathVariable String slug,
      @PathVariable UUID facilityId,
      @Valid @RequestBody SaveUseRequest request) {
    FacilityUse created =
        manage.create(
            new CreateUseCommand(
                new FacilityId(facilityId),
                UseWebMapper.spec(request),
                UseWebMapper.decisions(request)));
    URI location =
        URI.create(
            "/api/v1/municipalities/"
                + slug
                + "/facilities/"
                + facilityId
                + "/uses/"
                + created.id().value());
    return withETag(ResponseEntity.created(location), created);
  }

  @PutMapping("/{useId}")
  @Operation(
      operationId = "updateFacilityUse",
      summary = "Editar o uso enteiro (If-Match do uso); 409 use-has-bookings se toca reservas")
  @ApiResponse(responseCode = "200", description = "Gardado", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "409",
      description = "Reservas afectadas sen decisión: reenviar con decisions[]",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = AffectedBookingsProblemResponse.class)))
  @VersionedResource
  ResponseEntity<UseResponse> update(
      @PathVariable String slug,
      @PathVariable UUID facilityId,
      @PathVariable UUID useId,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody SaveUseRequest request) {
    FacilityUse updated =
        manage.update(
            new UpdateUseCommand(
                new FacilityId(facilityId),
                new UseId(useId),
                ETags.parseIfMatch(ifMatch),
                UseWebMapper.spec(request),
                UseWebMapper.decisions(request)));
    return withETag(ResponseEntity.ok(), updated);
  }

  @PostMapping("/{useId}/archive")
  @Operation(
      operationId = "archiveFacilityUse",
      summary = "Quitar o uso (arquívase; If-Match do uso); 409 use-has-bookings se ten reservas")
  @VersionedResource
  ResponseEntity<UseResponse> archive(
      @PathVariable String slug,
      @PathVariable UUID facilityId,
      @PathVariable UUID useId,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
    FacilityUse archived =
        manage.archive(
            new ArchiveUseCommand(
                new FacilityId(facilityId), new UseId(useId), ETags.parseIfMatch(ifMatch)));
    return withETag(ResponseEntity.ok(), archived);
  }

  private static ResponseEntity<UseResponse> withETag(
      ResponseEntity.BodyBuilder builder, FacilityUse use) {
    Long version = use.version();
    return builder.eTag(ETags.of(version == null ? 0 : version)).body(UseWebMapper.toResponse(use));
  }
}
