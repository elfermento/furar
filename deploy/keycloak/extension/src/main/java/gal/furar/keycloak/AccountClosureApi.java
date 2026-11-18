// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Eliminar a propia conta (ADR-0043): as dúas preguntas de Keycloak á API de Furar polo porto
 * interno de Actuator, como {@link SecondFactorPolicy}.
 *
 * <ul>
 *   <li><b>Pódese?</b> {@code POST <FURAR_API_INTERNAL_URL>/actuator/accountdeletion} → {@code
 *       {"allowed": true|false}}. Non se pode se a persoa ten reservas, partidas ou inscricións
 *       pendentes, ou é persoal ou administración da plataforma.
 *   <li><b>Xa se eliminou:</b> {@code POST …/actuator/accountclosed} → 200. A API borra e desliga
 *       os datos da conta; idempotente, así que se pode repetir.
 *   <li><b>Clave:</b> {@code base64url(HMAC-SHA256(FURAR_INVITATION_PROOF_SECRET,
 *       "furar-account-closure-v1"))}, distinta da do segundo factor. O segredo non sae nunca.
 *   <li><b>Sen resposta</b> (API parada, tempo esgotado, clave mala, resposta rara): {@link
 *       Optional#empty()} ou {@code false}. Quen chama decide; eliminar nunca o toma por un si.
 * </ul>
 *
 * Sen dependencias de Keycloak, para probala soa. Non rexistra o {@code sub}.
 */
public final class AccountClosureApi {

  static final String DELETION_PATH = "/actuator/accountdeletion";
  static final String CLOSED_PATH = "/actuator/accountclosed";
  static final String PURPOSE = "furar-account-closure-v1";

  private static final Duration TIMEOUT = Duration.ofSeconds(3);
  private static final Pattern ALLOWED =
      Pattern.compile("^\\s*\\{\\s*\"allowed\"\\s*:\\s*(true|false)\\s*}\\s*$");

  private final URI deletion;
  private final URI closed;
  private final String key;
  private final HttpClient http;

  AccountClosureApi(String baseUrl, String key) {
    String base = baseUrl.strip();
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    this.deletion = URI.create(base + DELETION_PATH);
    this.closed = URI.create(base + CLOSED_PATH);
    this.key = key;
    this.http =
        HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  /**
   * A API co contorno de Keycloak, ou baleira se falta o segredo ou é curto: entón ninguén pode
   * eliminar a súa conta (fallo pechado) e ninguén avisa.
   */
  static Optional<AccountClosureApi> fromEnvironment() {
    String secret = System.getenv(InvitationEmailVerification.SECRET_VARIABLE);
    if (secret == null || secret.strip().length() < InvitationProof.MIN_SECRET_LENGTH) {
      return Optional.empty();
    }
    String url = System.getenv(SecondFactorPolicy.URL_VARIABLE);
    return Optional.of(
        new AccountClosureApi(
            url == null || url.isBlank() ? SecondFactorPolicy.DEFAULT_URL : url, key(secret)));
  }

  /** A clave derivada do segredo compartido para este propósito. */
  static String key(String secret) {
    return SecondFactorPolicy.key(secret, PURPOSE);
  }

  /**
   * @param subject o id da conta (o {@code sub})
   * @return se se pode eliminar; baleiro se a API non respondeu ben
   */
  public Optional<Boolean> deletable(String subject) {
    Optional<HttpResponse<String>> response = post(deletion, subject);
    if (response.isEmpty() || response.get().statusCode() != 200) {
      return Optional.empty();
    }
    Matcher m = ALLOWED.matcher(response.get().body() == null ? "" : response.get().body());
    return m.matches() ? Optional.of(Boolean.parseBoolean(m.group(1))) : Optional.empty();
  }

  /**
   * Avisa de que a conta xa se eliminou.
   *
   * @return se a API o recibiu e limpou (200); se non, hai que volver avisar
   */
  public boolean closed(String subject) {
    return post(closed, subject).map(r -> r.statusCode() == 200).orElse(false);
  }

  private Optional<HttpResponse<String>> post(URI endpoint, String subject) {
    String body =
        "{\"key\":"
            + SecondFactorPolicy.json(key)
            + ",\"subject\":"
            + SecondFactorPolicy.json(subject)
            + "}";
    HttpRequest request =
        HttpRequest.newBuilder(endpoint)
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
    try {
      return Optional.of(
          http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
    } catch (IOException e) {
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  /** Onde pregunta (sen a clave). */
  URI deletionEndpoint() {
    return deletion;
  }

  @Override
  public String toString() {
    return "AccountClosureApi[" + deletion + ", " + closed + ", key=***]";
  }
}
