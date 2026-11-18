// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.persistence;

import gal.furar.shared.tenancy.TenantIdentifierResolver;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.Jackson3JsonFormatMapper;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Hibernate: concello por sesión e JSON con decimais exactos (ADR-0006, ADR-0010). */
@Configuration(proxyBeanMethods = false)
class PersistenceConfig {

  @Bean
  HibernatePropertiesCustomizer furarHibernate() {
    JsonMapper json =
        JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    return properties -> {
      properties.put(
          AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, new TenantIdentifierResolver());
      properties.put(AvailableSettings.JSON_FORMAT_MAPPER, new Jackson3JsonFormatMapper(json));
    };
  }
}
