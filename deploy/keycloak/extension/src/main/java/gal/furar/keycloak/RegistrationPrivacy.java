// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * O parámetro {@code furar_privacy} do URL de acceso (ADR-0017, revisión do 2026-11-03): a web pon
 * nel o camiño da páxina «Privacidade» do concello desde o que se entra ({@code
 * /naron/privacidade}). A frase do rexistro liga a esa páxina, na mesma orixe ca o {@code
 * redirect_uri} da petición, que Keycloak xa validou contra os do cliente: o parámetro só elixe o
 * camiño, nunca o sitio. Un valor que non sexa un camiño simple non vale, e sen ligazón a frase
 * non sae.
 */
final class RegistrationPrivacy {

  /** Parámetro do URL de acceso. */
  static final String PARAMETER = "furar_privacy";

  /** Nota do cliente onde Keycloak garda o parámetro (AuthorizationEndpoint). */
  static final String CLIENT_NOTE = "client_request_param_" + PARAMETER;

  /** Atributo da páxina de rexistro que le o tema (register-commons.ftl). */
  static final String FORM_ATTRIBUTE = "furarPrivacyUrl";

  static final int MAX_LENGTH = 200;

  /** Segmentos sen puntos ao principio (nin {@code .} nin {@code ..}), sen consulta nin fragmento. */
  private static final Pattern PATH = Pattern.compile("^(/[A-Za-z0-9_~-][A-Za-z0-9._~-]*)+$");

  private RegistrationPrivacy() {}

  /**
   * A ligazón á páxina de privacidade: a orixe de {@code redirectUri} (http ou https) e o camiño de
   * {@code path}. Baleiro se falta algún dos dous ou non vale.
   */
  static Optional<String> link(String redirectUri, String path) {
    if (redirectUri == null || path == null || path.length() > MAX_LENGTH) {
      return Optional.empty();
    }
    if (!PATH.matcher(path).matches()) {
      return Optional.empty();
    }
    try {
      URI redirect = new URI(redirectUri);
      String scheme = redirect.getScheme();
      if (scheme == null
          || redirect.getRawAuthority() == null
          || redirect.getHost() == null
          || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
        return Optional.empty();
      }
      return Optional.of(
          scheme.toLowerCase(Locale.ROOT) + "://" + redirect.getRawAuthority() + path);
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
  }
}
