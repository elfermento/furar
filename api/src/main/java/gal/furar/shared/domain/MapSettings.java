// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Mapa do despregue (ADR-0023, revisión do 2026-11-03). Sae da configuración ({@code FURAR_MAP},
 * {@code FURAR_MAP_ATTRIBUTION}, {@code FURAR_MAP_ATTRIBUTION_URL}); as teselas sérveas Caddy en
 * {@code /tiles}. Un valor que non vale impide arrincar.
 *
 * @param enabled se a web mostra mapas; sen mapa, só o enderezo e as coordenadas
 * @param attribution recoñecemento que vai sempre á vista ao pé do mapa (por defecto, o do Mapa
 *     base do IGN: «CC BY 4.0 scne.es»)
 * @param attributionUrl ligazón do recoñecemento; baleira se o servidor de teselas non ten
 */
public record MapSettings(boolean enabled, String attribution, Optional<URI> attributionUrl) {

  public MapSettings {
    Objects.requireNonNull(attributionUrl, "attributionUrl");
    attribution = attribution == null ? "" : attribution.strip();
    if (enabled && attribution.isEmpty()) {
      throw new IllegalArgumentException(
          "FURAR_MAP_ATTRIBUTION is required while the map is on (ADR-0023)");
    }
  }

  /**
   * Desde os valores de contorno.
   *
   * @param mode {@code on} (por defecto, tamén se vai baleiro) ou {@code off}, sen maiúsculas
   * @param attribution texto do recoñecemento
   * @param attributionUrl {@code https://…} ou {@code http://…}; baleiro = sen ligazón
   */
  public static MapSettings parse(String mode, String attribution, String attributionUrl) {
    return new MapSettings(enabled(mode), attribution, url(attributionUrl));
  }

  private static boolean enabled(String mode) {
    String value = mode == null ? "" : mode.strip().toLowerCase(Locale.ROOT);
    return switch (value) {
      case "", "on" -> true;
      case "off" -> false;
      default ->
          throw new IllegalArgumentException(
              "FURAR_MAP: '" + mode + "' is not on or off (ADR-0023)");
    };
  }

  private static Optional<URI> url(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    URI uri;
    try {
      uri = URI.create(value.strip());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "FURAR_MAP_ATTRIBUTION_URL: '" + value + "' is not a URL", e);
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("https") && !scheme.equals("http") || uri.getHost() == null) {
      throw new IllegalArgumentException(
          "FURAR_MAP_ATTRIBUTION_URL: use an absolute http(s) URL (ADR-0023)");
    }
    return Optional.of(uri);
  }
}
