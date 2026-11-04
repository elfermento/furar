// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web;

import gal.furar.shared.domain.Language;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.Locales;
import gal.furar.shared.web.VersionedResource;
import gal.furar.staff.adapter.in.web.dto.ChangeStaffRoleRequest;
import gal.furar.staff.adapter.in.web.dto.InviteStaffMemberRequest;
import gal.furar.staff.adapter.in.web.dto.StaffMemberResponse;
import gal.furar.staff.adapter.in.web.dto.StaffStatusValue;
import gal.furar.staff.application.port.in.ChangeStaffRoleCommand;
import gal.furar.staff.application.port.in.DeactivateStaffMemberCommand;
import gal.furar.staff.application.port.in.InviteStaffMemberCommand;
import gal.furar.staff.application.port.in.InviteStaffUseCase;
import gal.furar.staff.application.port.in.ListStaffUseCase;
import gal.furar.staff.application.port.in.ManageStaffMemberUseCase;
import gal.furar.staff.application.port.in.ReactivateStaffMemberCommand;
import gal.furar.staff.application.port.in.ResendStaffInvitationCommand;
import gal.furar.staff.application.port.in.StaffMemberView;
import gal.furar.staff.domain.StaffMemberId;
import gal.furar.staff.domain.StaffRole;
import gal.furar.staff.domain.StaffStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
 * Persoal do concello (plan do dominio 4, §2.3): lista, detalle, invitar, reenviar, cambiar o rol,
 * desactivar e reactivar. Controlador fino; o permiso vive no caso de uso. Cada resposta dunha
 * pertenza leva o seu {@code ETag}, e os cambios piden {@code If-Match} (412 se é vello, 428 se
 * falta).
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/staff")
@Tag(name = "staff", description = "Persoal")
class StaffController {

  private static final String PROBLEM = "#/components/schemas/Problem";
  private static final String RATE_LIMITED =
      "staff.invitation-rate-limited: 30 envíos por concello en 24 h, 2 min entre envíos á"
          + " mesma persoa e 5 por persoa. Retry-After di cantos segundos esperar (sen el,"
          + " esperar non serve)";

  private final ListStaffUseCase list;
  private final InviteStaffUseCase invite;
  private final ManageStaffMemberUseCase manage;

  StaffController(
      ListStaffUseCase list, InviteStaffUseCase invite, ManageStaffMemberUseCase manage) {
    this.list = list;
    this.invite = invite;
    this.manage = manage;
  }

  @GetMapping
  @Operation(
      operationId = "listStaff",
      summary = "O persoal do concello, todo ou só dun estado, das pertenzas máis novas ás vellas")
  List<StaffMemberResponse> list(
      @PathVariable String slug,
      @RequestParam(required = false) @Nullable StaffStatusValue status) {
    return list.list(status == null ? null : StaffStatus.valueOf(status.name())).stream()
        .map(StaffWebMapper::toResponse)
        .toList();
  }

  @GetMapping("/{id}")
  @Operation(operationId = "getStaffMember", summary = "Unha pertenza")
  @VersionedResource
  ResponseEntity<StaffMemberResponse> get(@PathVariable String slug, @PathVariable UUID id) {
    return withETag(ResponseEntity.ok(), list.get(new StaffMemberId(id)));
  }

  @PostMapping
  @Operation(
      operationId = "inviteStaffMember",
      summary =
          "Invitar por correo: crea a pertenza, envía o correo e responde como foi a entrega"
              + " (invitation.delivery)")
  @ApiResponse(responseCode = "201", description = "Invitada", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "422",
      description = "staff.email-invalid: o correo non ten forma de correo",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "429",
      description = RATE_LIMITED,
      headers = @Header(name = HttpHeaders.RETRY_AFTER, schema = @Schema(type = "integer")),
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @VersionedResource
  ResponseEntity<StaffMemberResponse> invite(
      @PathVariable String slug,
      @Valid @RequestBody InviteStaffMemberRequest request,
      HttpServletRequest http) {
    Language language =
        request.language() == null
            ? Locales.language(http)
            : Language.fromCode(request.language()).orElseThrow();
    StaffMemberView created =
        invite.invite(
            new InviteStaffMemberCommand(
                request.email(), StaffRole.valueOf(request.role().name()), language));
    URI location =
        URI.create("/api/v1/municipalities/" + slug + "/staff/" + created.member().id().value());
    return withETag(ResponseEntity.created(location), created);
  }

  @PostMapping("/{id}/invitation")
  @Operation(
      operationId = "resendStaffInvitation",
      summary =
          "Reenviar a invitación (If-Match; só invitadas): substitúe a anterior e conta outros 7"
              + " días")
  @ApiResponse(responseCode = "200", description = "Reenviada", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "429",
      description = RATE_LIMITED,
      headers = @Header(name = HttpHeaders.RETRY_AFTER, schema = @Schema(type = "integer")),
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @VersionedResource
  ResponseEntity<StaffMemberResponse> resend(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
    StaffMemberView resent =
        invite.resend(
            new ResendStaffInvitationCommand(new StaffMemberId(id), ETags.parseIfMatch(ifMatch)));
    return withETag(ResponseEntity.ok(), resent);
  }

  @PutMapping("/{id}/role")
  @Operation(
      operationId = "changeStaffRole",
      summary = "Cambiar o rol (If-Match); 409 staff.last-manager se deixa o concello sen xestor")
  @VersionedResource
  ResponseEntity<StaffMemberResponse> changeRole(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody ChangeStaffRoleRequest request) {
    StaffMemberView changed =
        manage.changeRole(
            new ChangeStaffRoleCommand(
                new StaffMemberId(id),
                ETags.parseIfMatch(ifMatch),
                StaffRole.valueOf(request.role().name())));
    return withETag(ResponseEntity.ok(), changed);
  }

  @PostMapping("/{id}/deactivate")
  @Operation(
      operationId = "deactivateStaffMember",
      summary =
          "Desactivar (If-Match): perde o acceso na seguinte petición e a invitación aberta"
              + " deixa de valer; non se borra nada e pódese reactivar. 409 staff.last-manager"
              + " se é a última persoa xestora")
  @VersionedResource
  ResponseEntity<StaffMemberResponse> deactivate(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
    StaffMemberView deactivated =
        manage.deactivate(
            new DeactivateStaffMemberCommand(new StaffMemberId(id), ETags.parseIfMatch(ifMatch)));
    return withETag(ResponseEntity.ok(), deactivated);
  }

  @PostMapping("/{id}/reactivate")
  @Operation(
      operationId = "reactivateStaffMember",
      summary =
          "Reactivar (If-Match) unha persoa desactivada que aceptou: volve entrar co mesmo rol,"
              + " sen invitación nova. 409 staff.anonymised,"
              + " staff.already-member (xa hai outra pertenza viva) ou staff.personal-account (a"
              + " conta reservou como veciña)")
  @VersionedResource
  ResponseEntity<StaffMemberResponse> reactivate(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
    StaffMemberView reactivated =
        manage.reactivate(
            new ReactivateStaffMemberCommand(new StaffMemberId(id), ETags.parseIfMatch(ifMatch)));
    return withETag(ResponseEntity.ok(), reactivated);
  }

  private static ResponseEntity<StaffMemberResponse> withETag(
      ResponseEntity.BodyBuilder builder, StaffMemberView view) {
    Long version = view.member().version();
    return builder
        .eTag(ETags.of(version == null ? 0 : version))
        .body(StaffWebMapper.toResponse(view));
  }
}
