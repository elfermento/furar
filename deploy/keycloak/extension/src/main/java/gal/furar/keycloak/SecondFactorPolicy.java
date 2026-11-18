// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Pregúntalle á API de Furar se unha conta ten que entrar co segundo factor (ADR-0029): o persoal
 * de calquera concello e a administración da plataforma, si; a veciñanza, non. O dato vive na base
 * de Furar, como os permisos (ADR-0027, ADR-0028), así que Keycloak pregunta en cada acceso.
 *
 * <ul>
 *   <li><b>Onde:</b> {@code POST <FURAR_API_INTERNAL_URL>/actuator/secondfactor}, o porto interno
 *       da API (por defecto {@code http://api:9081}, na rede de Compose; Caddy non o publica).
 *   <li><b>Clave:</b> {@code base64url(HMAC-SHA256(FURAR_INVITATION_PROOF_SECRET,
 *       "furar-second-factor-v1"))}, igual ca {@code gal.furar.shared.security.InternalCallKey} da
 *       API. O segredo non sae nunca.
 *   <li><b>Sen resposta</b> (API parada, tempo esgotado, clave mala, resposta rara):{@link
 *       Optional#empty()}. Quen chama decide; o acceso nunca o toma por un non.
 * </ul>
 *
 * Sen dependencias de Keycloak, para probala soa.
 */
public final class SecondFactorPolicy {

  /** Variable co URL interno da API (sen barra ao final). */
  static final String URL_VARIABLE = "FURAR_API_INTERNAL_URL";

  /** Por defecto: o servizo {@code api} de Compose, porto de Actuator. */
  static final String DEFAULT_URL = "http://api:9081";

  static final String PATH = "/actuator/secondfactor";
  static final String PURPOSE = "furar-second-factor-v1";

  private static final Duration TIMEOUT = Duration.ofSeconds(3);
  private static final Pattern REQUIRED =
      Pattern.compile("^\\s*\\{\\s*\"required\"\\s*:\\s*(true|false)\\s*}\\s*$");

  private final URI endpoint;
  private final String key;
  private final HttpClient http;

  SecondFactorPolicy(String baseUrl, String key) {
    String base = baseUrl.strip();
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    this.endpoint = URI.create(base + PATH);
    this.key = key;
    this.http =
        HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  /**
   * A política co contorno de Keycloak, ou baleira se falta o segredo ou é curto (entón ninguén
   * sabe se é persoal: quen chama decide como se sen resposta).
   */
  static Optional<SecondFactorPolicy> fromEnvironment() {
    String secret = System.getenv(InvitationEmailVerification.SECRET_VARIABLE);
    if (secret == null || secret.strip().length() < InvitationProof.MIN_SECRET_LENGTH) {
      return Optional.empty();
    }
    String url = System.getenv(URL_VARIABLE);
    return Optional.of(
        new SecondFactorPolicy(url == null || url.isBlank() ? DEFAULT_URL : url, key(secret)));
  }

  /** A clave derivada do segredo compartido. */
  static String key(String secret) {
    return key(secret, PURPOSE);
  }

  /**
   * A clave dun propósito: {@code base64url(HMAC-SHA256(segredo, propósito))}, sen recheo (a mesma
   * fórmula ca {@code InternalCallKey} da API; cada chamada interna ten o seu propósito).
   */
  static String key(String secret, String purpose) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.strip().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(purpose.getBytes(StandardCharsets.US_ASCII)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 unavailable", e);
    }
  }

  /**
   * @param subject o id da conta (o {@code sub})
   * @param email o correo da conta, ou {@code null}
   * @return se ten que entrar co segundo factor; baleiro se a API non respondeu ben
   */
  public Optional<Boolean> required(String subject, String email) {
    String body =
        "{\"key\":"
            + json(key)
            + ",\"subject\":"
            + json(subject)
            + (email == null ? "" : ",\"email\":" + json(email))
            + "}";
    HttpRequest request =
        HttpRequest.newBuilder(endpoint)
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
    try {
      HttpResponse<String> response =
          http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return response.statusCode() == 200 ? parse(response.body()) : Optional.empty();
    } catch (IOException e) {
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  static Optional<Boolean> parse(String body) {
    Matcher m = REQUIRED.matcher(body == null ? "" : body);
    return m.matches() ? Optional.of(Boolean.parseBoolean(m.group(1))) : Optional.empty();
  }

  /** Cadea JSON entre comiñas. */
  static String json(String value) {
    StringBuilder out = new StringBuilder(value.length() + 2).append('"');
    for (char c : value.toCharArray()) {
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"').toString();
  }

  /** Onde pregunta (sen a clave). */
  URI endpoint() {
    return endpoint;
  }

  @Override
  public String toString() {
    return "SecondFactorPolicy[" + endpoint + ", key=***]";
  }
}
