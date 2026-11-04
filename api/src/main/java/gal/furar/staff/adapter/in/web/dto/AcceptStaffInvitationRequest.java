// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Aceptar a invitación.
 *
 * @param token o token da ligazón
 */
public record AcceptStaffInvitationRequest(
    @NotNull @Schema(description = "Token da ligazón (43 caracteres base64url)") String token) {

  /** Sen o token: este record nunca debe levalo a un rexistro. */
  @Override
  public String toString() {
    return "AcceptStaffInvitationRequest[token=redacted]";
  }
}
