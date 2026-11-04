// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff;

import static gal.furar.staff.StaffManagementIT.assertError;
import static gal.furar.staff.StaffManagementIT.email;
import static gal.furar.staff.StaffManagementIT.staff;
import static gal.furar.staff.StaffManagementIT.token;
import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.sql.Types;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Desactivar e reactivar (ADR-0027, revisión do 2026-10-30; especificación, bloque 7, P7):
 * desactivar non borra nada (nin a fila nin o rexistro) e reactivar devolve o acceso co mesmo rol,
 * sen invitación nova. Só se reactiva quen aceptou algunha vez e non está anonimizada; a
 * plataforma, só xestores; nunca se a conta reservou mentres tanto nin se xa hai outra pertenza
 * viva. Os tokens do persoal van sen o claim de roles (o rol sae da táboa).
 */
class StaffReactivationIT extends AbstractIT {

  String naron;
  UUID naronId;
  String manager;

  @BeforeEach
  void municipality() {
    naron = uniqueSlug("reactivar");
    naronId = id(createMunicipality(naron));
    manager = "sub-xestora-" + UUID.randomUUID();
    member(naronId, manager, "MANAGER", "ACTIVE", email("xestora"), null);
  }

  @Test
  void deactivatingKeepsEverythingAndReactivatingGivesBackTheSameRole() {
    String person = "sub-conserxe-" + UUID.randomUUID();
    UUID id = member(naronId, person, "STAFF", "ACTIVE", email("conserxe"), null);
    assertThat(me(token(person)).get("role").asString()).isEqualTo("STAFF");
    long rows = rows();

    Api.Response deactivated = deactivate(token(manager), id, 0);
    assertThat(deactivated.status()).as(deactivated.toString()).isEqualTo(200);
    assertThat(deactivated.json().get("status").asString()).isEqualTo("DEACTIVATED");
    // Na seguinte petición xa non é persoal, pero a fila e a persoa seguen.
    assertThat(me(token(person)).get("role").isNull()).isTrue();
    assertThat(rows()).isEqualTo(rows);
    assertThat(column(id, "subject")).isEqualTo(person);
    assertThat(column(id, "email")).isNotNull();

    Api.Response reactivated = reactivate(token(manager), id, 1);
    assertThat(reactivated.status()).as(reactivated.toString()).isEqualTo(200);
    JsonNode body = reactivated.json();
    assertThat(body.get("status").asString()).isEqualTo("ACTIVE");
    assertThat(body.get("role").asString()).isEqualTo("STAFF");
    assertThat(body.get("name").asString()).isEqualTo("Persoa de proba");
    assertThat(body.get("deactivatedAt").isNull()).isTrue();
    assertThat(body.get("invitation").isNull()).isTrue();
    assertThat(reactivated.etagVersion()).isEqualTo(2);
    assertThat(rows()).isEqualTo(rows);
    // Sen invitación nova.
    assertThat(invitations(id)).isZero();
    // Volve entrar co mesmo rol na seguinte petición.
    assertThat(me(token(person)).get("role").asString()).isEqualTo("STAFF");

    // O rexistro: as dúas accións, só coa pertenza e o rol.
    for (String action : List.of("staff.deactivate", "staff.reactivate")) {
      JsonNode details = lastEvent(action, "SUCCESS");
      assertThat(details.get("memberId").asString()).isEqualTo(id.toString());
      assertThat(details.get("role").asString()).isEqualTo("STAFF");
      assertThat(details.toString()).doesNotContain("@", person);
    }
  }

  @Test
  void aManagerIsReactivatedAsManagerAndTheLastManagerRuleStillHolds() {
    String other = "sub-xestor-" + UUID.randomUUID();
    UUID otherId = member(naronId, other, "MANAGER", "ACTIVE", email("xestor"), null);
    assertThat(deactivate(token(manager), otherId, 0).status()).isEqualTo(200);
    // Agora queda unha soa xestora: nin desactivarse nin baixarse o rol.
    UUID managerId = idOf(manager);
    assertError(deactivate(token(manager), managerId, 0), 409, "staff.last-manager");
    assertError(
        api()
            .put(staff(naron) + "/" + managerId + "/role", Map.of("role", "STAFF"))
            .token(token(manager))
            .ifMatch(0)
            .send(),
        409,
        "staff.last-manager");

    Api.Response back = reactivate(token(manager), otherId, 1);
    assertThat(back.status()).isEqualTo(200);
    assertThat(back.json().get("role").asString()).isEqualTo("MANAGER");
    assertThat(me(token(other)).get("role").asString()).isEqualTo("MANAGER");
    // Con dúas xestoras activas, xa se pode desactivar unha.
    assertThat(deactivate(token(manager), managerId, 0).status()).isEqualTo(200);
  }

  @Test
  void onlyADeactivatedMembershipThatWasAcceptedIsReactivated() {
    UUID active = member(naronId, null, "STAFF", "ACTIVE", email("activa"), null);
    assertError(reactivate(token(manager), active, 0), 409, "staff.transition-invalid");

    // Invitada e desactivada: nunca tivo acceso; invítase de novo.
    Api.Response invited =
        api()
            .post(staff(naron), Map.of("email", email("invitada"), "role", "STAFF"))
            .token(token(manager))
            .send();
    UUID invitedId = id(invited.json());
    assertThat(deactivate(token(manager), invitedId, 0).status()).isEqualTo(200);
    assertError(reactivate(token(manager), invitedId, 1), 409, "staff.transition-invalid");
    assertThat(lastEvent("staff.reactivate", "FAILURE").get("code").asString())
        .isEqualTo("staff.transition-invalid");

    // O If-Match vello.
    UUID deactivated = member(naronId, null, "STAFF", "DEACTIVATED", email("vella"), null);
    assertError(reactivate(token(manager), deactivated, 3), 412, "common.stale-version");
  }

  @Test
  void anAnonymisedMembershipIsNotReactivated() {
    UUID id = UUID.randomUUID();
    member(
        id,
        naronId,
        "sub-" + id,
        "STAFF",
        "DEACTIVATED",
        "anon-" + id + "@anonimizada.invalid",
        OffsetDateTime.now(ZoneOffset.UTC).minusYears(4));
    assertError(reactivate(token(manager), id, 0), 409, "staff.anonymised");
    assertThat(column(id, "status")).isEqualTo("DEACTIVATED");
  }

  @Test
  void aBlockedMembershipIsReactivatedAndShownAgain() {
    UUID id =
        member(
            naronId,
            null,
            "STAFF",
            "DEACTIVATED",
            email("bloqueada"),
            OffsetDateTime.now(ZoneOffset.UTC).minus(Duration.ofDays(400)));
    JsonNode before = listed(id);
    assertThat(before.get("blocked").asBoolean()).isTrue();
    assertThat(before.get("email").isNull()).isTrue();

    assertThat(reactivate(token(manager), id, 0).status()).isEqualTo(200);
    JsonNode after = listed(id);
    assertThat(after.get("blocked").asBoolean()).isFalse();
    assertThat(after.get("email").asString()).endsWith("@example.org");
    assertThat(after.get("name").asString()).isEqualTo("Persoa de proba");
  }

  @Test
  void anotherLiveMembershipWithTheSameEmailOrPersonBlocksTheReactivation() {
    // Volveuse invitar o mesmo correo: a vella xa non se reactiva.
    String email = email("volta");
    UUID old = member(naronId, null, "STAFF", "DEACTIVATED", email, null);
    Api.Response again =
        api()
            .post(staff(naron), Map.of("email", email, "role", "STAFF"))
            .token(token(manager))
            .send();
    assertThat(again.status()).isEqualTo(201);
    assertError(reactivate(token(manager), old, 0), 409, "staff.already-member");

    // A mesma persoa xa é activa noutra pertenza do concello.
    String person = "sub-" + UUID.randomUUID();
    UUID first = member(naronId, person, "STAFF", "DEACTIVATED", email("primeira"), null);
    member(naronId, person, "STAFF", "ACTIVE", email("segunda"), null);
    assertError(reactivate(token(manager), first, 0), 409, "staff.already-member");
    assertThat(column(first, "status")).isEqualTo("DEACTIVATED");
  }

  @Test
  void thePlatformReactivatesAnyRoleAndStaffCannotReactivate() {
    String platform = TestTokens.platformAdmin();
    UUID managerGone = member(naronId, null, "MANAGER", "DEACTIVATED", email("xestor"), null);
    UUID staffGone = member(naronId, null, "STAFF", "DEACTIVATED", email("conserxe"), null);

    assertThat(reactivate(platform, staffGone, 0).status()).isEqualTo(200);
    assertThat(reactivate(platform, managerGone, 0).status()).isEqualTo(200);

    String conserxe = "sub-conserxe-" + UUID.randomUUID();
    member(naronId, conserxe, "STAFF", "ACTIVE", email("mostrador"), null);
    UUID stillGone = member(naronId, null, "STAFF", "DEACTIVATED", email("segue-fora"), null);
    assertError(reactivate(token(conserxe), stillGone, 0), 403, "auth.forbidden");
    // Outro concello: non existe aquí.
    String ferrol = uniqueSlug("reactivar-ferrol");
    UUID ferrolId = id(createMunicipality(ferrol));
    UUID ferrolMember = member(ferrolId, null, "STAFF", "DEACTIVATED", email("ferrol"), null);
    assertError(reactivate(token(manager), ferrolMember, 0), 404, "staff.not-found");
    assertThat(column(stillGone, "status")).isEqualTo("DEACTIVATED");
    assertThat(column(staffGone, "status")).isEqualTo("ACTIVE");
  }

  // --- axudas ---

  Api.Response deactivate(String token, UUID id, long version) {
    return api()
        .post(staff(naron) + "/" + id + "/deactivate", Map.of())
        .token(token)
        .ifMatch(version)
        .send();
  }

  Api.Response reactivate(String token, UUID id, long version) {
    return api()
        .post(staff(naron) + "/" + id + "/reactivate", Map.of())
        .token(token)
        .ifMatch(version)
        .send();
  }

  JsonNode me(String token) {
    Api.Response response =
        api().get("/api/v1/municipalities/" + naron + "/me/staff").token(token).send();
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    return response.json();
  }

  JsonNode listed(UUID id) {
    Api.Response response = api().get(staff(naron)).token(token(manager)).send();
    assertThat(response.status()).isEqualTo(200);
    for (JsonNode item : response.json()) {
      if (item.get("id").asString().equals(id.toString())) {
        return item;
      }
    }
    throw new AssertionError(id + " not listed");
  }

  long rows() {
    return jdbc.sql("select count(*) from staff_member where municipality_id = :m")
        .param("m", naronId)
        .query(Long.class)
        .single();
  }

  long invitations(UUID member) {
    return jdbc.sql("select count(*) from staff_invitation where member_id = :m")
        .param("m", member)
        .query(Long.class)
        .single();
  }

  @Nullable String column(UUID id, String column) {
    return jdbc.sql("select " + column + "::text from staff_member where id = :id")
        .param("id", id)
        .query(String.class)
        .optional()
        .orElse(null);
  }

  UUID idOf(String subject) {
    return jdbc.sql("select id from staff_member where subject = :s and status = 'ACTIVE'")
        .param("s", subject)
        .query(UUID.class)
        .single();
  }

  JsonNode lastEvent(String action, String outcome) {
    return jdbc.sql(
            """
            select details::text from audit_event
             where municipality_id = :m and action = :a and outcome = :o
             order by occurred_at desc limit 1
            """)
        .param("m", naronId)
        .param("a", action)
        .param("o", outcome)
        .query((rs, n) -> Api.JSON.readTree(rs.getString(1)))
        .single();
  }

  UUID member(
      UUID municipality,
      @Nullable String subject,
      String role,
      String status,
      String email,
      @Nullable OffsetDateTime deactivatedAt) {
    UUID id = UUID.randomUUID();
    member(
        id,
        municipality,
        subject == null ? "sub-" + id : subject,
        role,
        status,
        email,
        deactivatedAt);
    return id;
  }

  /** Unha pertenza aceptada por JDBC como dono da base (sen RLS). */
  void member(
      UUID id,
      UUID municipality,
      String subject,
      String role,
      String status,
      String email,
      @Nullable OffsetDateTime deactivatedAt) {
    boolean deactivated = "DEACTIVATED".equals(status);
    boolean anonymised = email.endsWith("@anonimizada.invalid");
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime since = now.minusYears(5);
    jdbc.sql(
            """
            insert into staff_member
              (id, municipality_id, email, display_name, subject, role, status, language,
               invited_by, invited_at, accepted_at, deactivated_by,
               deactivated_at, created_at, updated_at, version)
            values (:id, :m, :email, :name, :subject, :role, :status, 'gl', 'sub-semente', :since,
                    :since, :deactivatedBy, :deactivatedAt, :since, :since, 0)
            """)
        .param("id", id)
        .param("m", municipality)
        .param("email", email)
        .param("name", anonymised ? null : "Persoa de proba", Types.VARCHAR)
        .param("subject", subject)
        .param("role", role)
        .param("status", status)
        .param("since", since)
        .param("deactivatedBy", deactivated ? "sub-semente" : null, Types.VARCHAR)
        .param(
            "deactivatedAt",
            deactivated ? (deactivatedAt == null ? now : deactivatedAt) : null,
            Types.TIMESTAMP_WITH_TIMEZONE)
        .update();
  }
}
