// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.in.web;

import gal.furar.people.adapter.in.web.dto.AccountDeletionBlockerResponse;
import gal.furar.people.adapter.in.web.dto.AccountDeletionResponse;
import gal.furar.people.application.port.in.AccountDeletionUseCase;
import gal.furar.people.application.port.in.AccountDeletionView;
import gal.furar.shared.domain.Language;
import gal.furar.shared.web.Locales;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Se se pode eliminar a conta da sesión (ADR-0043). A web pregúntao antes de levar a persoa a
 * Keycloak; se hai algo pendente, lístao coas ligazóns. Fóra de {@code /municipalities/{slug}}: a
 * conta é común a todos os concellos. Controlador fino: o permiso vive no caso de uso.
 */
@RestController
@RequestMapping("/api/v1/me/account-deletion")
@Tag(name = "people", description = "Persoas a cargo")
class MyAccountDeletionController {

  private final AccountDeletionUseCase deletion;

  MyAccountDeletionController(AccountDeletionUseCase deletion) {
    this.deletion = deletion;
  }

  @GetMapping
  @Operation(
      operationId = "getMyAccountDeletion",
      summary = "Se se pode eliminar a conta e, se non, que hai que cancelar antes")
  AccountDeletionResponse mine(HttpServletRequest request) {
    Language language = Locales.language(request);
    List<AccountDeletionBlockerResponse> blockers =
        deletion.mine().stream().map(v -> response(v, language)).toList();
    return new AccountDeletionResponse(blockers.isEmpty(), blockers);
  }

  static AccountDeletionBlockerResponse response(AccountDeletionView view, Language language) {
    return new AccountDeletionBlockerResponse(
        AccountDeletionBlockerResponse.Kind.valueOf(view.kind().name()),
        view.slug(),
        view.municipalityName(),
        view.eventId(),
        language == Language.ES ? view.eventNameEs() : view.eventNameGl(),
        view.count());
  }
}
