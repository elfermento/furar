// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Buscador de enderezos do despregue e saída a Internet (ADR-0023). Sae da configuración ({@code
 * FURAR_GEOCODER}, {@code FURAR_HTTP_PROXY}); un valor que non vale impide arrincar.
 *
 * @param provider buscador: CartoCiudad (por defecto) ou os enderezos gardados
 * @param proxy proxy saínte HTTP; baleiro = saída directa
 */
public record GeocoderSettings(Provider provider, Optional<HttpProxy> proxy) {

  public GeocoderSettings {
    Objects.requireNonNull(provider, "provider");
    Objects.requireNonNull(proxy, "proxy");
  }

  /** Buscador. */
  public enum Provider {
    /** www.cartociudad.es (IGN) desde o servidor. */
    CARTOCIUDAD,
    /** Enderezos gardados de Narón, Ferrol e a comarca de Verín, sen rede (tests e e2e). */
    FIXED;

    /** {@code cartociudad} ou {@code fixed}, sen maiúsculas; baleiro = CartoCiudad. */
    public static Provider parse(String value) {
      String code = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
      return switch (code) {
        case "", "cartociudad" -> CARTOCIUDAD;
        case "fixed" -> FIXED;
        default ->
            throw new IllegalArgumentException(
                "FURAR_GEOCODER: '" + value + "' is not cartociudad or fixed (ADR-0023)");
      };
    }
  }

  /**
   * Proxy saínte.
   *
   * @param host nome ou IP
   * @param port porto
   */
  public record HttpProxy(String host, int port) {

    public HttpProxy {
      if (host == null || host.isBlank()) {
        throw new IllegalArgumentException("FURAR_HTTP_PROXY: host is required");
      }
      if (port < 1 || port > 65535) {
        throw new IllegalArgumentException("FURAR_HTTP_PROXY: port out of range");
      }
    }

    /**
     * {@code http://host:porto} (tamén sen esquema); baleiro = sen proxy. Sen usuario nin
     * contrasinal: un proxy con autenticación non se admite (fallaría ao arrincar, non en
     * silencio).
     */
    public static Optional<HttpProxy> parse(String value) {
      if (value == null || value.isBlank()) {
        return Optional.empty();
      }
      String text = value.strip();
      URI uri;
      try {
        uri = URI.create(text.contains("://") ? text : "http://" + text);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("FURAR_HTTP_PROXY: '" + value + "' is not a URL", e);
      }
      if (!"http".equalsIgnoreCase(uri.getScheme())) {
        throw new IllegalArgumentException("FURAR_HTTP_PROXY: only http://host:port is supported");
      }
      if (uri.getUserInfo() != null) {
        throw new IllegalArgumentException("FURAR_HTTP_PROXY: credentials are not supported");
      }
      if (uri.getHost() == null || uri.getPort() == -1) {
        throw new IllegalArgumentException("FURAR_HTTP_PROXY: use http://host:port");
      }
      String path = uri.getRawPath();
      if ((path != null && !path.isEmpty() && !"/".equals(path)) || uri.getRawQuery() != null) {
        throw new IllegalArgumentException("FURAR_HTTP_PROXY: use http://host:port");
      }
      return Optional.of(new HttpProxy(uri.getHost(), uri.getPort()));
    }
  }
}
