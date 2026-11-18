// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.in.web;

import gal.furar.people.adapter.in.web.dto.AccountDeletionAllowedResponse;
import gal.furar.people.application.port.in.AccountDeletionUseCase;
import gal.furar.shared.security.InternalCallKey;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;

/**
 * Keycloak pregunta aquí se pode eliminar unha conta (ADR-0043), antes de mostrar a confirmación e
 * antes de borrala: {@code POST /actuator/accountdeletion} co JSON {@code {"key", "subject"}} →
 * {@code {"allowed"}}.
 *
 * <ul>
 *   <li><b>Só no porto interno</b> de Actuator (9081), como {@code secondfactor}: Caddy non o
 *       publica. Non vai no contrato OpenAPI nin na web.
 *   <li><b>Coa clave</b> do propósito {@value InternalCallKey#ACCOUNT_CLOSURE}, comparada en tempo
 *       constante: sen ela, 403 sen corpo e sen mirar a base.
 *   <li><b>Sen rexistros</b> do {@code sub}.
 * </ul>
 */
@Component
@WebEndpoint(id = AccountDeletionEndpoint.ID)
public class AccountDeletionEndpoint {

  /** Id de Actuator (ruta e lista de {@code management.endpoints.web.exposure.include}). */
  public static final String ID = "accountdeletion";

  private static final int FORBIDDEN = 403;

  private final AccountDeletionUseCase deletion;
  private final InternalCallKey key;

  public AccountDeletionEndpoint(
      AccountDeletionUseCase deletion,
      @Value("${furar.invitation.proof-secret}") String sharedSecret) {
    this.deletion = deletion;
    this.key = InternalCallKey.derive(sharedSecret, InternalCallKey.ACCOUNT_CLOSURE);
  }

  @WriteOperation
  public WebEndpointResponse<AccountDeletionAllowedResponse> allowed(
      @Nullable String key, @Nullable String subject) {
    if (!this.key.matches(key)) {
      return new WebEndpointResponse<>(FORBIDDEN);
    }
    if (!AccountClosedEndpoint.validSubject(subject)) {
      return new WebEndpointResponse<>(WebEndpointResponse.STATUS_BAD_REQUEST);
    }
    return new WebEndpointResponse<>(new AccountDeletionAllowedResponse(deletion.allowed(subject)));
  }
}
