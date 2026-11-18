// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security.config;

import gal.furar.shared.config.FurarProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/**
 * Validación explícita do token: emisor, audiencia, cliente e caducidade (ADR-0007). O cliente
 * ({@code azp}, OIDC Core 2) ten que ser un dos de {@code furar.security.clients} (a web): a
 * audiencia {@code furar-api} vale para calquera cliente do realm que a engada, e só a web pasa
 * polo fluxo de acceso e os niveis de Furar.
 */
public final class JwtValidators {

  private JwtValidators() {}

  public static OAuth2TokenValidator<Jwt> of(FurarProperties properties) {
    String audience = properties.security().audience();
    OAuth2TokenValidator<Jwt> audienceValidator =
        jwt -> {
          List<String> aud = jwt.getAudience();
          return aud != null && aud.contains(audience)
              ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(
                  new OAuth2Error("invalid_token", "Missing audience " + audience, null));
        };
    List<String> clients = properties.security().clients();
    OAuth2TokenValidator<Jwt> clientValidator =
        jwt -> {
          Object azp = jwt.getClaims().get("azp");
          return azp instanceof String client && clients.contains(client)
              ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(
                  new OAuth2Error("invalid_token", "Unexpected authorized party (azp)", null));
        };
    OAuth2TokenValidator<Jwt> expiryRequired =
        jwt ->
            jwt.getExpiresAt() != null
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Missing exp", null));
    return new DelegatingOAuth2TokenValidator<>(
        new JwtTimestampValidator(Duration.ofSeconds(30)),
        expiryRequired,
        new JwtIssuerValidator(properties.issuer()),
        audienceValidator,
        clientValidator);
  }
}
