// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.testsupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Un só PostgreSQL 18.6 real por JVM de tests (ADR-0013). Sen Ryuk (Colima), párase cun gancho de
 * apagado; {@code verificar.sh} limpa os que queden coa etiqueta {@code org.testcontainers=true}.
 */
public final class PostgresContainers {

  /**
   * {@code postgres:18.6-alpine}, a mesma do compose, fixada por digest (Testcontainers non admite
   * etiqueta e digest á vez). {@code PostgresContainerIT} comproba que é a 18.6.
   */
  public static final String IMAGE =
      "postgres@sha256:83148c0bc09eac4d598b70a61e1404d0212b76c95c504bfd6c81a4c59ec91792";

  /** Os roles de produción (ADR-0015): a API proba con furar_app e Flyway migra con furar_owner. */
  public static final String APP_USER = "furar_app";

  public static final String APP_PASSWORD = "furar-app-test";
  public static final String OWNER_USER = "furar_owner";
  public static final String OWNER_PASSWORD = "furar-owner-test";

  /**
   * Rol só de tests: furar_app con {@code BYPASSRLS} (ADR-0006, D28 i/j). Co contorno {@code
   * FURAR_TEST_DB_USER=furar_app_bypass} a API conecta con el e a RLS non tapa unha fuga de
   * {@code @TenantId}: así {@code probar-violacion.sh} segue probando cada capa por separado.
   */
  public static final String BYPASS_USER = "furar_app_bypass";

  /**
   * Os placeholders de Flyway de {@code application.yaml} para un Flyway feito á man: o rol da API
   * e o día de hoxe en Galicia ({@code demo_day}, nos comentarios das sementes de demo).
   */
  public static Map<String, String> flywayPlaceholders() {
    return flywayPlaceholders(LocalDate.now(ZoneId.of("Europe/Madrid")));
  }

  /** Os mesmos placeholders cun día dado: Flyway volve aplicar as sementes de demo se cambia. */
  public static Map<String, String> flywayPlaceholders(LocalDate day) {
    return Map.of("approle", APP_USER, "demo_day", day.toString());
  }

  /** Rol co que conecta a API nos IT: {@link #APP_USER} salvo que o contorno diga outro. */
  public static String appUser() {
    String user = System.getenv("FURAR_TEST_DB_USER");
    if (user == null || user.isBlank()) {
      return APP_USER;
    }
    if (!user.equals(APP_USER) && !user.equals(BYPASS_USER)) {
      throw new IllegalStateException("FURAR_TEST_DB_USER must be furar_app or furar_app_bypass");
    }
    return user;
  }

  /** O mesmo script de arranque que o compose, para non probar outra cousa. */
  static final Path ROLES_SCRIPT = Path.of("..", "deploy", "postgres", "init", "05-furar.sh");

  private static final PostgreSQLContainer SHARED = start();

  private PostgresContainers() {}

  public static PostgreSQLContainer shared() {
    return SHARED;
  }

  private static PostgreSQLContainer start() {
    PostgreSQLContainer container =
        new PostgreSQLContainer(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("furar")
            .withUsername("postgres")
            .withPassword("postgres-test")
            .withEnv("FURAR_DB_OWNER_PASSWORD", OWNER_PASSWORD)
            .withEnv("FURAR_DB_PASSWORD", APP_PASSWORD)
            .withCopyFileToContainer(
                MountableFile.forHostPath(roles(), 0755), "/docker-entrypoint-initdb.d/05-furar.sh")
            .withCommand("postgres", "-c", "max_connections=60", "-c", "fsync=off");
    container.start();
    createBypassRole(container);
    Runtime.getRuntime().addShutdownHook(new Thread(container::stop, "postgres-stop"));
    return container;
  }

  private static void createBypassRole(PostgreSQLContainer container) {
    try (var connection =
            java.sql.DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
        var statement = connection.createStatement()) {
      statement.execute(
          "create role "
              + BYPASS_USER
              + " login nosuperuser bypassrls in role "
              + APP_USER
              + " password '"
              + APP_PASSWORD
              + "'");
    } catch (java.sql.SQLException e) {
      throw new IllegalStateException("Cannot create " + BYPASS_USER, e);
    }
  }

  private static Path roles() {
    if (!Files.isRegularFile(ROLES_SCRIPT)) {
      throw new IllegalStateException(
          "Run the tests from api/: missing " + ROLES_SCRIPT.toAbsolutePath());
    }
    return ROLES_SCRIPT;
  }
}
