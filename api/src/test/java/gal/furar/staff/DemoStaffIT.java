// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.FurarApplication;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.PostgresContainers;
import gal.furar.testsupport.TestJwtConfig;
import gal.furar.testsupport.TestSupportConfig;
import gal.furar.testsupport.TestTokens;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * D4-12: o persoal da demo ({@code R__demo_staff.sql}) nunha base nova cos scripts de demo e a API
 * como en {@code dev}/{@code demo}, con tokens <b>sen</b> {@code furar_roles} co {@code sub} fixo
 * de cada usuario do realm de demo ({@code deploy/keycloak/demo/overlay.json}). Os roles saen só da
 * táboa: {@code xestor-naron} xestiona Narón, {@code recepcion-naron} ve as reservas e non a
 * xestión, {@code ex-recepcion-naron} non entra. Volver aplicar o script non cambia nada.
 *
 * <p>Contexto propio (base {@code furar_demo_…}); péchase ao rematar.
 */
@SpringBootTest(
    classes = FurarApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestJwtConfig.class, TestSupportConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoStaffIT {

  static final PostgreSQLContainer POSTGRES = PostgresContainers.shared();
  static final String DB = "furar_demo_" + UUID.randomUUID().toString().substring(0, 8);
  static final String SCRIPT = "db/demo/R__demo_staff.sql";

  // sub fixos do realm de demo (deploy/keycloak/README.md)
  static final String ADMIN = "019a0b6e-0000-7000-8000-000000000900";
  static final String XESTOR_NARON = "019a0b6e-0000-7000-8000-000000000902";
  static final String XESTOR_FERROL = "019a0b6e-0000-7000-8000-000000000903";
  static final String RECEPCION_NARON = "019a0b6e-0000-7000-8000-000000000904";
  static final String EX_RECEPCION_NARON = "019a0b6e-0000-7000-8000-000000000905";
  static final String XESTOR_VERIN = "019a0b6e-0000-7000-8000-000000000907";

  /** Comarca de Verín: slug e sub da persoa xestora de cada concello (…0907 a …090e). */
  static final List<String> COMARCA =
      List.of(
          "verin 019a0b6e-0000-7000-8000-000000000907",
          "vilardevos 019a0b6e-0000-7000-8000-000000000908",
          "rios 019a0b6e-0000-7000-8000-000000000909",
          "castrelo-do-val 019a0b6e-0000-7000-8000-00000000090a",
          "cualedro 019a0b6e-0000-7000-8000-00000000090b",
          "laza 019a0b6e-0000-7000-8000-00000000090c",
          "monterrei 019a0b6e-0000-7000-8000-00000000090d",
          "oimbra 019a0b6e-0000-7000-8000-00000000090e");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    JdbcClient admin =
        JdbcClient.create(
            new SimpleDriverDataSource(
                new org.postgresql.Driver(),
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()));
    admin.sql("create database " + DB + " owner " + PostgresContainers.OWNER_USER).update();
    // Os permisos da API na base nova, como fai deploy/postgres/init/05-furar.sh na furar.
    JdbcClient.create(
            new SimpleDriverDataSource(
                new org.postgresql.Driver(), url(), POSTGRES.getUsername(), POSTGRES.getPassword()))
        .sql(
            """
            revoke all on schema public from public;
            grant usage on schema public to furar_app;
            alter default privileges for role furar_owner in schema public
              grant select, insert, update, delete on tables to furar_app;
            alter default privileges for role furar_owner in schema public
              grant usage, select on sequences to furar_app;
            """)
        .update();
    registry.add("spring.datasource.url", DemoStaffIT::url);
    registry.add("spring.datasource.username", PostgresContainers::appUser);
    registry.add("spring.datasource.password", () -> PostgresContainers.APP_PASSWORD);
    registry.add("spring.flyway.user", () -> PostgresContainers.OWNER_USER);
    registry.add("spring.flyway.password", () -> PostgresContainers.OWNER_PASSWORD);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/demo");
  }

  @LocalServerPort int port;

  @Test
  void theDemoMembershipsCoverEveryStateWithTheFixedSubjects() {
    JdbcClient owner = owner();

    assertThat(
            owner
                .sql(
                    """
                    select m.slug || ' ' || coalesce(s.subject, s.email) || ' ' || s.role || ' '
                           || s.status
                    from staff_member s join municipality m on m.id = s.municipality_id
                    order by s.id
                    """)
                .query(String.class)
                .list())
        .containsExactly(
            "naron " + XESTOR_NARON + " MANAGER ACTIVE",
            "ferrol " + XESTOR_FERROL + " MANAGER ACTIVE",
            "naron " + RECEPCION_NARON + " STAFF ACTIVE",
            "naron " + EX_RECEPCION_NARON + " STAFF DEACTIVATED",
            "naron persoal-novo@example.org STAFF INVITED",
            "naron persoal-antigo@example.org STAFF INVITED",
            COMARCA.get(0) + " MANAGER ACTIVE",
            COMARCA.get(1) + " MANAGER ACTIVE",
            COMARCA.get(2) + " MANAGER ACTIVE",
            COMARCA.get(3) + " MANAGER ACTIVE",
            COMARCA.get(4) + " MANAGER ACTIVE",
            COMARCA.get(5) + " MANAGER ACTIVE",
            COMARCA.get(6) + " MANAGER ACTIVE",
            COMARCA.get(7) + " MANAGER ACTIVE");
    assertThat(
            owner
                .sql(
                    """
                    select count(*) from staff_member
                    where (accepted_at is not null) <> (subject is not null)
                       or (status = 'DEACTIVATED') <> (deactivated_by is not distinct from :x)
                       or (invited_by = :a) <> (role = 'MANAGER')
                       or accepted_at > now() or deactivated_at > now() or invited_at > now()
                    """)
                .param("x", XESTOR_NARON)
                .param("a", ADMIN)
                .query(Integer.class)
                .single())
        .as("obrigas 2026-11, desactivación e primeiras xestoras pola plataforma, datas pasadas")
        .isZero();

    assertThat(
            owner
                .sql(
                    """
                    select coalesce(s.subject, s.email) || ' '
                           || case when i.used_at is not null then 'used'
                                   when i.expires_at > now() then 'open'
                                   else 'expired' end
                    from staff_invitation i join staff_member s on s.id = i.member_id
                    where octet_length(i.token_hash) = 32 and i.superseded_at is null
                    order by s.id
                    """)
                .query(String.class)
                .list())
        .as("unha invitación por pertenza, con hash de 32 bytes")
        .containsExactly(
            XESTOR_NARON + " used",
            XESTOR_FERROL + " used",
            RECEPCION_NARON + " used",
            EX_RECEPCION_NARON + " used",
            "persoal-novo@example.org open",
            "persoal-antigo@example.org expired",
            COMARCA.get(0).split(" ")[1] + " used",
            COMARCA.get(1).split(" ")[1] + " used",
            COMARCA.get(2).split(" ")[1] + " used",
            COMARCA.get(3).split(" ")[1] + " used",
            COMARCA.get(4).split(" ")[1] + " used",
            COMARCA.get(5).split(" ")[1] + " used",
            COMARCA.get(6).split(" ")[1] + " used",
            COMARCA.get(7).split(" ")[1] + " used");
  }

  @Test
  void theDemoActivityLogHasPastStaffEventsWithoutPersonalData() {
    JdbcClient owner = owner();

    assertThat(
            owner
                .sql(
                    """
                    select action || ' ' || count(*) from audit_event
                    where action like 'staff.%' group by action order by action
                    """)
                .query(String.class)
                .list())
        .containsExactly("staff.accept 12", "staff.deactivate 1", "staff.invite 14");
    assertThat(
            owner
                .sql(
                    """
                    select count(*) from audit_event
                    where action like 'staff.%'
                      and (occurred_at > now() or details::text like '%@%'
                           or not jsonb_exists_all(details, array['memberId', 'role'])
                           or exists (select 1 from jsonb_object_keys(details) k
                                      where k not in ('memberId', 'role')))
                    """)
                .query(Integer.class)
                .single())
        .as("datas pasadas e detalles sen correo nin nomes")
        .isZero();
  }

  @Test
  void rolesComeFromTheTableWithoutTheClaim() {
    String xestorNaron = token(XESTOR_NARON);
    assertThat(get("/naron/indicators", xestorNaron))
        .as("xestor-naron xestiona Narón")
        .isEqualTo(200);
    assertThat(get("/naron/bookings", xestorNaron)).isEqualTo(200);
    assertThat(get("/ferrol/indicators", xestorNaron)).isEqualTo(403);
    assertThat(role("naron", xestorNaron)).isEqualTo("MANAGER");

    String recepcion = token(RECEPCION_NARON);
    assertThat(get("/naron/bookings", recepcion))
        .as("recepcion-naron ve as reservas")
        .isEqualTo(200);
    assertThat(get("/naron/indicators", recepcion)).as("e non a xestión").isEqualTo(403);
    assertThat(role("naron", recepcion)).isEqualTo("STAFF");

    String exRecepcion = token(EX_RECEPCION_NARON);
    assertThat(get("/naron/bookings", exRecepcion)).as("ex-recepcion-naron").isEqualTo(403);
    assertThat(get("/naron/indicators", exRecepcion)).isEqualTo(403);
    assertThat(role("naron", exRecepcion)).isNull();

    assertThat(get("/ferrol/indicators", token(XESTOR_FERROL))).isEqualTo(200);
    assertThat(get("/naron/indicators", token(XESTOR_FERROL))).isEqualTo(403);

    // Comarca de Verín: cada xestor xestiona o seu concello e só o seu.
    for (String entry : COMARCA) {
      String[] parts = entry.split(" ");
      String xestor = token(parts[1]);
      assertThat(get("/" + parts[0] + "/indicators", xestor)).as(entry).isEqualTo(200);
      assertThat(role(parts[0], xestor)).as(entry).isEqualTo("MANAGER");
      assertThat(get("/naron/indicators", xestor)).as(entry).isEqualTo(403);
    }
    assertThat(get("/oimbra/indicators", token(XESTOR_VERIN))).isEqualTo(403);

    // O claim de antes (naron:MANAGER) xa non dá nada sen pertenza.
    assertThat(get("/naron/indicators", TestTokens.withRolesClaim("naron:MANAGER"))).isEqualTo(403);
  }

  @Test
  void reapplyingTheScriptChangesNothing() {
    JdbcClient owner = owner();
    String counts =
        """
        select (select count(*) from staff_member) || '/' || (select count(*) from staff_invitation)
               || '/' || (select count(*) from audit_event where action like 'staff.%')
               || '/' || (select md5(string_agg(id || status || invited_at || version, ','
                                                order by id)) from staff_member)
               || '/' || (select md5(string_agg(token_hash::text, ',' order by id))
                          from staff_invitation)
        """;
    String before = owner.sql(counts).query(String.class).single();

    runScript();

    assertThat(owner.sql(counts).query(String.class).single()).isEqualTo(before);
  }

  // --- Axudas ---

  /** Token como o do realm de demo: sen {@code furar_roles}. */
  static String token(String subject) {
    return TestTokens.withoutRolesClaim(subject, Map.of());
  }

  int get(String path, String token) {
    return new Api(port).get("/api/v1/municipalities" + path).token(token).send().status();
  }

  String role(String slug, String token) {
    Api.Response response =
        new Api(port).get("/api/v1/municipalities/" + slug + "/me/staff").token(token).send();
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    return response.json().get("role").isNull() ? null : response.json().get("role").asString();
  }

  void runScript() {
    try (var in = getClass().getClassLoader().getResourceAsStream(SCRIPT);
        var connection =
            DriverManager.getConnection(
                url(), PostgresContainers.OWNER_USER, PostgresContainers.OWNER_PASSWORD);
        var statement = connection.createStatement()) {
      assertThat(in).isNotNull();
      statement.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException | SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  static JdbcClient owner() {
    return JdbcClient.create(
        new SimpleDriverDataSource(
            new org.postgresql.Driver(),
            url(),
            PostgresContainers.OWNER_USER,
            PostgresContainers.OWNER_PASSWORD));
  }

  static String url() {
    return POSTGRES.getJdbcUrl().replaceFirst("/furar(\\?|$)", "/" + DB + "$1");
  }
}
