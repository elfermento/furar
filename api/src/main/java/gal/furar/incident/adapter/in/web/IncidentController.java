// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.adapter.in.web;

import gal.furar.incident.adapter.in.web.dto.IncidentCountResponse;
import gal.furar.incident.adapter.in.web.dto.IncidentReportedResponse;
import gal.furar.incident.adapter.in.web.dto.IncidentResponse;
import gal.furar.incident.adapter.in.web.dto.RecordIncidentRequest;
import gal.furar.incident.adapter.in.web.dto.ReportIncidentRequest;
import gal.furar.incident.adapter.in.web.dto.ReviewIncidentRequest;
import gal.furar.incident.application.port.in.DeleteIncidentUseCase;
import gal.furar.incident.application.port.in.GetIncidentUseCase;
import gal.furar.incident.application.port.in.IncidentView;
import gal.furar.incident.application.port.in.ListIncidentsUseCase;
import gal.furar.incident.application.port.in.PhotoBytes;
import gal.furar.incident.application.port.in.RecordIncidentUseCase;
import gal.furar.incident.application.port.in.ReportIncidentCommand;
import gal.furar.incident.application.port.in.ReportIncidentUseCase;
import gal.furar.incident.application.port.in.ReviewIncidentCommand;
import gal.furar.incident.application.port.in.ReviewIncidentUseCase;
import gal.furar.incident.domain.IncidentError;
import gal.furar.incident.domain.IncidentId;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.web.ETags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Incidencias (ADR-0036): o aviso da veciñanza ({@code POST …/incidents/reports}, con sesión e sen
 * rol) e a xestión do persoal (lista, contador, detalle, fotos, «Gardar» con {@code If-Match} e
 * «Nova incidencia»). Os avisos son {@code multipart/form-data} con ata tres fotos de 8 MB (Caddy
 * deixa pasar 25 MB só nesas dúas rutas). Ningunha operación é pública: as incidencias non se ven
 * sen sesión, e as fotos tampouco (a web pídeas co token). O permiso vive no caso de uso.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/incidents")
@Tag(name = "incidents", description = "Incidencias")
class IncidentController {

  /** As fotos dunha incidencia non cambian de bytes, pero son datos do concello: só privadas. */
  static final CacheControl PRIVATE_DAY =
      CacheControl.maxAge(Duration.ofDays(1)).cachePrivate().immutable();

  private final ReportIncidentUseCase report;
  private final RecordIncidentUseCase record;
  private final ListIncidentsUseCase list;
  private final GetIncidentUseCase get;
  private final ReviewIncidentUseCase review;
  private final DeleteIncidentUseCase delete;

  IncidentController(
      ReportIncidentUseCase report,
      RecordIncidentUseCase record,
      ListIncidentsUseCase list,
      GetIncidentUseCase get,
      ReviewIncidentUseCase review,
      DeleteIncidentUseCase delete) {
    this.report = report;
    this.record = record;
    this.list = list;
    this.get = get;
    this.review = review;
    this.delete = delete;
  }

  @PostMapping(value = "/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      operationId = "reportIncident",
      summary =
          "Avisar dun problema (a veciñanza, con sesión e correo verificado): instalación que se"
              + " ve no público, reserva propia se vén do historial, tipo, texto e ata 3 fotos")
  @ApiResponse(responseCode = "201", description = "Gardado", useReturnTypeSchema = true)
  ResponseEntity<IncidentReportedResponse> report(
      @PathVariable String slug, @Valid @ModelAttribute ReportIncidentRequest form) {
    IncidentId id =
        report.report(
            new ReportIncidentCommand(
                form.facilityId(),
                form.bookingId(),
                IncidentWebMapper.type(form.type()),
                form.description(),
                IncidentWebMapper.bytes(form.photos())));
    return ResponseEntity.created(
            URI.create("/api/v1/municipalities/" + slug + "/incidents/" + id.value()))
        .body(new IncidentReportedResponse(id.value()));
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      operationId = "recordIncident",
      summary = "Nova incidencia (o persoal): instalación do concello, tipo, texto e ata 3 fotos")
  @ApiResponse(responseCode = "201", description = "Gardada", useReturnTypeSchema = true)
  ResponseEntity<IncidentResponse> record(
      @PathVariable String slug, @Valid @ModelAttribute RecordIncidentRequest form) {
    IncidentView created =
        record.record(
            new ReportIncidentCommand(
                form.facilityId(),
                null,
                IncidentWebMapper.type(form.type()),
                form.description(),
                IncidentWebMapper.bytes(form.photos())));
    return withETag(
        ResponseEntity.created(
            URI.create(
                "/api/v1/municipalities/"
                    + slug
                    + "/incidents/"
                    + created.incident().id().value())),
        created);
  }

  @GetMapping
  @Operation(
      operationId = "listIncidents",
      summary = "As incidencias do concello, da máis nova á máis vella (ata 1000)")
  List<IncidentResponse> list(@PathVariable String slug) {
    return list.list().stream().map(IncidentWebMapper::toResponse).toList();
  }

  @GetMapping("/new-count")
  @Operation(operationId = "countNewIncidents", summary = "Cantas incidencias novas hai")
  IncidentCountResponse countNew(@PathVariable String slug) {
    return new IncidentCountResponse(list.countNew());
  }

  @GetMapping("/{id}")
  @Operation(operationId = "getIncident", summary = "Detalle, coa reserva se vén dunha")
  ResponseEntity<IncidentResponse> get(@PathVariable String slug, @PathVariable UUID id) {
    return withETag(ResponseEntity.ok(), get.get(new IncidentId(id)));
  }

  @PutMapping("/{id}")
  @Operation(
      operationId = "reviewIncident",
      summary =
          "Gardar o estado, a nota interna e, ao resolver, a mensaxe para a persoa (If-Match)."
              + " Pasar a Resolta un aviso da veciñanza envíalle un correo")
  ResponseEntity<IncidentResponse> review(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody ReviewIncidentRequest request) {
    IncidentView saved =
        review.review(
            new ReviewIncidentCommand(
                new IncidentId(id),
                ETags.parseIfMatch(ifMatch),
                IncidentWebMapper.status(request.status()),
                request.internalNote(),
                request.resolutionMessage()));
    return withETag(ResponseEntity.ok(), saved);
  }

  @DeleteMapping("/{id}")
  @Operation(
      operationId = "deleteIncident",
      summary = "Eliminar a incidencia coas súas fotos (só a xestión)")
  @ApiResponse(responseCode = "204", description = "Eliminada")
  ResponseEntity<Void> delete(@PathVariable String slug, @PathVariable UUID id) {
    delete.delete(new IncidentId(id));
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}/photos/{photoId}")
  @Operation(
      operationId = "deleteIncidentPhoto",
      summary = "Quitar unha foto da incidencia (só a xestión); devolve a incidencia como queda")
  ResponseEntity<IncidentResponse> deletePhoto(
      @PathVariable String slug, @PathVariable UUID id, @PathVariable UUID photoId) {
    return withETag(ResponseEntity.ok(), delete.deletePhoto(new IncidentId(id), photoId));
  }

  @GetMapping("/{id}/photos/{photoId}/{variant}")
  @Operation(
      operationId = "getIncidentPhoto",
      summary = "Bytes dunha foto (large: 1600 px; thumb: 480 px), sen EXIF e con sesión")
  @ApiResponse(
      responseCode = "200",
      description = "A imaxe",
      content =
          @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")))
  ResponseEntity<byte[]> photo(
      @PathVariable String slug,
      @PathVariable UUID id,
      @PathVariable UUID photoId,
      @PathVariable @Parameter(schema = @Schema(allowableValues = {"large", "thumb"}))
          String variant) {
    if (!"large".equals(variant) && !"thumb".equals(variant)) {
      throw new DomainException(IncidentError.PHOTO_NOT_FOUND);
    }
    PhotoBytes bytes = get.photo(new IncidentId(id), photoId, "large".equals(variant));
    return ResponseEntity.ok()
        .cacheControl(PRIVATE_DAY)
        .contentType(MediaType.parseMediaType(bytes.contentType()))
        .body(bytes.bytes());
  }

  private static ResponseEntity<IncidentResponse> withETag(
      ResponseEntity.BodyBuilder builder, IncidentView view) {
    Long version = view.incident().version();
    return builder
        .eTag(ETags.of(version == null ? 0 : version))
        .body(IncidentWebMapper.toResponse(view));
  }
}
