// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de Furar.
 *
 * @param publicUrl URL pública ({@code FURAR_PUBLIC_URL}), sen barra final
 * @param security validación de tokens
 * @param languages política de idiomas da instalación
 * @param geocoder buscador de enderezos ({@code FURAR_GEOCODER}, ADR-0023)
 * @param http saída a Internet da API ({@code FURAR_HTTP_PROXY}, ADR-0023)
 * @param baseMap mapa da web ({@code FURAR_MAP*}, ADR-0023)
 */
@ConfigurationProperties("furar")
public record FurarProperties(
    String publicUrl,
    Security security,
    @DefaultValue Languages languages,
    @DefaultValue Geocoder geocoder,
    @DefaultValue Http http,
    @DefaultValue BaseMap baseMap) {

  /**
   * @param provider {@code cartociudad} (por defecto, tamén se vai baleiro) ou {@code fixed}
   *     (enderezos gardados de Narón, Ferrol e a comarca de Verín, sen rede: tests e e2e). Non
   *     depende do perfil.
   */
  public record Geocoder(@DefaultValue("cartociudad") String provider) {}

  /**
   * @param proxy proxy saínte {@code http://host:porto}; baleiro = saída directa
   */
  public record Http(@DefaultValue("") String proxy) {}

  /**
   * Mapa da web. As teselas sérveas Caddy ({@code FURAR_MAP_TILES_*}); a API só di á web se hai
   * mapa e que recoñecemento leva.
   *
   * @param mode {@code on} (por defecto) ou {@code off} ({@code FURAR_MAP})
   * @param attribution recoñecemento á vista ao pé do mapa ({@code FURAR_MAP_ATTRIBUTION})
   * @param attributionUrl ligazón do recoñecemento ({@code FURAR_MAP_ATTRIBUTION_URL})
   */
  public record BaseMap(
      @DefaultValue("on") String mode,
      @DefaultValue("CC BY 4.0 scne.es") String attribution,
      @DefaultValue("https://www.scne.es") String attributionUrl) {}

  /**
   * @param required códigos dos idiomas que todo concello ten que ofrecer ({@code gl} en Galicia)
   */
  public record Languages(@DefaultValue("gl") List<String> required) {}

  /**
   * @param jwkSetUri chaves de Keycloak pola rede interna
   * @param realm realm de Keycloak
   * @param audience audiencia que debe levar o token
   * @param clients clientes OIDC dos que a API acepta tokens (claim {@code azp}): só a web ({@code
   *     furar-web}); a demo engade {@code furar-e2e}, o das probas automáticas. Un token que
   *     Keycloak emitise para outro cliente do realm co mesmo {@code aud} non vale
   */
  public record Security(
      String jwkSetUri,
      String realm,
      String audience,
      @DefaultValue("furar-web") List<String> clients) {}

  /** Emisor esperado: {@code ${FURAR_PUBLIC_URL}/auth/realms/<realm>}. */
  public String issuer() {
    return stripSlash(publicUrl) + "/auth/realms/" + security.realm();
  }

  private static String stripSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
