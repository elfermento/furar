// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web;

import gal.furar.shared.security.InternalCallKey;
import gal.furar.staff.adapter.in.web.dto.SecondFactorResponse;
import gal.furar.staff.application.port.in.SecondFactorUseCase;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;

/**
 * Keycloak pregunta aquí se unha conta ten que entrar co segundo factor (ADR-0029): {@code POST
 * /actuator/secondfactor} co JSON {@code {"key", "subject", "email"}} → {@code {"required"}}.
 *
 * <ul>
 *   <li><b>Só no porto interno</b> de Actuator (9081): Caddy non o publica nunca e na rede de
 *       Compose só chegan Keycloak e os demais servizos. Non vai no contrato OpenAPI nin na web.
 *   <li><b>Coa clave</b> derivada do segredo compartido ({@link InternalCallKey}), comparada en
 *       tempo constante: sen ela, 403 sen corpo e sen mirar a base.
 *   <li><b>Sen rexistros</b> do {@code sub} nin do correo.
 * </ul>
 */
@Component
@WebEndpoint(id = SecondFactorEndpoint.ID)
public class SecondFactorEndpoint {

  /** Id de Actuator (ruta e lista de {@code management.endpoints.web.exposure.include}). */
  public static final String ID = "secondfactor";

  private static final int FORBIDDEN = 403;

  private final SecondFactorUseCase secondFactor;
  private final InternalCallKey key;

  public SecondFactorEndpoint(
      SecondFactorUseCase secondFactor,
      @Value("${furar.invitation.proof-secret}") String sharedSecret) {
    this.secondFactor = secondFactor;
    this.key = InternalCallKey.derive(sharedSecret, InternalCallKey.SECOND_FACTOR);
  }

  @WriteOperation
  public WebEndpointResponse<SecondFactorResponse> required(
      @Nullable String key, @Nullable String subject, @Nullable String email) {
    if (!this.key.matches(key)) {
      return new WebEndpointResponse<>(FORBIDDEN);
    }
    if (subject == null || subject.isBlank() || subject.length() > 255) {
      return new WebEndpointResponse<>(WebEndpointResponse.STATUS_BAD_REQUEST);
    }
    return new WebEndpointResponse<>(
        new SecondFactorResponse(secondFactor.required(subject, email)));
  }
}
