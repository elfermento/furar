// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.testsupport;

import gal.furar.shared.config.FurarProperties;
import gal.furar.shared.security.config.JwtValidators;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Decodificador JWT dos tests: clave RSA xerada ao arrincar e os mesmos validadores de produción
 * (emisor, audiencia, caducidade). Os tokens fainos {@link TestTokens}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwtConfig {

  static final KeyPair KEYS = generate();

  @Bean
  JwtDecoder jwtDecoder(FurarProperties properties) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
    decoder.setJwtValidator(JwtValidators.of(properties));
    return decoder;
  }

  private static KeyPair generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
