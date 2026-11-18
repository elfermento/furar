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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * ADR-0029: Keycloak pregúntalle á API se a conta ten que usar o segundo factor, coa clave
 * derivada do segredo compartido. O vector da clave é o mesmo ca o de {@code InternalCallKeyTest}
 * da API. Calquera resposta que non sexa un 200 co JSON esperado é «sen resposta».
 */
class SecondFactorPolicyTest {

  static final String SECRET = "furar-demo-invitation-proof-secret-not-for-production";
  static final String KEY = "SO5VzVez0ZcaWEMnksh55SyqsPb6Q-3iXX64hd2TkoM"; // gitleaks:allow (vector de proba do segredo público de demo)

  private HttpServer server;
  private final List<String> bodies = new ArrayList<>();

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  /** Unha API de proba que responde sempre o mesmo e garda os corpos recibidos. */
  private String api(int status, String body) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        SecondFactorPolicy.PATH,
        exchange -> {
          bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] out = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
          }
        });
    server.start();
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
  }

  @Test
  void theKeyIsTheHmacOfThePurpose() {
    assertEquals(KEY, SecondFactorPolicy.key("  " + SECRET + "\n"));
  }

  @Test
  void asksWithTheKeyTheSubjectAndTheEmail() throws IOException {
    String url = api(200, "{\"required\":true}");
    SecondFactorPolicy policy = new SecondFactorPolicy(url, KEY);
    assertEquals(Optional.of(true), policy.required("sub-1", "a\"b@example.org"));
    assertEquals(
        "{\"key\":\"" + KEY + "\",\"subject\":\"sub-1\",\"email\":\"a\\\"b@example.org\"}",
        bodies.get(0));
    assertFalse(policy.toString().contains(KEY));
  }

  @Test
  void aNoIsANo() throws IOException {
    SecondFactorPolicy policy = new SecondFactorPolicy(api(200, " {\"required\": false} "), KEY);
    assertEquals(Optional.of(false), policy.required("sub-1", null));
    assertEquals("{\"key\":\"" + KEY + "\",\"subject\":\"sub-1\"}", bodies.get(0));
  }

  @Test
  void anythingElseIsNoAnswer() throws IOException {
    assertEquals(
        Optional.empty(), new SecondFactorPolicy(api(403, ""), KEY).required("sub-1", null));
    stop();
    assertEquals(
        Optional.empty(),
        new SecondFactorPolicy(api(200, "{\"required\":\"yes\"}"), KEY).required("sub-1", null));
    stop();
    assertEquals(
        Optional.empty(),
        new SecondFactorPolicy(api(500, "{\"required\":false}"), KEY).required("sub-1", null));
    stop();
    server = null;
    // Ninguén escoita: sen resposta, sen excepción.
    assertEquals(
        Optional.empty(), new SecondFactorPolicy("http://127.0.0.1:1", KEY).required("s", null));
  }

  @Test
  void theConditionNeverSkipsTheCodeWhenItCannotKnow() {
    AtomicInteger asked = new AtomicInteger();
    // Invitación válida: si, sen preguntar.
    assertEquals(
        SecondFactorCondition.Decision.REQUIRED,
        SecondFactorCondition.decide(
            true,
            () -> {
              asked.incrementAndGet();
              return Optional.of(false);
            },
            false));
    assertEquals(0, asked.get());
    assertEquals(
        SecondFactorCondition.Decision.REQUIRED,
        SecondFactorCondition.decide(false, () -> Optional.of(true), false));
    assertEquals(
        SecondFactorCondition.Decision.NOT_REQUIRED,
        SecondFactorCondition.decide(false, () -> Optional.of(false), true));
    // Sen resposta: cun método xa configurado (aplicación ou correo), pídese; sen el, o acceso para.
    assertEquals(
        SecondFactorCondition.Decision.REQUIRED,
        SecondFactorCondition.decide(false, Optional::empty, true));
    assertEquals(
        SecondFactorCondition.Decision.UNAVAILABLE,
        SecondFactorCondition.decide(false, Optional::empty, false));
  }

  @Test
  void aSecondFactorAlreadyGivenInThisSignInIsNotAskedAgain() {
    // Ao vincular unha conta dun provedor co contrasinal e o código, o fluxo de despois non o repite.
    assertTrue(SecondFactorCondition.alreadyGiven(List.of("password", "otp")));
    assertTrue(SecondFactorCondition.alreadyGiven(List.of("password", "furar-email-code")));
    assertFalse(SecondFactorCondition.alreadyGiven(List.of("password")));
    assertFalse(SecondFactorCondition.alreadyGiven(List.of()));
  }

  @Test
  void aNewAccountConfiguresItOnlyIfInvitedOrIfTheApiSaysSo() {
    assertTrue(SecondFactorRegistration.needsSetup(true, Optional::empty));
    assertTrue(SecondFactorRegistration.needsSetup(false, () -> Optional.of(true)));
    assertFalse(SecondFactorRegistration.needsSetup(false, () -> Optional.of(false)));
    // Sen resposta, nada: a condición do seguinte acceso non deixa entrar sen saber.
    assertFalse(SecondFactorRegistration.needsSetup(false, Optional::empty));
  }

  @Test
  void jsonStringsAreEscaped() {
    assertEquals("\"a\\\\b\\u000a\"", SecondFactorPolicy.json("a\\b\n"));
    assertEquals(Optional.empty(), SecondFactorPolicy.parse(null));
  }
}
