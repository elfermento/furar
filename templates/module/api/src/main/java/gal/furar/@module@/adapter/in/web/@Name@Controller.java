// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.adapter.in.web;

import gal.furar.@module@.adapter.in.web.dto.Create@Name@Request;
import gal.furar.@module@.adapter.in.web.dto.Update@Name@Request;
import gal.furar.@module@.adapter.in.web.dto.@Name@Response;
import gal.furar.@module@.application.port.in.Create@Name@UseCase;
import gal.furar.@module@.application.port.in.Get@Name@UseCase;
import gal.furar.@module@.application.port.in.List@Names@UseCase;
import gal.furar.@module@.application.port.in.Update@Name@UseCase;
import gal.furar.@module@.application.port.in.@Name@View;
import gal.furar.@module@.domain.@Name@Id;
import gal.furar.shared.security.PublicEndpoint;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador fino: DTO ↔ comando e caso de uso. O permiso vive no caso de uso; aquí só se marca
 * que operacións se poden chamar sen token ({@link PublicEndpoint}, ADR-0007). As edicións levan
 * {@code If-Match} coa versión do {@code ETag}.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/@names@")
@Tag(name = "@names@", description = "@Names@")
class @Name@Controller {

  private final Create@Name@UseCase create;
  private final Get@Name@UseCase get;
  private final List@Names@UseCase list;
  private final Update@Name@UseCase update;

  @Name@Controller(
      Create@Name@UseCase create,
      Get@Name@UseCase get,
      List@Names@UseCase list,
      Update@Name@UseCase update) {
    this.create = create;
    this.get = get;
    this.list = list;
    this.update = update;
  }

  @GetMapping
  @PublicEndpoint
  @Operation(operationId = "list@Names@", summary = "Lista do concello")
  PageResponse<@Name@Response> list(
      @PathVariable String slug,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(list.list(page, size), @Name@WebMapper::toResponse);
  }

  @GetMapping("/{id}")
  @PublicEndpoint
  @Operation(operationId = "get@Name@", summary = "Detalle")
  ResponseEntity<@Name@Response> get(@PathVariable String slug, @PathVariable UUID id) {
    return withETag(ResponseEntity.ok(), get.get(new @Name@Id(id)));
  }

  @PostMapping
  @Operation(operationId = "create@Name@", summary = "Alta")
  @ApiResponse(responseCode = "201", description = "Creado", useReturnTypeSchema = true)
  ResponseEntity<@Name@Response> create(
      @PathVariable String slug, @Valid @RequestBody Create@Name@Request request) {
    @Name@View created = create.create(@Name@WebMapper.toCommand(request));
    URI location =
        URI.create("/api/v1/municipalities/" + slug + "/@names@/" + created.value().id().value());
    return withETag(ResponseEntity.created(location), created);
  }

  @PutMapping("/{id}")
  @Operation(operationId = "update@Name@", summary = "Editar (If-Match)")
  ResponseEntity<@Name@Response> update(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody Update@Name@Request request) {
    @Name@View updated =
        update.update(
            @Name@WebMapper.toCommand(new @Name@Id(id), ETags.parseIfMatch(ifMatch), request));
    return withETag(ResponseEntity.ok(), updated);
  }

  private static ResponseEntity<@Name@Response> withETag(
      ResponseEntity.BodyBuilder builder, @Name@View view) {
    Long version = view.value().version();
    return builder
        .eTag(ETags.of(version == null ? 0 : version))
        .body(@Name@WebMapper.toResponse(view));
  }
}
