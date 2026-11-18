// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.geocoder;

import gal.furar.facility.application.port.out.AddressSearch;
import gal.furar.shared.domain.GeocoderSettings;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escolle o buscador de enderezos segundo {@code FURAR_GEOCODER} e monta o cliente HTTP de
 * CartoCiudad (ADR-0023): conexión 2 s, lectura 3 s, sen redireccións nin reintentos e, se o hai, o
 * proxy de {@code FURAR_HTTP_PROXY}.
 */
@Configuration(proxyBeanMethods = false)
class GeocoderConfig {

  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  static final Duration READ_TIMEOUT = Duration.ofSeconds(3);
  static final String USER_AGENT = "Furar";

  @Bean
  AddressSearch addressSearch(GeocoderSettings settings, JsonMapper json, Clock clock) {
    return switch (settings.provider()) {
      case FIXED -> new FixedAddressSearch();
      case CARTOCIUDAD -> {
        HttpClient client = httpClient(settings);
        yield new CartoCiudadAddressSearch(
            restClient(client, CartoCiudadAddressSearch.BASE_URL).build(), json, clock, client);
      }
    };
  }

  /** Cliente HTTP do JDK cos tempos límite e o proxy saínte. */
  static HttpClient httpClient(GeocoderSettings settings) {
    HttpClient.Builder builder =
        HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER);
    settings
        .proxy()
        .ifPresent(
            proxy ->
                builder.proxy(
                    ProxySelector.of(
                        InetSocketAddress.createUnresolved(proxy.host(), proxy.port()))));
    return builder.build();
  }

  /** {@code RestClient} sobre ese cliente, co tempo de lectura e a URL base. */
  static RestClient.Builder restClient(HttpClient client, String baseUrl) {
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder()
        .requestFactory(factory)
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
  }
}
