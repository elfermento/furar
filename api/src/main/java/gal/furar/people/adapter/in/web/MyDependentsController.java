// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.in.web;

import gal.furar.people.adapter.in.web.dto.DependentResponse;
import gal.furar.people.adapter.in.web.dto.SaveDependentRequest;
import gal.furar.people.application.port.in.ManageDependentsUseCase;
import gal.furar.people.application.port.in.SaveDependentCommand;
import gal.furar.people.domain.Dependent;
import gal.furar.people.domain.DependentId;
import gal.furar.shared.web.ETags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * As persoas a cargo da conta da sesión (ADR-0031, punto 9). Fóra de {@code /municipalities/{slug}}
 * porque a conta é común a todos os concellos; só sesión e correo verificado, e só as da propia
 * persoa. Controlador fino: o permiso vive no caso de uso.
 */
@RestController
@RequestMapping("/api/v1/me/dependents")
@Tag(name = "people", description = "Persoas a cargo")
class MyDependentsController {

  private final ManageDependentsUseCase dependents;

  MyDependentsController(ManageDependentsUseCase dependents) {
    this.dependents = dependents;
  }

  @GetMapping
  @Operation(operationId = "listMyDependents", summary = "As persoas ao meu cargo")
  List<DependentResponse> list() {
    return dependents.list().stream().map(MyDependentsController::response).toList();
  }

  @PostMapping
  @Operation(operationId = "createMyDependent", summary = "Gardar unha persoa ao meu cargo")
  @ApiResponse(responseCode = "201", description = "Creada", useReturnTypeSchema = true)
  ResponseEntity<DependentResponse> create(@Valid @RequestBody SaveDependentRequest request) {
    Dependent created = dependents.create(command(request));
    return ResponseEntity.created(URI.create("/api/v1/me/dependents/" + created.id().value()))
        .eTag(ETags.of(version(created)))
        .body(response(created));
  }

  @PutMapping("/{id}")
  @Operation(operationId = "updateMyDependent", summary = "Editar unha persoa (If-Match)")
  ResponseEntity<DependentResponse> update(
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody SaveDependentRequest request) {
    Dependent updated =
        dependents.update(new DependentId(id), ETags.parseIfMatch(ifMatch), command(request));
    return ResponseEntity.ok().eTag(ETags.of(version(updated))).body(response(updated));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteMyDependent", summary = "Eliminar unha persoa")
  void delete(@PathVariable UUID id) {
    dependents.delete(new DependentId(id));
  }

  private static SaveDependentCommand command(SaveDependentRequest request) {
    return new SaveDependentCommand(
        request.name(), request.birthYear(), Boolean.TRUE.equals(request.inMyCare()));
  }

  private static DependentResponse response(Dependent dependent) {
    return new DependentResponse(
        dependent.id().value(), dependent.name(), dependent.birthYear(), version(dependent));
  }

  private static long version(Dependent dependent) {
    Long version = dependent.version();
    return version == null ? 0 : version;
  }
}
