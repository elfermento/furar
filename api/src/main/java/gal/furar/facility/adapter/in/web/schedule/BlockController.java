// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule;

import gal.furar.facility.adapter.in.web.dto.AffectedBookingsProblemResponse;
import gal.furar.facility.adapter.in.web.schedule.dto.BlockRequest;
import gal.furar.facility.adapter.in.web.schedule.dto.BlockResponse;
import gal.furar.facility.application.port.in.schedule.CreateBlockCommand;
import gal.furar.facility.application.port.in.schedule.DeleteBlockCommand;
import gal.furar.facility.application.port.in.schedule.ManageBlocksUseCase;
import gal.furar.facility.application.port.in.schedule.UpdateBlockCommand;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.Block;
import gal.furar.facility.domain.schedule.BlockId;
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
 * Bloqueos dunha instalación (xestión, D1-16). Cada bloqueo leva a súa versión ({@code ETag} e
 * {@code If-Match} do bloqueo, ADR-0022 punto 7) e cada resposta devolve o bloqueo enteiro. Un
 * bloqueo novo ou que bloquea máis horas pode volver 409 {@code facility.affected-bookings}:
 * reenviar con {@code decisions[]} (punto 6). Borrar non abre o modal.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/facilities/{facilityId}/blocks")
@Tag(name = "facilities", description = "Instalacións")
class BlockController {

  private final ManageBlocksUseCase manage;

  BlockController(ManageBlocksUseCase manage) {
    this.manage = manage;
  }

  @GetMapping
  @Operation(
      operationId = "listFacilityBlocks",
      summary = "Bloqueos da instalación, por orde de inicio")
  List<BlockResponse> list(@PathVariable String slug, @PathVariable UUID facilityId) {
    return manage.blocks(new FacilityId(facilityId)).stream()
        .map(ScheduleWebMapper::toResponse)
        .toList();
  }

  @GetMapping("/{blockId}")
  @Operation(operationId = "getFacilityBlock", summary = "Un bloqueo")
  @VersionedResource
  ResponseEntity<BlockResponse> get(
      @PathVariable String slug, @PathVariable UUID facilityId, @PathVariable UUID blockId) {
    return withETag(
        ResponseEntity.ok(), manage.block(new FacilityId(facilityId), new BlockId(blockId)));
  }

  @PostMapping
  @Operation(
      operationId = "createFacilityBlock",
      summary = "Novo bloqueo dun día; 409 block-overlap se pisa outro")
  @ApiResponse(responseCode = "201", description = "Creado", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "409",
      description =
          "Reservas afectadas sen decisión (reenviar con decisions[]) ou outro bloqueo nesas"
              + " horas (block-overlap)",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = AffectedBookingsProblemResponse.class)))
  @VersionedResource
  ResponseEntity<BlockResponse> create(
      @PathVariable String slug,
      @PathVariable UUID facilityId,
      @Valid @RequestBody BlockRequest request) {
    Block created =
        manage.create(
            new CreateBlockCommand(
                new FacilityId(facilityId),
                ScheduleWebMapper.spec(request),
                ScheduleWebMapper.decisions(request.decisions())));
    URI location =
        URI.create(
            "/api/v1/municipalities/"
                + slug
                + "/facilities/"
                + facilityId
                + "/blocks/"
                + created.id().value());
    return withETag(ResponseEntity.created(location), created);
  }

  @PutMapping("/{blockId}")
  @Operation(
      operationId = "updateFacilityBlock",
      summary = "Editar o bloqueo enteiro (If-Match do bloqueo)")
  @ApiResponse(responseCode = "200", description = "Gardado", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "409",
      description =
          "Reservas afectadas sen decisión (reenviar con decisions[]) ou outro bloqueo nesas"
              + " horas (block-overlap)",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = AffectedBookingsProblemResponse.class)))
  @VersionedResource
  ResponseEntity<BlockResponse> update(
      @PathVariable String slug,
      @PathVariable UUID facilityId,
      @PathVariable UUID blockId,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody BlockRequest request) {
    Block updated =
        manage.update(
            new UpdateBlockCommand(
                new FacilityId(facilityId),
                new BlockId(blockId),
                ETags.parseIfMatch(ifMatch),
                ScheduleWebMapper.spec(request),
                ScheduleWebMapper.decisions(request.decisions())));
    return withETag(ResponseEntity.ok(), updated);
  }

  @DeleteMapping("/{blockId}")
  @Operation(
      operationId = "deleteFacilityBlock",
      summary = "Borrar o bloqueo (If-Match do bloqueo)")
  @ApiResponse(responseCode = "204", description = "Borrado")
  @VersionedResource
  ResponseEntity<Void> delete(
      @PathVariable String slug,
      @PathVariable UUID facilityId,
      @PathVariable UUID blockId,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
    manage.delete(
        new DeleteBlockCommand(
            new FacilityId(facilityId), new BlockId(blockId), ETags.parseIfMatch(ifMatch)));
    return ResponseEntity.noContent().build();
  }

  private static ResponseEntity<BlockResponse> withETag(
      ResponseEntity.BodyBuilder builder, Block block) {
    Long version = block.version();
    return builder
        .eTag(ETags.of(version == null ? 0 : version))
        .body(ScheduleWebMapper.toResponse(block));
  }
}
