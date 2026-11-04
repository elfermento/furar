// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web;

import gal.furar.facility.adapter.in.web.dto.AddressRequest;
import gal.furar.facility.adapter.in.web.dto.AffectedBookingsProblemResponse;
import gal.furar.facility.adapter.in.web.dto.ChangeFacilityStatusRequest;
import gal.furar.facility.adapter.in.web.dto.CreateFacilityRequest;
import gal.furar.facility.adapter.in.web.dto.FacilityNoteRequest;
import gal.furar.facility.adapter.in.web.dto.FacilityResponse;
import gal.furar.facility.adapter.in.web.dto.FacilitySummaryResponse;
import gal.furar.facility.adapter.in.web.dto.RenameFacilityRequest;
import gal.furar.facility.adapter.in.web.dto.UpdateFacilityAmenitiesRequest;
import gal.furar.facility.adapter.in.web.dto.UpdateFacilityDescriptionRequest;
import gal.furar.facility.adapter.in.web.dto.UpdateFacilityDetailsRequest;
import gal.furar.facility.adapter.in.web.dto.UpdateFacilityPaymentNotesRequest;
import gal.furar.facility.adapter.in.web.dto.UpdateHouseRulesRequest;
import gal.furar.facility.application.port.in.ChangeFacilityStatusUseCase;
import gal.furar.facility.application.port.in.CreateFacilityUseCase;
import gal.furar.facility.application.port.in.DeleteFacilityCommand;
import gal.furar.facility.application.port.in.DeleteFacilityUseCase;
import gal.furar.facility.application.port.in.DescriptionInput;
import gal.furar.facility.application.port.in.FacilityView;
import gal.furar.facility.application.port.in.GetFacilityUseCase;
import gal.furar.facility.application.port.in.ListFacilitiesUseCase;
import gal.furar.facility.application.port.in.PublishFacilityCommand;
import gal.furar.facility.application.port.in.PublishFacilityUseCase;
import gal.furar.facility.application.port.in.RenameFacilityUseCase;
import gal.furar.facility.application.port.in.UpdateFacilityPaymentNotesCommand;
import gal.furar.facility.application.port.in.UpdateFacilitySectionUseCase;
import gal.furar.facility.application.port.in.UpdateHouseRulesCommand;
import gal.furar.facility.domain.FacilityId;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.Locales;
import gal.furar.shared.web.PageResponse;
import gal.furar.shared.web.VersionedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * Instalacións (xestión). Controlador fino: DTO ↔ comando e caso de uso; o permiso vive no caso de
 * uso. Gardar por sección: un endpoint por sección con {@code If-Match}, e cada resposta devolve a
 * ficha enteira co {@code ETag} novo (ADR-0022, punto 7). A lectura pública chega no dominio 2.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/facilities")
@Tag(name = "facilities", description = "Instalacións")
class FacilityController {

  private final CreateFacilityUseCase create;
  private final GetFacilityUseCase get;
  private final ListFacilitiesUseCase list;
  private final RenameFacilityUseCase rename;
  private final ChangeFacilityStatusUseCase status;
  private final UpdateFacilitySectionUseCase sections;
  private final DeleteFacilityUseCase delete;
  private final PublishFacilityUseCase publish;

  FacilityController(
      CreateFacilityUseCase create,
      GetFacilityUseCase get,
      ListFacilitiesUseCase list,
      RenameFacilityUseCase rename,
      ChangeFacilityStatusUseCase status,
      UpdateFacilitySectionUseCase sections,
      DeleteFacilityUseCase delete,
      PublishFacilityUseCase publish) {
    this.create = create;
    this.get = get;
    this.list = list;
    this.rename = rename;
    this.status = status;
    this.sections = sections;
    this.delete = delete;
    this.publish = publish;
  }

  @GetMapping
  @Operation(
      operationId = "listFacilities",
      summary = "Listado de xestión, en orde alfabética segundo Accept-Language")
  PageResponse<FacilitySummaryResponse> list(
      @PathVariable String slug,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      HttpServletRequest request) {
    return PageResponse.from(
        list.list(page, size, Locales.language(request)), FacilityWebMapper::toSummary);
  }

  @PostMapping
  @Operation(
      operationId = "createFacility",
      summary = "Nova instalación sen publicar: o nome e, se xa o hai, o enderezo")
  @ApiResponse(responseCode = "201", description = "Creada", useReturnTypeSchema = true)
  @VersionedResource
  ResponseEntity<FacilityResponse> create(
      @PathVariable String slug, @Valid @RequestBody CreateFacilityRequest request) {
    FacilityView created = create.create(FacilityWebMapper.toCommand(request));
    URI location =
        URI.create(
            "/api/v1/municipalities/" + slug + "/facilities/" + created.value().id().value());
    return withETag(ResponseEntity.created(location), created);
  }

  @GetMapping("/{id}")
  @Operation(operationId = "getFacility", summary = "Ficha de xestión")
  @VersionedResource
  ResponseEntity<FacilityResponse> get(@PathVariable String slug, @PathVariable UUID id) {
    return withETag(ResponseEntity.ok(), get.get(new FacilityId(id)));
  }

  @PutMapping("/{id}/name")
  @Operation(operationId = "renameFacility", summary = "Sección nome (If-Match)")
  @VersionedResource
  ResponseEntity<FacilityResponse> rename(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody RenameFacilityRequest request) {
    FacilityView updated =
        rename.rename(
            FacilityWebMapper.toCommand(new FacilityId(id), ETags.parseIfMatch(ifMatch), request));
    return withETag(ResponseEntity.ok(), updated);
  }

  @PutMapping("/{id}/details")
  @Operation(
      operationId = "updateFacilityDetails",
      summary = "Sección Datos (If-Match): deportes, tipo, RIDEGA, contacto, reserva e cuberta")
  @VersionedResource
  ResponseEntity<FacilityResponse> updateDetails(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody UpdateFacilityDetailsRequest request) {
    FacilityView updated =
        sections.updateDetails(
            FacilityWebMapper.toCommand(new FacilityId(id), ETags.parseIfMatch(ifMatch), request));
    return withETag(ResponseEntity.ok(), updated);
  }

  @PutMapping("/{id}/description")
  @Operation(
      operationId = "updateFacilityDescription",
      summary = "Sección Descrición (If-Match): gl e es, ou as dúas baleiras para quitala")
  @VersionedResource
  ResponseEntity<FacilityResponse> updateDescription(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody UpdateFacilityDescriptionRequest request) {
    FacilityView updated =
        sections.updateDescription(
            FacilityWebMapper.toCommand(new FacilityId(id), ETags.parseIfMatch(ifMatch), request));
    return withETag(ResponseEntity.ok(), updated);
  }

  @PutMapping("/{id}/house-rules")
  @Operation(
      operationId = "updateFacilityHouseRules",
      summary = "Normas da instalación (If-Match): gl e es, ou as dúas baleiras para quitalas")
  @VersionedResource
  ResponseEntity<FacilityResponse> updateHouseRules(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody UpdateHouseRulesRequest request) {
    FacilityView updated =
        sections.updateHouseRules(
            new UpdateHouseRulesCommand(
                new FacilityId(id),
                ETags.parseIfMatch(ifMatch),
                new DescriptionInput(request.gl(), request.es())));
    return withETag(ResponseEntity.ok(), updated);
  }

  @PutMapping("/{id}/payment-notes")
  @Operation(
      operationId = "updateFacilityPaymentNotes",
      summary =
          "Pago e devolución desta instalación (If-Match): cada nota en gl e es, ou as dúas"
              + " baleiras para quitala")
  @VersionedResource
  ResponseEntity<FacilityResponse> updatePaymentNotes(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody UpdateFacilityPaymentNotesRequest request) {
    FacilityView updated =
        sections.updatePaymentNotes(
            new UpdateFacilityPaymentNotesCommand(
                new FacilityId(id),
                ETags.parseIfMatch(ifMatch),
                input(request.payment()),
                input(request.refund())));
    return withETag(ResponseEntity.ok(), updated);
  }

  private static DescriptionInput input(@Nullable FacilityNoteRequest note) {
    return note == null
        ? new DescriptionInput(null, null)
        : new DescriptionInput(note.gl(), note.es());
  }

  @PutMapping("/{id}/address")
  @Operation(
      operationId = "updateFacilityAddress",
      summary = "Sección Localización (If-Match): enderezo e punto no mapa")
  @VersionedResource
  ResponseEntity<FacilityResponse> updateAddress(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody AddressRequest request) {
    FacilityView updated =
        sections.updateAddress(
            FacilityWebMapper.toCommand(new FacilityId(id), ETags.parseIfMatch(ifMatch), request));
    return withETag(ResponseEntity.ok(), updated);
  }

  @PutMapping("/{id}/amenities")
  @Operation(
      operationId = "updateFacilityAmenities",
      summary = "Sección Servizos e accesibilidade (If-Match): a lista enteira")
  @VersionedResource
  ResponseEntity<FacilityResponse> updateAmenities(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody UpdateFacilityAmenitiesRequest request) {
    FacilityView updated =
        sections.updateAmenities(
            FacilityWebMapper.toCommand(new FacilityId(id), ETags.parseIfMatch(ifMatch), request));
    return withETag(ResponseEntity.ok(), updated);
  }

  @PutMapping("/{id}/status")
  @Operation(
      operationId = "changeFacilityStatus",
      summary = "Cambiar o estado (If-Match); 409 con affected[] se hai reservas sen decisión")
  @ApiResponse(responseCode = "200", description = "Cambiado", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "409",
      description = "Reservas afectadas sen decisión: reenviar con decisions[]",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = AffectedBookingsProblemResponse.class)))
  @VersionedResource
  ResponseEntity<FacilityResponse> changeStatus(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody ChangeFacilityStatusRequest request) {
    FacilityView updated =
        status.changeStatus(
            FacilityWebMapper.toCommand(new FacilityId(id), ETags.parseIfMatch(ifMatch), request));
    return withETag(ResponseEntity.ok(), updated);
  }

  @PutMapping("/{id}/publication")
  @Operation(
      operationId = "publishFacility",
      summary =
          "Publicar (If-Match): desde agora a ve a veciñanza. 409 facility.not-publishable sen"
              + " enderezo, deportes, usos ou horario")
  @ApiResponse(responseCode = "200", description = "Publicada", useReturnTypeSchema = true)
  @VersionedResource
  ResponseEntity<FacilityResponse> publish(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
    FacilityView updated =
        publish.publish(
            new PublishFacilityCommand(new FacilityId(id), ETags.parseIfMatch(ifMatch)));
    return withETag(ResponseEntity.ok(), updated);
  }

  @DeleteMapping("/{id}")
  @Operation(
      operationId = "deleteFacility",
      summary =
          "Eliminar a instalación (If-Match) co seu horario, usos e lista de imaxes; as fotos seguen"
              + " na biblioteca. 409 facility.has-bookings se tivo reservas")
  @ApiResponse(responseCode = "204", description = "Eliminada")
  @VersionedResource
  ResponseEntity<Void> delete(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
    delete.delete(new DeleteFacilityCommand(new FacilityId(id), ETags.parseIfMatch(ifMatch)));
    return ResponseEntity.noContent().build();
  }

  private static ResponseEntity<FacilityResponse> withETag(
      ResponseEntity.BodyBuilder builder, FacilityView view) {
    Long version = view.value().version();
    return builder
        .eTag(ETags.of(version == null ? 0 : version))
        .body(FacilityWebMapper.toResponse(view));
  }
}
