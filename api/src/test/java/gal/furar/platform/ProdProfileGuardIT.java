// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.FurarApplication;
import gal.furar.shared.config.ProdProfileGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.env.MockEnvironment;

/**
 * D20 (ADR-0007): co perfil {@code prod}, a API négase a arrincar con credenciais de demo ou URL de
 * localhost. Falla antes de crear o contexto, sen tocar a base.
 */
class ProdProfileGuardIT {

  /**
   * Valor que o garda acepta (non é de demo e ten 12+ caracteres). Constrúese en execución e é
   * obviamente sintético: no código non hai ningún literal con aspecto de credencial (D07,
   * gitleaks). Para o dono úsase outro distinto para comprobar que non se confunden.
   */
  private static final String NON_DEMO_VALUE = "x".repeat(16);

  private static final String OTHER_NON_DEMO_VALUE = "y".repeat(16);

  /** Segredo da proba da invitación que o garda acepta (32+ caracteres, sintético). */
  private static final String PROOF_SECRET = "z".repeat(40);

  @Test
  void prodWithDemoPasswordDoesNotStart() {
    SpringApplicationBuilder app =
        new SpringApplicationBuilder(FurarApplication.class)
            .profiles("prod")
            .properties(
                "FURAR_DB_URL=jdbc:postgresql://127.0.0.1:1/none",
                "FURAR_DB_USER=furar_app",
                "FURAR_DB_PASSWORD=furar",
                "FURAR_PUBLIC_URL=https://reservas.example.gal",
                "FURAR_INVITATION_PROOF_SECRET=" + PROOF_SECRET,
                "FURAR_JWK_SET_URI=http://keycloak:8082/certs");
    assertThatThrownBy(() -> app.run().close())
        .hasMessageContaining("Refusing to start with profile 'prod'")
        .hasMessageContaining("FURAR_DB_PASSWORD");
  }

  @Test
  void prodWithLocalhostUrlDoesNotStart() {
    assertThatThrownBy(() -> guard(NON_DEMO_VALUE, "http://localhost:8080", "prod"))
        .hasMessageContaining("FURAR_PUBLIC_URL");
    assertThatThrownBy(() -> guard(NON_DEMO_VALUE, "http://reservas.example.gal", "prod"))
        .hasMessageContaining("https");
    assertThatThrownBy(() -> guard(NON_DEMO_VALUE, "https://reservas.example.gal", "prod", "demo"))
        .hasMessageContaining("demo");
  }

  /** SEC-02: en produción a API non conecta como superusuario nin como dono do esquema. */
  @Test
  void prodRefusesPrivilegedDatabaseUsers() {
    assertThatThrownBy(() -> guardUsers("furar_owner", "furar_owner"))
        .hasMessageContaining("schema owner");
    assertThatThrownBy(() -> guardUsers("postgres", "furar_owner"))
        .hasMessageContaining("superuser");
  }

  /**
   * SEC2-03: en {@code prod} a API non migra nin recibe o contrasinal do dono; iso só o fai o
   * servizo {@code migrate}, onde se comproba que non é de demo nin dun superusuario.
   */
  @Test
  void prodApiNeitherMigratesNorHoldsTheOwnerPassword() {
    MockEnvironment migrating = env("prod");
    migrating.setProperty("spring.flyway.enabled", "true");
    assertThatThrownBy(() -> run(migrating)).hasMessageContaining("must not run migrations");

    MockEnvironment withOwnerPassword = env("prod");
    withOwnerPassword.setProperty("FURAR_DB_OWNER_PASSWORD", OTHER_NON_DEMO_VALUE);
    assertThatThrownBy(() -> run(withOwnerPassword))
        .hasMessageContaining("FURAR_DB_OWNER_PASSWORD must not reach the API process");
  }

  @Test
  void prodMigrateModeChecksOnlyTheOwner() {
    MockEnvironment migrate = migrateEnv();
    assertThatNoException().isThrownBy(() -> run(migrate));

    MockEnvironment demoOwner = migrateEnv();
    demoOwner.setProperty("spring.flyway.password", "furar-owner-demo");
    assertThatThrownBy(() -> run(demoOwner)).hasMessageContaining("FURAR_DB_OWNER_PASSWORD");

    MockEnvironment superuser = migrateEnv();
    superuser.setProperty("spring.flyway.user", "postgres");
    assertThatThrownBy(() -> run(superuser)).hasMessageContaining("superuser");
  }

  /** Contorno do servizo migrate: sen contrasinal da API nin URL pública. */
  private static MockEnvironment migrateEnv() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("prod");
    env.setProperty("furar.mode", "migrate");
    env.setProperty("spring.flyway.enabled", "true");
    env.setProperty("spring.flyway.user", "furar_owner");
    env.setProperty("spring.flyway.password", OTHER_NON_DEMO_VALUE);
    return env;
  }

  /** R5: en {@code prod} a API só acepta tokens da web, nunca do cliente das probas. */
  @Test
  void prodRefusesTheTestClient() {
    MockEnvironment e2e = env("prod");
    e2e.setProperty("furar.security.clients", "furar-web,furar-e2e");
    assertThatThrownBy(() -> run(e2e)).hasMessageContaining("furar-e2e");
    MockEnvironment web = env("prod");
    web.setProperty("furar.security.clients", "furar-web");
    assertThatNoException().isThrownBy(() -> run(web));
  }

  /** ADR-0027: sen o segredo da proba da invitación, curto ou de demo, prod non arrinca. */
  @Test
  void prodRefusesAWeakInvitationProofSecret() {
    for (String weak :
        new String[] {
          "", "x".repeat(31), "furar-demo-invitation-proof-secret-not-for-production"
        }) {
      MockEnvironment env = env("prod");
      env.setProperty("furar.invitation.proof-secret", weak);
      assertThatThrownBy(() -> run(env)).hasMessageContaining("FURAR_INVITATION_PROOF_SECRET");
    }
  }

  @Test
  void prodWithRealSecretsPassesTheGuard() {
    assertThatNoException()
        .isThrownBy(() -> guard(NON_DEMO_VALUE, "https://reservas.example.gal", "prod"));
  }

  @Test
  void otherProfilesAreNotChecked() {
    assertThatNoException().isThrownBy(() -> guard("furar", "http://localhost:4200", "dev"));
    assertThat(new MockEnvironment().getActiveProfiles()).isEmpty();
  }

  private static void guard(String password, String url, String... profiles) {
    MockEnvironment env = env(profiles);
    env.setProperty("spring.datasource.password", password);
    env.setProperty("furar.public-url", url);
    run(env);
  }

  private static void guardUsers(String user, String owner) {
    MockEnvironment env = env("prod");
    env.setProperty("spring.datasource.username", user);
    env.setProperty("spring.flyway.user", owner);
    run(env);
  }

  private static MockEnvironment env(String... profiles) {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles(profiles);
    env.setProperty("spring.datasource.password", NON_DEMO_VALUE);
    env.setProperty("spring.datasource.username", "furar_app");
    env.setProperty("spring.flyway.user", "furar_owner");
    env.setProperty("spring.flyway.enabled", "false");
    env.setProperty("furar.public-url", "https://reservas.example.gal");
    env.setProperty("furar.invitation.proof-secret", PROOF_SECRET);
    return env;
  }

  private static void run(MockEnvironment env) {
    new ProdProfileGuard()
        .postProcessEnvironment(env, new SpringApplication(FurarApplication.class));
  }
}
