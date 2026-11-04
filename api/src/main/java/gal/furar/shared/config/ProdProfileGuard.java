// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Co perfil {@code prod}, a API non arrinca con credenciais de demo (tamén o segredo da proba da
 * invitación), conectando como superusuario ou dono do esquema, con URL de localhost, migrando ou
 * co contrasinal do dono no contorno; no modo {@code migrate} só se comproba o dono (ADR-0007,
 * ADR-0015). Comprobase antes de crear o contexto: nada chega a conectar coa base. Que o rol real
 * non sexa superusuario compróbao despois {@code DatabaseRoleCheck}, xa conectado.
 */
public class ProdProfileGuard implements EnvironmentPostProcessor {

  /** Valores de demo coñecidos ({@code .env.example}, compose de desenvolvemento). */
  static final Set<String> DEMO_SECRETS =
      Set.of(
          "", "furar", "demo", "password", "changeme", "change-me", "postgres", "admin", "secret");

  static final String MODE_PROPERTY = "furar.mode";
  static final String MIGRATE = "migrate";
  static final String OWNER_PASSWORD_VARIABLE = "FURAR_DB_OWNER_PASSWORD";
  static final String CLIENTS = "furar.security.clients";
  static final String E2E_CLIENT = "furar-e2e";
  static final String PROOF_SECRET = "furar.invitation.proof-secret";

  /** Lonxitude mínima do segredo da proba da invitación (igual ca na extensión de Keycloak). */
  static final int MIN_PROOF_SECRET_LENGTH = 32;

  private static final Set<String> SUPERUSERS = Set.of("postgres", "furar");

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (!Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
      return;
    }
    List<String> problems = new ArrayList<>();
    if (Arrays.asList(environment.getActiveProfiles()).contains("demo")) {
      problems.add("profile 'demo' cannot be combined with 'prod'");
    }
    String owner = environment.getProperty("spring.flyway.user", "");
    if (MIGRATE.equals(environment.getProperty(MODE_PROPERTY))) {
      // Servizo migrate (SEC2-03): só o dono do esquema, nunca un superusuario.
      if (isDemo(environment.getProperty("spring.flyway.password", ""))) {
        problems.add("spring.flyway.password (FURAR_DB_OWNER_PASSWORD) has a demo value");
      }
      if (SUPERUSERS.contains(owner)) {
        problems.add("FURAR_DB_OWNER_USER '" + owner + "' is a superuser; use furar_owner");
      }
    } else {
      checkApi(environment, owner, problems);
    }
    if (!problems.isEmpty()) {
      throw new IllegalStateException(
          "Refusing to start with profile 'prod': " + String.join("; ", problems));
    }
  }

  /**
   * A API que atende peticións: só DML, sen o contrasinal do dono e sen migrar (SEC-02, SEC2-03).
   * As migracións son do servizo {@code migrate} (a mesma imaxe co argumento {@code migrate}).
   */
  private static void checkApi(
      ConfigurableEnvironment environment, String owner, List<String> problems) {
    String password = environment.getProperty("spring.datasource.password", "");
    if (isDemo(password)) {
      problems.add("spring.datasource.password (FURAR_DB_PASSWORD) has a demo value");
    }
    String user = environment.getProperty("spring.datasource.username", "");
    if (!user.isBlank() && user.equals(owner)) {
      problems.add(
          "the API (FURAR_DB_USER) must not connect as the schema owner (FURAR_DB_OWNER_USER)");
    }
    if (SUPERUSERS.contains(user)) {
      problems.add("FURAR_DB_USER '" + user + "' is a superuser; use furar_app (ADR-0015)");
    }
    if (environment.getProperty("spring.flyway.enabled", Boolean.class, true)) {
      problems.add(
          "the API must not run migrations in 'prod' (spring.flyway.enabled=true):"
              + " run the image with the 'migrate' argument first (ADR-0015)");
    }
    if (environment.containsProperty(OWNER_PASSWORD_VARIABLE)) {
      problems.add(
          OWNER_PASSWORD_VARIABLE
              + " must not reach the API process: only the 'migrate' service needs it");
    }
    String clients = environment.getProperty(CLIENTS, "");
    if (clients.contains(E2E_CLIENT)) {
      problems.add(
          CLIENTS
              + " must not accept tokens of the test client '"
              + E2E_CLIENT
              + "' in 'prod' (demo only)");
    }
    String proofSecret = environment.getProperty(PROOF_SECRET, "");
    if (isDemo(proofSecret) || proofSecret.strip().length() < MIN_PROOF_SECRET_LENGTH) {
      problems.add(
          PROOF_SECRET
              + " (FURAR_INVITATION_PROOF_SECRET) must be a random value of at least "
              + MIN_PROOF_SECRET_LENGTH
              + " characters, the same as Keycloak's (ADR-0027)");
    }
    String publicUrl = environment.getProperty("furar.public-url", "");
    if (publicUrl.isBlank() || publicUrl.contains("localhost") || publicUrl.contains("127.0.0.1")) {
      problems.add("furar.public-url (FURAR_PUBLIC_URL) must be the public URL, not localhost");
    } else if (!publicUrl.startsWith("https://")) {
      problems.add("furar.public-url (FURAR_PUBLIC_URL) must use https");
    }
  }

  static boolean isDemo(String secret) {
    String s = secret.strip().toLowerCase(Locale.ROOT);
    return DEMO_SECRETS.contains(s) || s.contains("demo") || s.length() < 12;
  }
}
