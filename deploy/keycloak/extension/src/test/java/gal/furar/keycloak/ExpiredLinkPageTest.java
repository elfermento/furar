// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Os URL dos botóns da ligazón caducada. Que Keycloak os acepte (PKCE, a volta do cliente) próbase
 * en D51.
 */
class ExpiredLinkPageTest {

  static final String REALM = "https://reservas.example.org/auth/realms/furar";
  static final String WEB = "https://reservas.example.org/";
  static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

  static Map<String, String> query(String url) {
    Map<String, String> params = new LinkedHashMap<>();
    for (String pair : URI.create(url).getRawQuery().split("&")) {
      String[] kv = pair.split("=", 2);
      params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
    }
    return params;
  }

  @Test
  void loginGoesToTheAuthorizationEndpointOfTheWebClientWithPkce() {
    String url = ExpiredLinkPage.loginUrl(REALM, WEB, "es", new SecureRandom());
    assertTrue(url.startsWith(REALM + "/protocol/openid-connect/auth?"), url);
    Map<String, String> q = query(url);
    assertEquals("furar-web", q.get("client_id"));
    assertEquals(WEB, q.get("redirect_uri"));
    assertEquals("code", q.get("response_type"));
    assertEquals("openid", q.get("scope"));
    assertEquals("S256", q.get("code_challenge_method"));
    assertEquals("es", q.get("ui_locales"));
    for (String key : new String[] {"state", "nonce", "code_challenge"}) {
      assertTrue(TOKEN.matcher(q.get(key)).matches(), key + "=" + q.get(key));
    }
    assertNotEquals(q.get("state"), q.get("code_challenge"));
  }

  @Test
  void eachVisitHasItsOwnState() {
    SecureRandom random = new SecureRandom();
    assertNotEquals(
        query(ExpiredLinkPage.loginUrl(REALM, WEB, "gl", random)).get("state"),
        query(ExpiredLinkPage.loginUrl(REALM, WEB, "gl", random)).get("state"));
  }

  @Test
  void resetGoesToForgotPasswordWithTheSameClientAndReturn() {
    String url = ExpiredLinkPage.resetUrl(REALM, WEB, "gl");
    assertTrue(url.startsWith(REALM + "/login-actions/reset-credentials?"), url);
    assertEquals(
        Map.of("client_id", "furar-web", "redirect_uri", WEB, "kc_locale", "gl"), query(url));
  }

  @Test
  void parametersAreEncoded() {
    String url = ExpiredLinkPage.resetUrl(REALM, "https://x.example.org/a b?c=d&e", "gl");
    assertTrue(url.contains("redirect_uri=https%3A%2F%2Fx.example.org%2Fa+b%3Fc%3Dd%26e"), url);
    assertEquals("https://x.example.org/a b?c=d&e", query(url).get("redirect_uri"));
  }
}
