// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.config;

import gal.furar.shared.audit.AuditRetention;
import gal.furar.shared.domain.GeocoderSettings;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.LanguagePolicy;
import gal.furar.shared.domain.MapSettings;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({FurarProperties.class, AuditProperties.class})
class ConfigPropertiesConfig {

  /** {@code furar.languages.required}: un código descoñecido impide arrincar (ADR-0003). */
  @Bean
  LanguagePolicy languagePolicy(FurarProperties properties) {
    return new LanguagePolicy(
        properties.languages().required().stream()
            .map(String::strip)
            .filter(code -> !code.isEmpty())
            .map(
                code ->
                    Language.fromCode(code)
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "furar.languages.required: unknown language '" + code + "'")))
            .collect(Collectors.toSet()));
  }

  /** {@code furar.audit.visible} e {@code furar.audit.retention}: os prazos do rexistro. */
  @Bean
  AuditRetention auditRetention(AuditProperties properties) {
    return properties.policy();
  }

  /**
   * {@code furar.geocoder.provider} e {@code furar.http.proxy}: un valor que non vale non arrinca.
   */
  @Bean
  GeocoderSettings geocoderSettings(FurarProperties properties) {
    return new GeocoderSettings(
        GeocoderSettings.Provider.parse(properties.geocoder().provider()),
        GeocoderSettings.HttpProxy.parse(properties.http().proxy()));
  }

  /** {@code furar.base-map.*}: o mapa da web (ADR-0023); un valor que non vale non arrinca. */
  @Bean
  MapSettings mapSettings(FurarProperties properties) {
    FurarProperties.BaseMap map = properties.baseMap();
    return MapSettings.parse(map.mode(), map.attribution(), map.attributionUrl());
  }
}
