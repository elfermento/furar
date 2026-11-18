// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.in.web;

import gal.furar.notification.adapter.in.web.dto.MunicipalityJoinRequest;
import gal.furar.notification.application.port.in.SubmitJoinRequestUseCase;
import gal.furar.shared.security.PublicEndpoint;
import gal.furar.shared.web.Locales;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * «¿Es un concello?» (ADR-0042): sen concello no URL nin sesión. Controlador fino: o idioma do
 * correo sae de {@code Accept-Language} e o resto vive no caso de uso.
 */
@RestController
@Tag(name = "deployment", description = "Despregue")
class MunicipalityJoinRequestController {

  private static final String PROBLEM = "#/components/schemas/Problem";

  private final SubmitJoinRequestUseCase joinRequests;

  MunicipalityJoinRequestController(SubmitJoinRequestUseCase joinRequests) {
    this.joinRequests = joinRequests;
  }

  @PostMapping("/api/v1/municipality-join-requests")
  @PublicEndpoint
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
      operationId = "submitMunicipalityJoinRequest",
      summary =
          "Solicitude de alta dun concello: vai por correo a quen opera o servizo, no idioma de"
              + " Accept-Language e con Reply-To ao correo de quen a pide; se o correo non sae,"
              + " gárdase e reinténtase. Sen a función no despregue, 404")
  @ApiResponse(responseCode = "202", description = "Enviada ou gardada para reintentala")
  @ApiResponse(
      responseCode = "404",
      description = "common.not-found",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "429",
      description = "notification.join-request-rate-limited",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  @ApiResponse(
      responseCode = "503",
      description = "notification.join-request-not-sent",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  void submit(@Valid @RequestBody MunicipalityJoinRequest request, HttpServletRequest http) {
    joinRequests.submit(
        new SubmitJoinRequestUseCase.Submission(
            request.municipality(),
            request.name(),
            request.role(),
            request.email(),
            request.phone(),
            request.message(),
            request.website(),
            Locales.language(http),
            String.valueOf(http.getRemoteAddr())));
  }
}
