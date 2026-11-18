// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * «Engadirme como xestor» (ADR-0028, revisión do 2026-11-11): a administración da plataforma pasa a
 * ser xestora activa do concello sen invitación nin correo, queda no rexistro do concello (e, polo
 * seu rol, no da plataforma), é idempotente e só a pode facer a plataforma.
 */
class StaffJoinIT extends AbstractIT {

  String slug;
  UUID municipality;
  String admin;
  String token;

  @BeforeEach
  void municipality() {
    slug = uniqueSlug("join");
    municipality = id(createMunicipality(slug));
    admin = "plat-" + UUID.randomUUID();
    TestTokens.grantPlatformAdmin(admin);
    token = TestTokens.withoutRolesClaim(admin, Map.of("name", "Plataforma Deportes"));
  }

  @Test
  void thePlatformBecomesAnActiveManagerWithATraceAndNoMail() {
    // Antes: a plataforma non ve as reservas do concello.
    assertThat(api().get(bookings()).token(token).send().status()).isEqualTo(403);

    Api.Response joined = join(token);

    assertThat(joined.status()).as(joined.toString()).isEqualTo(200);
    JsonNode member = joined.json();
    assertThat(member.get("email").asString()).isEqualTo(admin + "@example.org");
    assertThat(member.get("role").asString()).isEqualTo("MANAGER");
    assertThat(member.get("status").asString()).isEqualTo("ACTIVE");
    assertThat(
            jdbc.sql(
                    "select count(*) from staff_invitation i join staff_member m on m.id ="
                        + " i.member_id where m.subject = :s")
                .param("s", admin)
                .query(Long.class)
                .single())
        .as("sen invitación nin correo")
        .isZero();

    // Desde a seguinte petición, é xestora: ve as reservas e os indicadores do concello.
    assertThat(api().get(bookings()).token(token).send().status()).isEqualTo(200);
    assertThat(
            api()
                .get("/api/v1/municipalities/" + slug + "/indicators")
                .token(token)
                .send()
                .status())
        .isEqualTo(200);
    // E sae no seu cambio de concello.
    JsonNode memberships = api().get("/api/v1/me/staff-memberships").token(token).send().json();
    assertThat(memberships.findValuesAsString("slug")).contains(slug);

    // Rastro: un staff.join no concello, co rol da plataforma e sen correo nos detalles.
    List<Map<String, Object>> events = events(admin);
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().get("roles").toString()).contains("PLATFORM_ADMIN");
    assertThat(events.getFirst().get("details").toString())
        .contains(member.get("id").asString(), "MANAGER")
        .doesNotContain("@");
    // E sae no rexistro da plataforma.
    JsonNode platform = api().get("/api/v1/audit-events").token(token).send().json();
    assertThat(platform.findValuesAsString("action")).contains("staff.join");
  }

  @Test
  void joiningTwiceChangesNothingAndLeavesOneTrace() {
    String first = join(token).json().get("id").asString();
    Api.Response again = join(token);

    assertThat(again.status()).isEqualTo(200);
    assertThat(again.json().get("id").asString()).isEqualTo(first);
    assertThat(events(admin)).hasSize(1);
    assertThat(
            jdbc.sql("select count(*) from staff_member where subject = :s")
                .param("s", admin)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void aPendingInvitationToTheSameEmailIsAcceptedAsManager() {
    Api.Response invited =
        api()
            .post(
                "/api/v1/municipalities/" + slug + "/staff",
                Map.of("email", admin + "@example.org", "role", "STAFF", "language", "gl"))
            .token(TestTokens.platformAdmin())
            .send();
    assertThat(invited.status()).as(invited.toString()).isEqualTo(201);

    Api.Response joined = join(token);

    assertThat(joined.status()).as(joined.toString()).isEqualTo(200);
    assertThat(joined.json().get("id").asString()).isEqualTo(invited.json().get("id").asString());
    assertThat(joined.json().get("role").asString()).isEqualTo("MANAGER");
    assertThat(joined.json().get("status").asString()).isEqualTo("ACTIVE");
  }

  @Test
  void itCanBeRemovedLikeAnyManagerButNeverLeavesTheMunicipalityWithoutOne() {
    JsonNode member = join(token).json();
    String id = member.get("id").asString();

    // É a única xestora: non se pode desactivar (staff.last-manager).
    Api.Response last =
        api()
            .post("/api/v1/municipalities/" + slug + "/staff/" + id + "/deactivate", Map.of())
            .ifMatch(member.get("version").asLong())
            .token(TestTokens.platformAdmin())
            .send();
    assertThat(last.status()).as(last.toString()).isEqualTo(409);
    assertThat(last.json().get("code").asString()).isEqualTo("staff.last-manager");

    // Con outra xestora no concello, xa se pode.
    assertThat(TestTokens.manager(slug)).isNotBlank();
    Api.Response removed =
        api()
            .post("/api/v1/municipalities/" + slug + "/staff/" + id + "/deactivate", Map.of())
            .ifMatch(member.get("version").asLong())
            .token(TestTokens.platformAdmin())
            .send();
    assertThat(removed.status()).as(removed.toString()).isEqualTo(200);
    assertThat(api().get(bookings()).token(token).send().status()).isEqualTo(403);
  }

  @Test
  void onlyThePlatformCanDoIt() {
    assertThat(api().post(path(), Map.of()).send().status()).isEqualTo(401);
    for (String other :
        List.of(
            TestTokens.manager(slug),
            TestTokens.staff(slug),
            TestTokens.person("p-" + UUID.randomUUID(), true))) {
      Api.Response response = join(other);
      assertThat(response.status()).as(response.toString()).isEqualTo(403);
      assertThat(response.json().get("code").asString()).isEqualTo("auth.forbidden");
    }
    assertThat(
            jdbc.sql(
                    "select count(*) from audit_event where action = 'staff.join' and municipality_id = :m")
                .param("m", municipality)
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  void anotherMunicipalityIsNotTouched() {
    String other = uniqueSlug("join-b");
    createMunicipality(other);

    join(token);

    assertThat(
            api().get("/api/v1/municipalities/" + other + "/bookings").token(token).send().status())
        .isEqualTo(403);
    assertThat(
            jdbc.sql("select count(*) from staff_member where subject = :s")
                .param("s", admin)
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  // --- Axudas ---

  private String path() {
    return "/api/v1/municipalities/" + slug + "/staff/join";
  }

  private String bookings() {
    return "/api/v1/municipalities/" + slug + "/bookings";
  }

  private Api.Response join(String token) {
    return api().post(path(), Map.of()).token(token).send();
  }

  private List<Map<String, Object>> events(String subject) {
    return jdbc.sql(
            """
            select details::text as details, actor_roles::text as roles from audit_event
             where action = 'staff.join' and actor_subject = :s and municipality_id = :m
               and outcome = 'SUCCESS'
            """)
        .param("s", subject)
        .param("m", municipality)
        .query()
        .listOfRows();
  }
}
