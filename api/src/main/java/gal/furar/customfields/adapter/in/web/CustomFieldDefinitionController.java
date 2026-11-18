// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.in.web;

import gal.furar.customfields.adapter.in.web.dto.CustomFieldDefinitionRequest;
import gal.furar.customfields.adapter.in.web.dto.CustomFieldDefinitionResponse;
import gal.furar.customfields.adapter.in.web.dto.UpdateCustomFieldDefinitionRequest;
import gal.furar.customfields.application.port.in.ChangeCustomFieldStatusUseCase;
import gal.furar.customfields.application.port.in.CreateCustomFieldDefinitionUseCase;
import gal.furar.customfields.application.port.in.CustomFieldInUse;
import gal.furar.customfields.application.port.in.GetCustomFieldDefinitionUseCase;
import gal.furar.customfields.application.port.in.GetCustomFieldInUseUseCase;
import gal.furar.customfields.application.port.in.GetCustomFieldSchemaUseCase;
import gal.furar.customfields.application.port.in.ListCustomFieldDefinitionsUseCase;
import gal.furar.customfields.application.port.in.UpdateCustomFieldDefinitionCommand;
import gal.furar.customfields.application.port.in.UpdateCustomFieldDefinitionUseCase;
import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.customfields.domain.CustomFieldDefinitionId;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.Language;
import gal.furar.shared.security.PublicEndpoint;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.Locales;
import gal.furar.shared.web.VersionedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Definicións de campos personalizados dun concello (ADR-0011). */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/custom-fields")
@Tag(name = "custom-fields", description = "Campos personalizados por concello")
class CustomFieldDefinitionController {

  private final CreateCustomFieldDefinitionUseCase create;
  private final UpdateCustomFieldDefinitionUseCase update;
  private final ChangeCustomFieldStatusUseCase changeStatus;
  private final GetCustomFieldDefinitionUseCase get;
  private final ListCustomFieldDefinitionsUseCase list;
  private final GetCustomFieldSchemaUseCase schema;
  private final GetCustomFieldInUseUseCase inUse;

  CustomFieldDefinitionController(
      CreateCustomFieldDefinitionUseCase create,
      UpdateCustomFieldDefinitionUseCase update,
      ChangeCustomFieldStatusUseCase changeStatus,
      GetCustomFieldDefinitionUseCase get,
      ListCustomFieldDefinitionsUseCase list,
      GetCustomFieldSchemaUseCase schema,
      GetCustomFieldInUseUseCase inUse) {
    this.create = create;
    this.update = update;
    this.changeStatus = changeStatus;
    this.get = get;
    this.list = list;
    this.schema = schema;
    this.inUse = inUse;
  }

  @GetMapping
  @PublicEndpoint
  @Operation(
      operationId = "listCustomFields",
      summary =
          "Campos usados no destino e visibles para quen pregunta, en orde alfabética (os"
              + " desactivados, status INACTIVE, só para ler os valores gardados);"
              + " includeInactive=true (xestor) devolve tamén os desactivados e, sen target,"
              + " todos, con hasValues")
  List<CustomFieldDefinitionResponse> list(
      @PathVariable String slug,
      @RequestParam(required = false) @Schema(example = "MUNICIPALITY") String target,
      @RequestParam(defaultValue = "false") boolean includeInactive,
      HttpServletRequest request) {
    Language language = Locales.language(request);
    if (target == null) {
      return managerResponse(list.listAll(), language);
    }
    CustomFieldTarget wanted = new CustomFieldTarget(target);
    if (includeInactive) {
      return managerResponse(list.listAll(wanted), language);
    }
    return CustomFieldWebMapper.sorted(list.listVisible(wanted), language).stream()
        .map(
            d -> CustomFieldWebMapper.toResponse(d, language, u -> u.target().equals(wanted), null))
        .toList();
  }

  /** Lista do xestor: cos valores gardados de cada campo e opción (dúas consultas). */
  private List<CustomFieldDefinitionResponse> managerResponse(
      List<CustomFieldDefinition> definitions, Language language) {
    Map<CustomFieldDefinitionId, CustomFieldInUse> used = inUse.inUse(definitions);
    return CustomFieldWebMapper.sorted(definitions, language).stream()
        .map(d -> CustomFieldWebMapper.toResponse(d, language, used.get(d.id())))
        .toList();
  }

  @GetMapping("/{target}/schema")
  @PublicEndpoint
  @Operation(operationId = "getCustomFieldsSchema", summary = "JSON Schema 2020-12 dos campos")
  Map<String, Object> schema(@PathVariable String slug, @PathVariable String target) {
    return schema.schema(new CustomFieldTarget(target));
  }

  @GetMapping("/{id}")
  @Operation(operationId = "getCustomField", summary = "Unha definición, para editala")
  @VersionedResource
  ResponseEntity<CustomFieldDefinitionResponse> get(
      @PathVariable String slug, @PathVariable UUID id, HttpServletRequest request) {
    return withETag(ResponseEntity.ok(), get.get(new CustomFieldDefinitionId(id)), request);
  }

  @PostMapping
  @Operation(operationId = "createCustomField", summary = "Definir un campo novo")
  @ApiResponse(responseCode = "201", description = "Creado", useReturnTypeSchema = true)
  @VersionedResource
  ResponseEntity<CustomFieldDefinitionResponse> create(
      @PathVariable String slug,
      @Valid @RequestBody CustomFieldDefinitionRequest body,
      HttpServletRequest request) {
    CustomFieldDefinition created = create.create(CustomFieldWebMapper.toCommand(body));
    URI location =
        URI.create("/api/v1/municipalities/" + slug + "/custom-fields/" + created.id().value());
    return withETag(ResponseEntity.created(location), created, request);
  }

  @PutMapping("/{id}")
  @Operation(operationId = "updateCustomField", summary = "Editar un campo (If-Match)")
  @VersionedResource
  ResponseEntity<CustomFieldDefinitionResponse> update(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody UpdateCustomFieldDefinitionRequest body,
      HttpServletRequest request) {
    CustomFieldDefinition updated =
        update.update(
            new UpdateCustomFieldDefinitionCommand(
                new CustomFieldDefinitionId(id),
                ETags.parseIfMatch(ifMatch),
                CustomFieldWebMapper.toSpec(body)));
    return withETag(ResponseEntity.ok(), updated, request);
  }

  @PostMapping("/{id}/deactivate")
  @Operation(
      operationId = "deactivateCustomField",
      summary = "Desactivar un campo (If-Match): deixa de pedirse; os valores consérvanse e vense")
  @VersionedResource
  ResponseEntity<CustomFieldDefinitionResponse> deactivate(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      HttpServletRequest request) {
    return withETag(
        ResponseEntity.ok(),
        changeStatus.deactivate(new CustomFieldDefinitionId(id), ETags.parseIfMatch(ifMatch)),
        request);
  }

  @PostMapping("/{id}/activate")
  @Operation(operationId = "activateCustomField", summary = "Activar un campo (If-Match)")
  @VersionedResource
  ResponseEntity<CustomFieldDefinitionResponse> activate(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      HttpServletRequest request) {
    return withETag(
        ResponseEntity.ok(),
        changeStatus.activate(new CustomFieldDefinitionId(id), ETags.parseIfMatch(ifMatch)),
        request);
  }

  @DeleteMapping("/{id}")
  @Operation(
      operationId = "deleteCustomField",
      summary =
          "Eliminar un campo sen valores (If-Match); con valores, 409 customfield.has-values:"
              + " desactívase")
  @ApiResponse(responseCode = "204", description = "Eliminado")
  ResponseEntity<Void> delete(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
    changeStatus.delete(new CustomFieldDefinitionId(id), ETags.parseIfMatch(ifMatch));
    return ResponseEntity.noContent().build();
  }

  private ResponseEntity<CustomFieldDefinitionResponse> withETag(
      ResponseEntity.BodyBuilder builder,
      CustomFieldDefinition definition,
      HttpServletRequest request) {
    long version = definition.version() == null ? 0 : definition.version();
    CustomFieldInUse used = inUse.inUse(List.of(definition)).get(definition.id());
    return builder
        .eTag(ETags.of(version))
        .body(CustomFieldWebMapper.toResponse(definition, Locales.language(request), used));
  }
}
