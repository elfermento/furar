// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.in.web.dto;

import gal.furar.notification.domain.JoinRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Solicitude de alta dun concello (ADR-0042). Os límites son os de {@link JoinRequest}.
 *
 * @param website campo trampa: a web escóndeo ás persoas e vai sempre baleiro
 */
public record MunicipalityJoinRequest(
    @Schema(description = "Concello ou entidade", example = "Concello de Exemplo")
        @NotBlank
        @Size(max = JoinRequest.MUNICIPALITY_MAX)
        String municipality,
    @Schema(description = "Nome e apelidos de quen a pide")
        @NotBlank
        @Size(max = JoinRequest.NAME_MAX)
        String name,
    @Schema(description = "Cargo") @Nullable @Size(max = JoinRequest.ROLE_MAX) String role,
    @Schema(description = "Correo ao que contestar", example = "nome@example.org")
        @NotBlank
        @Size(min = JoinRequest.EMAIL_MIN, max = JoinRequest.EMAIL_MAX)
        @Pattern(regexp = JoinRequest.EMAIL_PATTERN)
        String email,
    @Schema(description = "Teléfono")
        @NotBlank
        @Size(min = JoinRequest.PHONE_MIN, max = JoinRequest.PHONE_MAX)
        @Pattern(regexp = JoinRequest.PHONE_PATTERN)
        String phone,
    @Schema(description = "Mensaxe") @Nullable @Size(max = JoinRequest.MESSAGE_MAX) String message,
    @Schema(description = "Campo trampa contra programas: sempre baleiro")
        @Nullable
        @Size(max = 500)
        String website) {

  @Override
  public String toString() {
    return "MunicipalityJoinRequest[municipality=" + municipality + "]";
  }
}
