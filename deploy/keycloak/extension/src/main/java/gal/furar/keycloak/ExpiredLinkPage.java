// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * A páxina da ligazón curta caducada ou descoñecida ({@link ShortLinkResource}): «A ligazón
 * caducou», «Pide outra desde aquí.» e dous botóns, cos URL que fai esta clase.
 *
 * <ul>
 *   <li><b>Entrar e reenviar o correo:</b> o acceso do realm co cliente da web ({@code furar-web}),
 *       que volve á raíz da web. Keycloak pide o correo e o contrasinal e, se a conta ten o correo
 *       sen verificar, mostra a verificación co botón de reenviar. O URL non depende de ningunha
 *       sesión: leva un {@code state}, un {@code nonce} e un {@code code_challenge} (S256, o que
 *       esixe o cliente) ao chou, que ninguén canxea. Ao volver, a web non coñece ese {@code
 *       state} e entra en silencio coa sesión que Keycloak xa ten (o mesmo camiño ca cando se
 *       verifica o correo noutra lapela).
 *   <li><b>Cambiar o contrasinal:</b> «Esqueciches o contrasinal?» de Keycloak ({@code
 *       login-actions/reset-credentials}) co mesmo cliente e a mesma volta: Keycloak abre o fluxo
 *       sen sesión previa.
 * </ul>
 *
 * Os dous no idioma da páxina ({@code ui_locales} e {@code kc_locale}). Sen dependencias de
 * Keycloak, para probala soa.
 */
final class ExpiredLinkPage {

  /** Modelo do tema (login). */
  static final String TEMPLATE = "furar-link-expired.ftl";

  /** Atributo do tema: URL de «Entrar e reenviar o correo». */
  static final String LOGIN_ATTRIBUTE = "furarLoginUrl";

  /** Atributo do tema: URL de «Cambiar o contrasinal». */
  static final String RESET_ATTRIBUTE = "furarResetUrl";

  /** Cliente da web (realm de Furar). */
  static final String CLIENT_ID = "furar-web";

  private static final int RANDOM_BYTES = 32;

  private ExpiredLinkPage() {}

  /**
   * Autorización do cliente cara á web.
   *
   * @param realmUrl {@code <orixe>/auth/realms/<realm>}, sen barra final
   * @param redirectUri a volta (unha das do cliente)
   * @param locale etiqueta do idioma da páxina ({@code gl}, {@code es})
   */
  static String loginUrl(
      String realmUrl, String redirectUri, String locale, RandomGenerator random) {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("client_id", CLIENT_ID);
    query.put("redirect_uri", redirectUri);
    query.put("response_type", "code");
    query.put("scope", "openid");
    query.put("state", token(random));
    query.put("nonce", token(random));
    query.put("code_challenge", token(random));
    query.put("code_challenge_method", "S256");
    query.put("ui_locales", locale);
    return realmUrl + "/protocol/openid-connect/auth?" + encode(query);
  }

  /** «Esqueciches o contrasinal?» co cliente da web, que volve a {@code redirectUri}. */
  static String resetUrl(String realmUrl, String redirectUri, String locale) {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("client_id", CLIENT_ID);
    query.put("redirect_uri", redirectUri);
    query.put("kc_locale", locale);
    return realmUrl + "/login-actions/reset-credentials?" + encode(query);
  }

  /** 32 bytes ao chou en base64url sen recheo: 43 caracteres, válido tamén como reto PKCE. */
  private static String token(RandomGenerator random) {
    byte[] bytes = new byte[RANDOM_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String encode(Map<String, String> query) {
    return query.entrySet().stream()
        .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }
}
