// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import gal.furar.shared.tenancy.TenantSlugResolver;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import tools.jackson.databind.DeserializationFeature;

/** Configuración web común: idiomas, textos, JSON, reloxo e filtro de concello. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class WebConfig {

  /**
   * Textos de erro en galego e castelán: os comúns ({@code messages_gl/es.properties}) e os de cada
   * módulo ({@code messages/<modulo>_gl/es.properties}), atopados no classpath sen lista central
   * (ADR-0005 ARQ3-2).
   */
  /** {@code messages} e un {@code messages/<modulo>} por cada {@code messages/*_gl.properties}. */
  public static List<String> messageBasenames() throws IOException {
    var basenames = new TreeSet<String>();
    for (var resource :
        new PathMatchingResourcePatternResolver()
            .getResources("classpath*:messages/*_gl.properties")) {
      String name = Objects.requireNonNull(resource.getFilename());
      basenames.add("messages/" + name.substring(0, name.length() - "_gl.properties".length()));
    }
    var all = new ArrayList<String>();
    all.add("messages");
    all.addAll(basenames);
    return all;
  }

  @Bean
  MessageSource messageSource() throws IOException {
    var source = new ResourceBundleMessageSource();
    source.setBasenames(messageBasenames().toArray(String[]::new));
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    source.setDefaultLocale(Locales.GALICIAN);
    return source;
  }

  @Bean
  LocaleResolver localeResolver() {
    var resolver = new AcceptHeaderLocaleResolver();
    resolver.setSupportedLocales(List.of(Locales.GALICIAN, Locales.SPANISH));
    resolver.setDefaultLocale(Locales.GALICIAN);
    return resolver;
  }

  /** Decimais exactos ({@code BigDecimal}) e propiedades descoñecidas rexeitadas. */
  @Bean
  JsonMapperBuilderCustomizer furarJson() {
    return builder ->
        builder
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /** Identificador de petición, o primeiro de todos (rexistros correlacionables). */
  @Bean
  FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
    var registration = new FilterRegistrationBean<>(new RequestIdFilter());
    registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  /**
   * Límite por IP (decisión do 2026-11-04), despois do identificador de petición e antes do resto:
   * unha inundación nin sequera chega a Spring Security. {@code furar.rate-limit.*} a 0 quítao.
   */
  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
      ProblemWriter problems,
      Clock clock,
      @Value("${furar.rate-limit.requests-per-minute:3000}") int requests,
      @Value("${furar.rate-limit.writes-per-minute:300}") int writes) {
    var registration =
        new FilterRegistrationBean<>(new RateLimitFilter(problems, clock, requests, writes));
    registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10);
    registration.addUrlPatterns("/api/*");
    return registration;
  }

  /** Filtro de concello antes de Spring Security: un slug descoñecido é 404 para todos. */
  @Bean
  FilterRegistrationBean<TenantFilter> tenantFilter(
      TenantSlugResolver resolver, ProblemWriter problems) {
    var registration = new FilterRegistrationBean<>(new TenantFilter(resolver, problems));
    registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10);
    registration.addUrlPatterns("/api/v1/municipalities/*");
    return registration;
  }
}
