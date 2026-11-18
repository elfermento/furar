// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.OrganizationRequest;
import gal.furar.booking.adapter.in.web.dto.OrganizationResponse;
import gal.furar.booking.adapter.in.web.dto.OrganizationRowResponse;
import gal.furar.booking.adapter.in.web.dto.OrganizationStatusValue;
import gal.furar.booking.application.port.in.ManageOrganizationsUseCase;
import gal.furar.booking.application.port.in.OrganizationCommand;
import gal.furar.booking.domain.season.Organization;
import gal.furar.booking.domain.season.OrganizationStatus;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.VersionedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Objects;
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
 * As entidades do concello e os seus responsables (ADR-0039, punto 7; permiso {@code
 * booking.organizations}).
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/organizations")
@Tag(name = "bookings", description = "Reservas")
class OrganizationsController {

  private final ManageOrganizationsUseCase organizations;

  OrganizationsController(ManageOrganizationsUseCase organizations) {
    this.organizations = organizations;
  }

  @GetMapping
  @Operation(
      operationId = "listOrganizations",
      summary =
          "As entidades dadas de alta (ou só as arquivadas), por nome, co seu estado: con"
              + " solicitude pendente, en tempada ou sen tempada")
  List<OrganizationRowResponse> list(
      @PathVariable String slug,
      @RequestParam(required = false) @Nullable OrganizationStatusValue status,
      @RequestParam(defaultValue = "false") boolean archived) {
    return organizations
        .list(status == null ? null : OrganizationStatus.valueOf(status.name()), archived)
        .stream()
        .map(SeasonWebMapper::row)
        .toList();
  }

  @GetMapping("/{id}")
  @Operation(operationId = "getOrganization", summary = "Unha entidade, cos seus responsables")
  @VersionedResource
  ResponseEntity<OrganizationResponse> get(@PathVariable String slug, @PathVariable UUID id) {
    return entity(organizations.get(id));
  }

  @PostMapping
  @Operation(
      operationId = "createOrganization",
      summary =
          "Dar de alta unha entidade: nome, contacto, correo (obrigatorio), teléfono e ata 10"
              + " responsables polo seu correo")
  @ApiResponse(responseCode = "201", description = "Dada de alta", useReturnTypeSchema = true)
  @VersionedResource
  ResponseEntity<OrganizationResponse> create(
      @PathVariable String slug, @Valid @RequestBody OrganizationRequest request) {
    Organization created = organizations.create(command(request));
    return ResponseEntity.created(
            URI.create("/api/v1/municipalities/" + slug + "/organizations/" + created.id()))
        .eTag(ETags.of(Objects.requireNonNull(created.version())))
        .body(SeasonWebMapper.organization(created));
  }

  @PutMapping("/{id}")
  @Operation(
      operationId = "updateOrganization",
      summary =
          "Cambiar os datos dunha entidade (If-Match); os responsables substitúen os de antes e o"
              + " nome novo vai tamén ás súas reservas")
  @VersionedResource
  ResponseEntity<OrganizationResponse> update(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody OrganizationRequest request) {
    return entity(organizations.update(id, ETags.parseIfMatch(ifMatch), command(request)));
  }

  @DeleteMapping("/{id}")
  @Operation(
      operationId = "deleteOrganization",
      summary =
          "Eliminar unha entidade (If-Match): sen ningunha reserva nin tempada na súa historia,"
              + " bórrase; con ela, arquívase (queda o nome, sen contacto nin responsables). Con"
              + " sesións ou reservas futuras, 409 booking.organization-has-future-bookings")
  @ApiResponse(responseCode = "204", description = "Eliminada ou arquivada")
  ResponseEntity<Void> delete(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
    organizations.delete(id, ETags.parseIfMatch(ifMatch));
    return ResponseEntity.noContent().build();
  }

  private static ResponseEntity<OrganizationResponse> entity(Organization o) {
    return ResponseEntity.ok()
        .eTag(ETags.of(Objects.requireNonNull(o.version())))
        .body(SeasonWebMapper.organization(o));
  }

  private static OrganizationCommand command(OrganizationRequest r) {
    return new OrganizationCommand(r.name(), r.contactName(), r.email(), r.phone(), r.managers());
  }
}
