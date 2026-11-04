// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security.config;

import gal.furar.shared.config.FurarProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Chaves de Keycloak pola rede interna ({@code jwk-set-uri}) e emisor público comprobado á parte.
 * Sen {@code issuer-uri}: a API non depende de Keycloak para arrincar (ADR-0007). Caché das chaves:
 * a por defecto (5 min; un {@code kid} descoñecido forza a recarga), xustificada en ADR-0007. Nos
 * tests, {@code TestJwtConfig} usa unha clave RSA propia cos mesmos validadores.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
class JwtConfig {

  @Bean
  JwtDecoder jwtDecoder(FurarProperties properties) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(properties.security().jwkSetUri()).build();
    decoder.setJwtValidator(JwtValidators.of(properties));
    return decoder;
  }
}
