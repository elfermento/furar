// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * {@code GET /api/v1/me/staff-memberships} (ADR-0027, revisión do 2026-11-03): os concellos onde a
 * persoa da sesión é persoal activo, co seu rol, sen ler nada doutra persoa nin rexistrar un
 * acceso. O rol de cada concello lese coa RLS dese concello.
 */
class MyStaffMembershipsIT extends AbstractIT {

  static final String PATH = "/api/v1/me/staff-memberships";

  String first;
  String second;
  String third;
  UUID firstId;
  UUID secondId;
  UUID thirdId;
  String subject;

  @BeforeEach
  void municipalities() {
    // Os nomes («Concello cambio-a-…») dan a orde.
    first = uniqueSlug("cambio-a");
    second = uniqueSlug("cambio-b");
    third = uniqueSlug("cambio-c");
    firstId = id(createMunicipality(first));
    secondId = id(createMunicipality(second));
    thirdId = id(createMunicipality(third));
    subject = "sub-" + UUID.randomUUID();
  }

  @Test
  void listsOnlyTheActiveMembershipsOfTheCallerSortedByNameWithTheirRole() {
    insert(secondId, "MANAGER", "ACTIVE", subject);
    insert(firstId, "STAFF", "ACTIVE", subject);
    insert(thirdId, "MANAGER", "DEACTIVATED", subject);
    insert(thirdId, "MANAGER", "ACTIVE", "sub-other-" + UUID.randomUUID());

    JsonNode body = mine(token(subject));

    assertThat(rows(body))
        .containsExactly(
            List.of(first, "Concello " + first, "STAFF"),
            List.of(second, "Concello " + second, "MANAGER"));
  }

  @Test
  void aPersonWhoIsNotStaffGetsAnEmptyList() {
    insert(firstId, "MANAGER", "DEACTIVATED", subject);

    assertThat(mine(token(subject)).isEmpty()).isTrue();
  }

  @Test
  void itDoesNotRecordASignInInAnyMunicipality() {
    insert(firstId, "MANAGER", "ACTIVE", subject);
    insert(secondId, "STAFF", "ACTIVE", subject);

    mine(token(subject));

    assertThat(
            jdbc.sql(
                    "select count(*) from staff_member where subject = :s and last_sign_in_at is not null")
                .param("s", subject)
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  void withoutSessionItIsUnauthorizedAndAnUnverifiedEmailIsForbidden() {
    assertThat(api().get(PATH).send().status()).isEqualTo(401);
    Api.Response unverified =
        api()
            .get(PATH)
            .token(TestTokens.withoutRolesClaim(subject, Map.of("email_verified", false)))
            .send();
    assertThat(unverified.status()).isEqualTo(403);
    assertThat(unverified.json().get("code").asString()).isEqualTo("auth.email-not-verified");
  }

  // --- axudas ---

  static String token(String subject) {
    return TestTokens.withoutRolesClaim(
        subject, Map.of("auth_time", Instant.parse("2026-11-03T08:00:00Z").getEpochSecond()));
  }

  JsonNode mine(String token) {
    Api.Response response = api().get(PATH).token(token).send();
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    return response.json();
  }

  static List<List<String>> rows(JsonNode body) {
    List<List<String>> rows = new ArrayList<>();
    body.forEach(
        m ->
            rows.add(
                List.of(
                    m.get("slug").asString(), m.get("name").asString(), m.get("role").asString())));
    return rows;
  }

  /** Unha pertenza por JDBC como dono da base (sen RLS). */
  void insert(UUID municipality, String role, String status, String sub) {
    UUID id = UUID.randomUUID();
    boolean deactivated = "DEACTIVATED".equals(status);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    jdbc.sql(
            """
            insert into staff_member
              (id, municipality_id, email, display_name, subject, role, status, language,
               invited_by, invited_at, accepted_at, deactivated_by, deactivated_at,
               created_at, updated_at, version)
            values (:id, :m, :email, 'Persoa de proba', :subject, :role, :status, 'gl',
                    'sub-xestora', :now, :now, :deactivatedBy, :deactivatedAt, :now, :now,
                    0)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("email", "persoal-" + id + "@example.org")
        .param("subject", sub)
        .param("role", role)
        .param("status", status)
        .param("now", now)
        .param("deactivatedBy", deactivated ? "sub-xestora" : null, Types.VARCHAR)
        .param("deactivatedAt", deactivated ? now : null, Types.TIMESTAMP_WITH_TIMEZONE)
        .update();
  }
}
