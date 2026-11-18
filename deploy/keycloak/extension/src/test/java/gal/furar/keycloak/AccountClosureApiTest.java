// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * ADR-0043: Keycloak pregunta á API se se pode eliminar unha conta e avísaa cando se eliminou, coa
 * clave do seu propósito. O vector da clave é o mesmo ca o de {@code InternalCallKeyTest} da API.
 * Calquera resposta que non sexa un 200 co JSON esperado é «sen resposta»: nunca un si.
 */
class AccountClosureApiTest {

  static final String SECRET = SecondFactorPolicyTest.SECRET;
  static final String KEY = "tlSMz70rcawpGl90zbi0aY6ELJQds3cBfBg3Ge42Lpo"; // gitleaks:allow (vector de proba do segredo público de demo)

  private HttpServer server;
  private final List<String> calls = new ArrayList<>();

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  /** Unha API de proba: cada ruta responde sempre o mesmo e garda as chamadas. */
  private String api(int status, String deletionBody) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    for (String path : List.of(AccountClosureApi.DELETION_PATH, AccountClosureApi.CLOSED_PATH)) {
      server.createContext(
          path,
          exchange -> {
            calls.add(
                path
                    + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out =
                (path.equals(AccountClosureApi.DELETION_PATH) ? deletionBody : "{\"closed\":true}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
            try (OutputStream os = exchange.getResponseBody()) {
              os.write(out);
            }
          });
    }
    server.start();
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
  }

  @Test
  void theKeyIsItsOwnAndNotTheSecondFactorOne() {
    assertEquals(KEY, AccountClosureApi.key("  " + SECRET + "\n"));
    assertFalse(KEY.equals(SecondFactorPolicy.key(SECRET)));
  }

  @Test
  void asksWithTheKeyAndTheSubject() throws IOException {
    AccountClosureApi closure = new AccountClosureApi(api(200, "{\"allowed\":true}"), KEY);
    assertEquals(Optional.of(true), closure.deletable("sub-1"));
    assertEquals(
        AccountClosureApi.DELETION_PATH + " {\"key\":\"" + KEY + "\",\"subject\":\"sub-1\"}",
        calls.get(0));
    assertFalse(closure.toString().contains(KEY));
  }

  @Test
  void aNoIsANo() throws IOException {
    AccountClosureApi closure = new AccountClosureApi(api(200, " {\"allowed\": false} "), KEY);
    assertEquals(Optional.of(false), closure.deletable("sub-1"));
  }

  @Test
  void anythingElseIsNoAnswer() throws IOException {
    assertEquals(
        Optional.empty(),
        new AccountClosureApi(api(403, ""), KEY).deletable("sub-1"),
        "clave mala");
    stop();
    assertEquals(
        Optional.empty(),
        new AccountClosureApi(api(200, "{\"allowed\":\"yes\"}"), KEY).deletable("sub-1"),
        "resposta rara");
    stop();
    assertEquals(
        Optional.empty(),
        new AccountClosureApi(api(500, "{\"allowed\":true}"), KEY).deletable("sub-1"),
        "erro");
  }

  @Test
  void withoutTheApiThereIsNoAnswer() {
    AccountClosureApi closure = new AccountClosureApi("http://127.0.0.1:9", KEY);
    assertEquals(Optional.empty(), closure.deletable("sub-1"));
    assertFalse(closure.closed("sub-1"));
  }

  @Test
  void closedIsTakenOnlyWithA200() throws IOException {
    assertTrue(new AccountClosureApi(api(200, ""), KEY).closed("sub-1"));
    assertEquals(
        AccountClosureApi.CLOSED_PATH + " {\"key\":\"" + KEY + "\",\"subject\":\"sub-1\"}",
        calls.get(0));
    stop();
    assertFalse(new AccountClosureApi(api(500, ""), KEY).closed("sub-1"));
  }

  @Test
  void theBaseUrlMayEndInASlash() {
    assertEquals(
        "http://api:9081/actuator/accountdeletion",
        new AccountClosureApi("http://api:9081//", KEY).deletionEndpoint().toString());
  }
}
