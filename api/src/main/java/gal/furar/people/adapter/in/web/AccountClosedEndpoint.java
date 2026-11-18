// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.in.web;

import gal.furar.people.adapter.in.web.dto.AccountClosedResponse;
import gal.furar.people.application.port.in.AccountClosureUseCase;
import gal.furar.shared.security.InternalCallKey;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;

/**
 * Keycloak avisa aquí de que eliminou unha conta (ADR-0043): {@code POST /actuator/accountclosed}
 * co JSON {@code {"key", "subject"}} → {@code {"closed": true}}. A API borra e desliga os seus
 * datos ({@code AccountClosureService}). Idempotente: Keycloak volve avisar ata que responde 200.
 *
 * <ul>
 *   <li><b>Só no porto interno</b> de Actuator (9081), como {@code secondfactor}: Caddy non o
 *       publica. Non vai no contrato OpenAPI nin na web.
 *   <li><b>Coa clave</b> do propósito {@value InternalCallKey#ACCOUNT_CLOSURE}: sen ela, 403 sen
 *       corpo e sen tocar nada.
 *   <li><b>Sen rexistros</b> do {@code sub}: o rexistro de actividade de cada concello leva só as
 *       cifras.
 * </ul>
 */
@Component
@WebEndpoint(id = AccountClosedEndpoint.ID)
public class AccountClosedEndpoint {

  /** Id de Actuator (ruta e lista de {@code management.endpoints.web.exposure.include}). */
  public static final String ID = "accountclosed";

  private static final int FORBIDDEN = 403;

  private final AccountClosureUseCase closure;
  private final InternalCallKey key;

  public AccountClosedEndpoint(
      AccountClosureUseCase closure,
      @Value("${furar.invitation.proof-secret}") String sharedSecret) {
    this.closure = closure;
    this.key = InternalCallKey.derive(sharedSecret, InternalCallKey.ACCOUNT_CLOSURE);
  }

  @WriteOperation
  public WebEndpointResponse<AccountClosedResponse> closed(
      @Nullable String key, @Nullable String subject) {
    if (!this.key.matches(key)) {
      return new WebEndpointResponse<>(FORBIDDEN);
    }
    if (!validSubject(subject)) {
      return new WebEndpointResponse<>(WebEndpointResponse.STATUS_BAD_REQUEST);
    }
    closure.closed(subject);
    return new WebEndpointResponse<>(new AccountClosedResponse(true));
  }

  /**
   * Un {@code sub} que se pode buscar: non baleiro, ata 255 caracteres e nunca unha conta de
   * sistema ({@code furar:…}).
   */
  static boolean validSubject(@Nullable String subject) {
    return subject != null
        && !subject.isBlank()
        && subject.length() <= 255
        && !subject.startsWith("furar:");
  }
}
