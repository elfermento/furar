// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web;

import gal.furar.shared.security.PublicEndpoint;
import gal.furar.staff.adapter.in.web.dto.AcceptStaffInvitationRequest;
import gal.furar.staff.adapter.in.web.dto.AcceptedStaffInvitationResponse;
import gal.furar.staff.adapter.in.web.dto.PreviewStaffInvitationRequest;
import gal.furar.staff.adapter.in.web.dto.StaffInvitationPreviewResponse;
import gal.furar.staff.adapter.in.web.dto.StaffRoleValue;
import gal.furar.staff.application.port.in.AcceptStaffInvitationCommand;
import gal.furar.staff.application.port.in.StaffInvitationPreview;
import gal.furar.staff.application.port.in.StaffInvitationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A invitación ao persoal desde a ligazón do correo (plan do dominio 4, §1.2.6 e §2.3). O token vai
 * sempre no corpo dun {@code POST}, nunca no URL: así non chega aos rexistros de acceso nin ao
 * {@code Referer}. Ver non gasta a invitación; só aceptar.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/staff-invitations")
@Tag(name = "staff", description = "Persoal")
class StaffInvitationController {

  private static final String PROBLEM = "#/components/schemas/Problem";
  private static final String INVALID =
      "staff.invitation-invalid: token descoñecido, usado, substituído, doutro concello ou dunha"
          + " pertenza desactivada (a mesma resposta para todos)";
  private static final String EXPIRED = "staff.invitation-expired: caducou (7 días)";

  private final StaffInvitationUseCase invitations;

  StaffInvitationController(StaffInvitationUseCase invitations) {
    this.invitations = invitations;
  }

  @PostMapping("/preview")
  @PublicEndpoint
  @Operation(
      operationId = "previewStaffInvitation",
      summary =
          "Ver a invitación sen sesión e sen gastala: concello, rol, correo, caducidade e a proba"
              + " para que o rexistro de Keycloak dea o correo por verificado")
  @ApiResponse(responseCode = "200", description = "Vale", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "404",
      description = INVALID,
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "410",
      description = EXPIRED,
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  StaffInvitationPreviewResponse preview(
      @PathVariable String slug, @Valid @RequestBody PreviewStaffInvitationRequest request) {
    StaffInvitationPreview preview = invitations.preview(request.token());
    return new StaffInvitationPreviewResponse(
        preview.municipalityName(),
        StaffRoleValue.valueOf(preview.role().name()),
        preview.email(),
        preview.expiresAt(),
        preview.registrationProof());
  }

  @PostMapping("/accept")
  @Operation(
      operationId = "acceptStaffInvitation",
      summary =
          "Aceptar a invitación coa persoa da sesión (correo verificado e igual ao invitado); o"
              + " rol vale desde a seguinte petición")
  @ApiResponse(responseCode = "200", description = "Aceptada", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "403",
      description =
          "staff.invitation-email-mismatch: a sesión é doutro correo; auth.email-not-verified:"
              + " o correo da sesión está sen verificar;"
              + " staff.invitation-platform-account: a conta é da administración da plataforma;"
              + " staff.invitation-own: quen acepta fixo a invitación;"
              + " staff.invitation-personal-account: a conta xa reservou como veciña nalgún"
              + " concello (a de traballo vai á parte)",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "404",
      description = INVALID,
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "409",
      description = "staff.already-member: a persoa xa é persoal activo do concello",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "410",
      description = EXPIRED,
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  AcceptedStaffInvitationResponse accept(
      @PathVariable String slug, @Valid @RequestBody AcceptStaffInvitationRequest request) {
    return new AcceptedStaffInvitationResponse(
        StaffRoleValue.valueOf(
            invitations.accept(new AcceptStaffInvitationCommand(request.token())).name()));
  }
}
