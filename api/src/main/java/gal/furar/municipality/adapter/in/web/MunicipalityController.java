// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web;

import gal.furar.municipality.adapter.in.web.dto.CreateMunicipalityRequest;
import gal.furar.municipality.adapter.in.web.dto.MunicipalityResponse;
import gal.furar.municipality.adapter.in.web.dto.MunicipalitySummaryResponse;
import gal.furar.municipality.adapter.in.web.dto.ThemeResponse;
import gal.furar.municipality.adapter.in.web.dto.UpdateMunicipalityRequest;
import gal.furar.municipality.application.port.in.CreateMunicipalityUseCase;
import gal.furar.municipality.application.port.in.GetMunicipalityUseCase;
import gal.furar.municipality.application.port.in.ListMunicipalitiesUseCase;
import gal.furar.municipality.application.port.in.MunicipalityView;
import gal.furar.municipality.application.port.in.UpdateMunicipalityUseCase;
import gal.furar.shared.security.PublicEndpoint;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.PageResponse;
import gal.furar.shared.web.VersionedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
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

/** Concellos: lista e detalle públicos; alta (plataforma) e edición (xestor). */
@RestController
@RequestMapping("/api/v1/municipalities")
@Tag(name = "municipalities", description = "Concellos")
class MunicipalityController {

  private final CreateMunicipalityUseCase create;
  private final GetMunicipalityUseCase get;
  private final ListMunicipalitiesUseCase list;
  private final UpdateMunicipalityUseCase update;

  MunicipalityController(
      CreateMunicipalityUseCase create,
      GetMunicipalityUseCase get,
      ListMunicipalitiesUseCase list,
      UpdateMunicipalityUseCase update) {
    this.create = create;
    this.get = get;
    this.list = list;
    this.update = update;
  }

  @GetMapping
  @PublicEndpoint
  @Operation(operationId = "listMunicipalities", summary = "Concellos da instalación")
  PageResponse<MunicipalitySummaryResponse> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(list.list(page, size), MunicipalityWebMapper::toSummary);
  }

  @PostMapping
  @Operation(operationId = "createMunicipality", summary = "Alta dun concello (plataforma)")
  @ApiResponse(responseCode = "201", description = "Creado", useReturnTypeSchema = true)
  @VersionedResource
  ResponseEntity<MunicipalityResponse> create(
      @Valid @RequestBody CreateMunicipalityRequest request) {
    MunicipalityView created = create.create(MunicipalityWebMapper.toCommand(request));
    URI location = URI.create("/api/v1/municipalities/" + created.municipality().slug().value());
    return withETag(ResponseEntity.created(location), created);
  }

  @GetMapping("/{slug}")
  @PublicEndpoint
  @Operation(operationId = "getMunicipality", summary = "Datos dun concello")
  @VersionedResource
  ResponseEntity<MunicipalityResponse> get(@PathVariable String slug) {
    return withETag(ResponseEntity.ok(), get.getCurrent());
  }

  @GetMapping("/{slug}/theme")
  @PublicEndpoint
  @Operation(operationId = "getMunicipalityTheme", summary = "Tema (cores) do concello")
  ThemeResponse theme(@PathVariable String slug) {
    return MunicipalityWebMapper.theme(get.getCurrent().municipality().theme());
  }

  @PutMapping("/{slug}")
  @Operation(operationId = "updateMunicipality", summary = "Editar o concello (If-Match)")
  @VersionedResource
  ResponseEntity<MunicipalityResponse> update(
      @PathVariable String slug,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody UpdateMunicipalityRequest request) {
    MunicipalityView updated =
        update.update(MunicipalityWebMapper.toCommand(ETags.parseIfMatch(ifMatch), request));
    return withETag(ResponseEntity.ok(), updated);
  }

  private static ResponseEntity<MunicipalityResponse> withETag(
      ResponseEntity.BodyBuilder builder, MunicipalityView view) {
    Long version = view.municipality().version();
    return builder
        .eTag(ETags.of(version == null ? 0 : version))
        .body(MunicipalityWebMapper.toResponse(view));
  }
}
