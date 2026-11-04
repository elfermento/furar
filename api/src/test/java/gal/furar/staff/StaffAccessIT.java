// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.StatementCounter;
import gal.furar.testsupport.TestTokens;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * D4-05 (ADR-0027, puntos 2–5): os roles do persoal saen da táboa de pertenzas en cada petición.
 * Todos os tokens van <b>sen</b> o claim {@code furar_roles}, como os do persoal en Keycloak.
 */
class StaffAccessIT extends AbstractIT {

  static final Instant T0 = Instant.parse("2026-11-01T08:00:00Z");
  static final Instant T1 = Instant.parse("2026-11-05T09:00:00Z");
  static final Instant T2 = Instant.parse("2026-11-05T09:03:00Z");

  String naron;
  String ferrol;
  UUID naronId;
  UUID ferrolId;
  String subject;

  @BeforeEach
  void municipalities() {
    naron = uniqueSlug("persoal-naron");
    ferrol = uniqueSlug("persoal-ferrol");
    naronId = id(createMunicipality(naron));
    ferrolId = id(createMunicipality(ferrol));
    subject = "sub-" + UUID.randomUUID();
  }

  @Test
  void anActiveMembershipGivesItsRoleAndADeactivationCountsOnTheNextRequest() {
    UUID member = insert(naronId, "STAFF", "ACTIVE", null);
    String token = session(T1);

    assertThat(bookings(naron, token).status()).isEqualTo(200);

    deactivate(member);
    Api.Response next = bookings(naron, token);
    assertThat(next.status()).isEqualTo(403);
    assertThat(code(next)).isEqualTo("auth.forbidden");
  }

  @Test
  void aRoleChangeCountsOnTheNextRequest() {
    UUID member = insert(naronId, "STAFF", "ACTIVE", null);
    String token = session(T1);

    assertThat(indicators(naron, token).status()).isEqualTo(403);
    assertThat(bookings(naron, token).status()).isEqualTo(200);

    jdbc.sql("update staff_member set role = 'MANAGER', version = version + 1 where id = :id")
        .param("id", member)
        .update();
    assertThat(indicators(naron, token).status()).isEqualTo(200);
  }

  @Test
  void eachMunicipalityHasItsOwnRole() {
    insert(naronId, "MANAGER", "ACTIVE", null);
    insert(ferrolId, "STAFF", "ACTIVE", null);
    String token = session(T1);

    assertThat(indicators(naron, token).status()).isEqualTo(200);
    assertThat(indicators(ferrol, token).status()).isEqualTo(403);
    assertThat(bookings(ferrol, token).status()).isEqualTo(200);
  }

  @Test
  void aMembershipInOneMunicipalityGivesNothingInAnother() {
    insert(naronId, "MANAGER", "ACTIVE", null);
    String token = session(T1);

    assertThat(bookings(naron, token).status()).isEqualTo(200);
    Api.Response other = bookings(ferrol, token);
    assertThat(other.status()).isEqualTo(403);
    assertThat(code(other)).isEqualTo("auth.forbidden");
    // Nin as invitadas nin as desactivadas dan rol.
    String invitedSubject = "sub-" + UUID.randomUUID();
    insertWithSubject(ferrolId, "STAFF", "DEACTIVATED", invitedSubject);
    assertThat(bookings(ferrol, sessionFor(invitedSubject, T1)).status()).isEqualTo(403);
  }

  /**
   * Sen segundo factor (decisión do mantedor do 2026-11-02): unha sesión só de contrasinal dá o rol
   * da táboa e rexistra o acceso unha vez, con {@code staff.sign-in}.
   */
  @Test
  void aPasswordOnlySessionGivesTheRoleAndRecordsTheSignIn() {
    UUID member = insert(naronId, "MANAGER", "ACTIVE", null);
    String token =
        TestTokens.withoutRolesClaim(
            subject, Map.of("acr", "furar-pwd", "amr", List.of("pwd"), "auth_time", seconds(T2)));

    assertThat(bookings(naron, token).status()).isEqualTo(200);
    assertThat(bookings(naron, token).status()).isEqualTo(200);
    assertThat(me(naron, token).get("role").asString()).isEqualTo("MANAGER");
    assertThat(instant(member, "last_sign_in_at")).isEqualTo(T2);
    assertThat(instant(member, "previous_sign_in_at")).isNull();
    assertThat(signIns()).isEqualTo(1);
    JsonNode event =
        jdbc.sql(
                """
                select municipality_id, details::text as details from audit_event
                 where action = 'staff.sign-in' and actor_subject = :s
                """)
            .param("s", subject)
            .query((rs, n) -> Api.JSON.readTree(rs.getString("details")))
            .single();
    assertThat(event.get("role").asString()).isEqualTo("MANAGER");
    assertThat(event.get("memberId").asString()).isEqualTo(member.toString());
  }

  /**
   * ADR-0027, punto 5: un {@code auth_time} novo move o último acceso ao anterior unha soa vez,
   * aínda que cheguen dez peticións á vez, e só unha rexistra {@code staff.sign-in}.
   */
  @Test
  void aNewSignInMovesTheLastOneOnceUnderConcurrency() throws Exception {
    UUID member = insert(naronId, "MANAGER", "ACTIVE", T0);
    String token = session(T1);
    int parallel = 10;
    CyclicBarrier together = new CyclicBarrier(parallel);
    ExecutorService pool = Executors.newFixedThreadPool(parallel);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < parallel; i++) {
        results.add(
            pool.submit(
                () -> {
                  together.await(10, TimeUnit.SECONDS);
                  return bookings(naron, token).status();
                }));
      }
      for (Future<Integer> result : results) {
        assertThat(result.get(60, TimeUnit.SECONDS)).isEqualTo(200);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(instant(member, "last_sign_in_at")).isEqualTo(T1);
    assertThat(instant(member, "previous_sign_in_at")).isEqualTo(T0);
    assertThat(signIns()).isEqualTo(1);

    // A seguinte petición da mesma sesión non cambia nada; a web ve o acceso anterior.
    JsonNode me = me(naron, token);
    assertThat(Instant.parse(me.get("previousSignInAt").asString())).isEqualTo(T0);
    assertThat(signIns()).isEqualTo(1);
    // Unha sesión vella (auth_time anterior) tampouco.
    assertThat(bookings(naron, session(T0)).status()).isEqualTo(200);
    assertThat(instant(member, "last_sign_in_at")).isEqualTo(T1);
    assertThat(signIns()).isEqualTo(1);
  }

  /** Quen non é persoal do concello: sen rol. */
  @Test
  void myAccessWithoutMembership() {
    insert(ferrolId, "MANAGER", "ACTIVE", T0);
    JsonNode me = me(naron, session(T1));
    assertThat(me.get("role").isNull()).isTrue();
    assertThat(me.get("previousSignInAt").isNull()).isTrue();
    assertThat(api().get(meStaff(naron)).send().status()).isEqualTo(401);
  }

  /**
   * Risco do plan (§5): a táboa engade unha soa sentenza a cada petición autorizada da mesma sesión
   * (máis o {@code set_config} da RLS da súa transacción curta), e ningunha escritura.
   */
  @Test
  void anAuthorizedRequestAddsOneStatement() {
    insert(naronId, "STAFF", "ACTIVE", null);
    String token = session(T1);
    assertThat(bookings(naron, token).status()).isEqualTo(200);

    try (StatementCounter.Recording recording = StatementCounter.record()) {
      assertThat(bookings(naron, token).status()).isEqualTo(200);
      assertThat(recording.count(s -> s.contains("staff_member"))).isEqualTo(1);
      assertThat(recording.count(s -> s.contains("update staff_member"))).isZero();
      assertThat(recording.count(s -> s.contains("audit_event"))).isZero();
    }
  }

  /**
   * R3: ler o concello con sesión é un caso de uso público que mira quen pregunta dentro da súa
   * transacción. O rol da táboa xa se leu antes, fóra dela, así que a petición nunca ten dúas
   * conexións á vez (sen iso, un pool cheo destas peticións quedaba bloqueado).
   */
  @Test
  void readingTheMunicipalityWithASessionUsesOneConnectionAtATime() {
    insert(naronId, "STAFF", "ACTIVE", null);
    String token = session(T1);
    String municipality = "/api/v1/municipalities/" + naron;
    assertThat(api().get(municipality).token(token).send().status()).isEqualTo(200);

    try (StatementCounter.Recording recording = StatementCounter.record()) {
      Api.Response response = api().get(municipality).token(token).send();
      assertThat(response.status()).as(response.toString()).isEqualTo(200);
      assertThat(recording.count(s -> s.contains("staff_member"))).isEqualTo(1);
      assertThat(recording.maxConnectionsPerThread()).isEqualTo(1);
    }
  }

  // --- axudas ---

  /** Token da sesión de {@code subject} iniciada en {@code authTime}. */
  String session(Instant authTime) {
    return sessionFor(subject, authTime);
  }

  static String sessionFor(String subject, Instant authTime) {
    return TestTokens.withoutRolesClaim(subject, Map.of("auth_time", seconds(authTime)));
  }

  static long seconds(Instant instant) {
    return instant.getEpochSecond();
  }

  Api.Response bookings(String slug, String token) {
    return api().get("/api/v1/municipalities/" + slug + "/bookings").token(token).send();
  }

  Api.Response indicators(String slug, String token) {
    return api().get("/api/v1/municipalities/" + slug + "/indicators").token(token).send();
  }

  static String meStaff(String slug) {
    return "/api/v1/municipalities/" + slug + "/me/staff";
  }

  JsonNode me(String slug, String token) {
    Api.Response response = api().get(meStaff(slug)).token(token).send();
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    return response.json();
  }

  static String code(Api.Response response) {
    return response.json().get("code").asString();
  }

  UUID insert(UUID municipality, String role, String status, @Nullable Instant lastSignIn) {
    UUID id = insertWithSubject(municipality, role, status, subject);
    if (lastSignIn != null) {
      jdbc.sql("update staff_member set last_sign_in_at = :t where id = :id")
          .param("t", OffsetDateTime.ofInstant(lastSignIn, ZoneOffset.UTC))
          .param("id", id)
          .update();
    }
    return id;
  }

  /** Unha pertenza por JDBC como dono da base (sen RLS), co {@code sub} indicado. */
  UUID insertWithSubject(UUID municipality, String role, String status, String sub) {
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
    return id;
  }

  void deactivate(UUID member) {
    jdbc.sql(
            """
            update staff_member
               set status = 'DEACTIVATED', deactivated_by = 'sub-xestora', deactivated_at = now(),
                   version = version + 1
             where id = :id
            """)
        .param("id", member)
        .update();
  }

  @Nullable Instant instant(UUID member, String column) {
    OffsetDateTime value =
        jdbc.sql("select " + column + " from staff_member where id = :id")
            .param("id", member)
            .query(OffsetDateTime.class)
            .list()
            .get(0);
    return value == null ? null : value.toInstant();
  }

  long signIns() {
    return jdbc.sql(
            "select count(*) from audit_event where action = 'staff.sign-in' and actor_subject = :s")
        .param("s", subject)
        .query(Long.class)
        .single();
  }
}
