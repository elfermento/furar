// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security.config;

import gal.furar.shared.config.FurarProperties;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.shared.tenancy.TenantSlugResolver;
import gal.furar.shared.web.ErrorCatalog;
import gal.furar.shared.web.ProblemWriter;
import gal.furar.shared.web.WebConfig;
import gal.furar.testsupport.TestJwtConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Configuración real de seguridade, erros e filtro de concello para os {@code @WebMvcTest}: só se
 * substitúen os casos de uso. Concellos coñecidos: {@code naron} e {@code ferrol}.
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(FurarProperties.class)
@Import({
  SecurityConfig.class,
  JwtCurrentActor.class,
  ProblemWriter.class,
  ErrorCatalog.class,
  WebConfig.class,
  TestJwtConfig.class
})
public class WebTestConfig {

  public static final UUID NARON = UUID.fromString("019a0b6e-0000-7000-8000-000000000001");
  public static final UUID FERROL = UUID.fromString("019a0b6e-0000-7000-8000-000000000002");

  @Bean
  TenantSlugResolver tenantSlugResolver() {
    return slug ->
        switch (slug) {
          case "naron" -> Optional.of(new TenantRef(NARON, slug));
          case "ferrol" -> Optional.of(new TenantRef(FERROL, slug));
          default -> Optional.empty();
        };
  }
}
