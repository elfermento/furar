// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * O token da ligazón da invitación, que a web le do fragmento (nunca no URL da API).
 *
 * @param token 43 caracteres base64url
 */
public record PreviewStaffInvitationRequest(
    @NotNull @Schema(description = "Token da ligazón (43 caracteres base64url)") String token) {

  /** Sen o token: este record nunca debe levalo a un rexistro. */
  @Override
  public String toString() {
    return "PreviewStaffInvitationRequest[token=redacted]";
  }
}
