// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.geocoder;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import gal.furar.facility.application.port.out.AddressSearch;
import gal.furar.facility.domain.SearchArea;
import gal.furar.shared.domain.GeocoderSettings;
import gal.furar.shared.domain.GeocoderSettings.HttpProxy;
import gal.furar.shared.domain.GeocoderSettings.Provider;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Montaxe do buscador: {@code FURAR_GEOCODER} escolle o adaptador e {@code FURAR_HTTP_PROXY} chega
 * ao {@code RestClient}. O proxy de proba é un servidor HTTP local: ningunha petición sae á rede.
 */
class GeocoderConfigTest {

  HttpServer proxy;

  @AfterEach
  void stop() {
    if (proxy != null) {
      proxy.stop(0);
    }
  }

  @Test
  void fixedNeedsNoNetwork() {
    AddressSearch search =
        new GeocoderConfig()
            .addressSearch(
                new GeocoderSettings(Provider.FIXED, Optional.empty()),
                JsonMapper.builder().build(),
                Clock.systemUTC());

    assertThat(search).isInstanceOf(FixedAddressSearch.class);
  }

  @Test
  void cartociudadIsTheDefault() throws Exception {
    AddressSearch search =
        new GeocoderConfig()
            .addressSearch(
                new GeocoderSettings(Provider.parse(""), Optional.empty()),
                JsonMapper.builder().build(),
                Clock.systemUTC());

    assertThat(search).isInstanceOf(CartoCiudadAddressSearch.class);
    ((CartoCiudadAddressSearch) search).close();
  }

  @Test
  void withoutProxyTheClientGoesDirectWithTheTimeouts() {
    try (HttpClient client =
        GeocoderConfig.httpClient(new GeocoderSettings(Provider.CARTOCIUDAD, Optional.empty()))) {
      assertThat(client.proxy()).isEmpty();
      assertThat(client.connectTimeout()).contains(GeocoderConfig.CONNECT_TIMEOUT);
      assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
    }
  }

  @Test
  void theRestClientGoesThroughTheProxy() throws Exception {
    List<String> seen = new CopyOnWriteArrayList<>();
    proxy = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    proxy.createContext(
        "/",
        exchange -> {
          seen.add(exchange.getRequestURI().toString());
          seen.add("UA " + exchange.getRequestHeaders().getFirst("User-Agent"));
          byte[] body =
              "[{\"id\":\"1\",\"type\":\"portal\",\"address\":\"RUA REAL 10, Ferrol\",\"muni\":\"Ferrol\",\"muniCode\":\"15036\",\"portalNumber\":10,\"tip_via\":\"RUA\"}]"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    proxy.start();
    GeocoderSettings settings =
        new GeocoderSettings(
            Provider.CARTOCIUDAD,
            Optional.of(new HttpProxy("127.0.0.1", proxy.getAddress().getPort())));

    try (HttpClient client = GeocoderConfig.httpClient(settings)) {
      assertThat(client.proxy()).isPresent();
      CartoCiudadAddressSearch search =
          new CartoCiudadAddressSearch(
              GeocoderConfig.restClient(client, "http://cartociudad.invalid/geocoder/api/geocoder")
                  .build(),
              JsonMapper.builder().build(),
              Clock.systemUTC(),
              null);

      var found = search.suggest("rua real", new SearchArea("Ferrol", "15036"));

      assertThat(found).hasSize(1);
    }
    // O proxy recibe a URL enteira do destino (cartociudad.invalid non existe: só o proxy
    // responde).
    assertThat(seen).hasSize(2);
    assertThat(URI.create(seen.get(0)).getHost()).isEqualTo("cartociudad.invalid");
    assertThat(seen.get(0)).contains("/geocoder/api/geocoder/candidates?q=rua%20real");
    assertThat(seen.get(1)).isEqualTo("UA Furar");
  }
}
