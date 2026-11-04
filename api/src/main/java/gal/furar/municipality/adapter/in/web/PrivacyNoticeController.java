// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web;

import gal.furar.municipality.adapter.in.web.dto.PrivacyNoticeResponse;
import gal.furar.municipality.application.port.in.GetPrivacyNoticeUseCase;
import gal.furar.shared.audit.AuditRetention;
import gal.furar.shared.security.PublicEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Datos do despregue para a páxina de privacidade (RGPD art. 13): públicos e sen concello no URL. O
 * resto da cláusula vai cos datos legais de cada concello.
 */
@RestController
@Tag(name = "privacy", description = "Protección de datos")
class PrivacyNoticeController {

  private final GetPrivacyNoticeUseCase notice;

  PrivacyNoticeController(GetPrivacyNoticeUseCase notice) {
    this.notice = notice;
  }

  @GetMapping("/api/v1/privacy-notice")
  @PublicEndpoint
  @Operation(
      operationId = "getPrivacyNotice",
      summary = "Prazos do rexistro de actividade do despregue")
  PrivacyNoticeResponse get() {
    AuditRetention activity = notice.activityLog();
    return new PrivacyNoticeResponse(
        activity.visible().toString(), activity.retention().toString());
  }
}
