// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.shared.security.InternalCallKey;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalManagementPort;

/**
 * ADR-0029: Keycloak pregunta polo porto interno de Actuator se a conta ten que entrar co segundo
 * factor. Si para o persoal activo e a administración da plataforma; non para a veciñanza nin para
 * quen deixou de ser persoal. Sen a clave derivada do segredo compartido, 403; e nada no porto da
 * API.
 */
class SecondFactorEndpointIT extends AbstractIT {

  @LocalManagementPort int managementPort;

  @Value("${furar.invitation.proof-secret}")
  String sharedSecret;

  @Test
  void staffAndPlatformAdministratorsNeedTheSecondFactorAndResidentsDoNot() throws Exception {
    UUID municipality = id(createMunicipality(uniqueSlug("sf")));
    String staff = "sf-staff-" + UUID.randomUUID();
    String former = "sf-former-" + UUID.randomUUID();
    String admin = "sf-admin-" + UUID.randomUUID();
    member(municipality, staff, "ACTIVE");
    member(municipality, former, "DEACTIVATED");
    jdbc.sql(
            "insert into platform_admin (id, subject, granted_at, granted_by)"
                + " values (:id, :s, now(), 'proba')")
        .param("id", UUID.randomUUID())
        .param("s", admin)
        .update();
    try {
      String key = InternalCallKey.derive(sharedSecret, InternalCallKey.SECOND_FACTOR).value();
      assertThat(ask(key, staff, "persoal@example.org")).isEqualTo("200 {\"required\":true}");
      assertThat(ask(key, admin, null)).isEqualTo("200 {\"required\":true}");
      assertThat(ask(key, former, null)).isEqualTo("200 {\"required\":false}");
      assertThat(ask(key, "vecina-" + UUID.randomUUID(), "vecina@example.org"))
          .isEqualTo("200 {\"required\":false}");
    } finally {
      jdbc.sql("delete from platform_admin where subject = :s").param("s", admin).update();
    }
  }

  @Test
  void withoutTheDerivedKeyItAnswersNothing() throws Exception {
    assertThat(ask(null, "calquera", null)).startsWith("403");
    assertThat(ask("", "calquera", null)).startsWith("403");
    // O segredo sen derivar non é a clave.
    assertThat(ask(sharedSecret, "calquera", null)).startsWith("403");
    assertThat(
            ask(
                InternalCallKey.derive(sharedSecret, InternalCallKey.SECOND_FACTOR).value(),
                " ",
                null))
        .startsWith("400");
  }

  @Test
  void itIsNotOnTheApiPort() {
    Api.Response response =
        api().post("/actuator/secondfactor", Map.of("subject", "calquera")).send();
    assertThat(response.status()).isNotEqualTo(200);
  }

  private String ask(String key, String subject, String email)
      throws IOException, InterruptedException {
    Map<String, Object> body = new LinkedHashMap<>();
    if (key != null) {
      body.put("key", key);
    }
    body.put("subject", subject);
    if (email != null) {
      body.put("email", email);
    }
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + managementPort + "/actuator/secondfactor"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Api.JSON.writeValueAsString(body)))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    return (response.statusCode() + " " + response.body()).strip();
  }

  private void member(UUID municipality, String subject, String status) {
    boolean deactivated = "DEACTIVATED".equals(status);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            insert into staff_member
              (id, municipality_id, email, display_name, subject, role, status, language,
               invited_by, invited_at, accepted_at, deactivated_by, deactivated_at,
               created_at, updated_at, version)
            values (:id, :m, :email, 'Persoa de proba', :subject, 'STAFF', :status, 'gl',
                    'sub-xestora', :now, :now, :deactivatedBy, :deactivatedAt, :now, :now, 0)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("email", "persoal-" + id + "@example.org")
        .param("subject", subject)
        .param("status", status)
        .param("now", now)
        .param("deactivatedBy", deactivated ? "sub-xestora" : null, java.sql.Types.VARCHAR)
        .param("deactivatedAt", deactivated ? now : null, java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
        .update();
  }
}
