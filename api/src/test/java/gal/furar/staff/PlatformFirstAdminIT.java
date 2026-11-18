// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.PostgresContainers;
import gal.furar.testsupport.TestTokens;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * ADR-0028, punto 2: a primeira administración da plataforma é a conta co correo verificado igual a
 * {@code FURAR_ADMIN_EMAIL} (normalizado) mentres non hai ningunha; unha soa vez, no rexistro de
 * actividade e sen o correo en ningunha resposta nin evento. Despois, ningunha outra conta o pasa a
 * ser polo correo. A orde importa dentro de cada test e a táboa é de toda a instalación: cada un
 * empeza baleirándoa.
 */
@TestPropertySource(properties = "furar.platform.admin-email= Primeira.Admin@Example.ORG ")
class PlatformFirstAdminIT extends AbstractIT {

  private static final String EMAIL = "primeira.admin@example.org";

  @Test
  void theVerifiedAccountOfFurarAdminEmailBecomesTheFirstAdministratorOnce() {
    // Instalación nova: ningunha administración (os outros IT deixan as súas filas).
    jdbc.sql("delete from platform_admin").update();

    // Mesmo correo sen verificar: nada (e a persoa non pasa de PersonUseCase).
    String unverified = "unverified-" + UUID.randomUUID();
    Api.Response notVerified =
        access(token(unverified, Map.of("email", EMAIL, "email_verified", false)));
    assertThat(notVerified.status()).isEqualTo(403);
    assertThat(create(token(unverified, Map.of("email", EMAIL, "email_verified", false))))
        .isEqualTo(403);

    // Outra conta verificada: conta normal, sen rol.
    String other = "other-" + UUID.randomUUID();
    Api.Response normal = access(token(other, Map.of("email", "outra@example.org")));
    assertThat(normal.status()).isEqualTo(200);
    assertThat(normal.json().get("platformAdmin").asBoolean()).isFalse();
    assertThat(admins()).isZero();

    // A conta do correo, verificada (maiúsculas e espazos distintos): pasa a ser administración.
    String first = "first-" + UUID.randomUUID();
    String firstToken = token(first, Map.of("email", "  PRIMEIRA.admin@example.org"));
    Api.Response granted = access(firstToken);
    assertThat(granted.status()).isEqualTo(200);
    assertThat(granted.body()).doesNotContainIgnoringCase(EMAIL);
    assertThat(granted.json().get("platformAdmin").asBoolean()).isTrue();
    assertThat(
            jdbc.sql("select granted_by from platform_admin where subject = :s")
                .param("s", first)
                .query(String.class)
                .single())
        .isEqualTo("furar:first-run");
    assertThat(create(firstToken)).isEqualTo(201);

    // Unha vez: un evento co rol, sen concello e sen o correo.
    List<Map<String, Object>> events =
        jdbc.sql(
                """
                select actor_subject, actor_roles::text as roles, municipality_id,
                       details::text as details
                  from audit_event where action = 'platform.first-admin'
                """)
            .query()
            .listOfRows();
    assertThat(events).hasSize(1);
    assertThat(events.getFirst())
        .containsEntry("actor_subject", first)
        .containsEntry("roles", "{PLATFORM_ADMIN}")
        .containsEntry("municipality_id", null);
    assertThat(String.valueOf(events.getFirst().get("details")))
        .doesNotContainIgnoringCase("example.org");

    // Xa hai administración: outra conta co mesmo correo (Keycloak non o deixaría) non pasa a selo.
    String second = "second-" + UUID.randomUUID();
    Api.Response later = access(token(second, Map.of("email", EMAIL)));
    assertThat(later.json().get("platformAdmin").asBoolean()).isFalse();
    assertThat(admins()).isEqualTo(1);
    assertThat(access(firstToken).json().get("platformAdmin").asBoolean()).isTrue();
  }

  /**
   * A web pide á vez o acceso á plataforma e as pertenzas ao persoal. Se a outra petición dá o rol
   * e aínda non fixo commit, esta espera no índice único e non insire: tamén ten que dicir que si
   * (antes dicía que non e a web quedaba na portada ata recargar).
   */
  @Test
  void aRequestThatWaitsForAConcurrentGrantAlsoSeesTheAdministrator() throws Exception {
    jdbc.sql("delete from platform_admin").update();
    String first = "first-" + UUID.randomUUID();
    String firstToken = token(first, Map.of("email", EMAIL));

    try (Connection other =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            PostgresContainers.OWNER_USER,
            PostgresContainers.OWNER_PASSWORD)) {
      // A outra petición: xa inseriu a primeira administración e aínda non fixo commit.
      other.setAutoCommit(false);
      try (PreparedStatement insert =
          other.prepareStatement(
              "insert into platform_admin (id, subject, granted_at, granted_by)"
                  + " values (?, ?, now(), 'furar:first-run')")) {
        insert.setObject(1, UUID.randomUUID());
        insert.setString(2, first);
        insert.executeUpdate();
      }
      CompletableFuture<Api.Response> waiting =
          CompletableFuture.supplyAsync(() -> access(firstToken));
      awaitLockWait();
      other.commit();

      Api.Response response = waiting.get();
      assertThat(response.status()).isEqualTo(200);
      assertThat(response.json().get("platformAdmin").asBoolean()).isTrue();
    }
    assertThat(admins()).isEqualTo(1);
  }

  /** Ata que unha sesión da base espera por un bloqueo (a inserción da petición). */
  private void awaitLockWait() throws InterruptedException {
    Instant limit = Instant.now().plus(Duration.ofSeconds(20));
    while (Instant.now().isBefore(limit)) {
      int waiting =
          jdbc.sql("select count(*) from pg_locks where not granted").query(Integer.class).single();
      if (waiting > 0) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("the request never waited for the concurrent grant");
  }

  private static String token(String subject, Map<String, Object> claims) {
    return TestTokens.withoutRolesClaim(subject, claims);
  }

  private Api.Response access(String token) {
    return api().get("/api/v1/me/platform-access").token(token).send();
  }

  private int create(String token) {
    return api()
        .post(
            "/api/v1/municipalities",
            Map.of(
                "slug",
                uniqueSlug("primeiro"),
                "name",
                "Primeiro concello",
                "languages",
                List.of("gl"),
                "defaultLanguage",
                "gl"))
        .token(token)
        .send()
        .status();
  }

  private int admins() {
    return jdbc.sql("select count(*) from platform_admin").query(Integer.class).single();
  }
}
