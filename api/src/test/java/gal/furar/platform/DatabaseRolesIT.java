// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.FurarMigration;
import gal.furar.shared.adapter.out.persistence.DatabaseRoleCheck;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.PostgresContainers;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * SEC-02 (ADR-0015): os IT usan os roles de produción, creados polo mesmo {@code 05-furar.sh}. A
 * API conecta como {@code furar_app}, que non é superusuario, só fai DML e non pode tocar o
 * historial de Flyway (SEC2-03); Flyway migrou como {@code furar_owner}, tamén co modo {@code
 * migrate}; e {@link DatabaseRoleCheck} detecta unha conexión de superusuario.
 */
class DatabaseRolesIT extends AbstractIT {

  @Autowired DatabaseRoleCheck check;

  @Test
  void apiRoleIsDataOnly() {
    assertThat(appJdbc.sql("select current_user").query(String.class).single())
        .isEqualTo(PostgresContainers.APP_USER);
    assertThat(appJdbc.sql("select current_setting('is_superuser')").query(String.class).single())
        .isEqualTo("off");
    assertThat(check.problems()).isEmpty();
    assertThatThrownBy(() -> appJdbc.sql("create table probe_ddl (id int)").update())
        .rootCause()
        .hasMessageContaining("permission denied");
    assertThatThrownBy(() -> appJdbc.sql("alter table municipality add column x int").update())
        .rootCause()
        .hasMessageContaining("must be owner");
    assertThatThrownBy(
            () -> appJdbc.sql("copy (select 1) to program 'id'").query(String.class).list())
        .rootCause()
        .hasMessageContaining("permission denied");
  }

  @Test
  void flywayMigratedAsTheSchemaOwner() {
    List<String> owners =
        appJdbc
            .sql(
                """
                select distinct tableowner from pg_tables
                where schemaname = 'public' order by tableowner
                """)
            .query(String.class)
            .list();
    assertThat(owners).containsExactly(PostgresContainers.OWNER_USER);
    assertThat(
            appJdbc
                .sql("select installed_by from flyway_schema_history group by installed_by")
                .query(String.class)
                .list())
        .containsExactly(PostgresContainers.OWNER_USER);
  }

  /**
   * SEC2-03: furar_app le o historial de Flyway pero non o pode cambiar (os privilexios por defecto
   * de 05-furar.sh tamén lle darían DML nesa táboa; V202610221300 quítallo).
   */
  @Test
  void apiRoleCannotRewriteTheMigrationHistory() {
    assertThat(appJdbc.sql("select count(*) from flyway_schema_history").query(Long.class).single())
        .isPositive();
    for (String sql :
        List.of(
            "delete from flyway_schema_history",
            "update flyway_schema_history set success = false",
            "insert into flyway_schema_history (installed_rank, description, type, script,"
                + " installed_by, execution_time, success) values (9999, 'x', 'SQL', 'x', 'x', 0,"
                + " true)",
            "truncate flyway_schema_history")) {
      assertThatThrownBy(() -> appJdbc.sql(sql).update())
          .as(sql)
          .rootCause()
          .hasMessageContaining("permission denied");
    }
  }

  /** ADR-0020: o rexistro de actividade só se engade e le; furar_app non o pode cambiar. */
  @Test
  void apiRoleCannotRewriteTheAuditTrail() {
    appJdbc
        .sql(
            """
            insert into audit_event (id, occurred_at, actor_subject, action, outcome)
            values (gen_random_uuid(), now(), 'test', 'test.append', 'SUCCESS')
            """)
        .update();
    assertThat(appJdbc.sql("select count(*) from audit_event").query(Long.class).single())
        .isPositive();
    for (String sql :
        List.of(
            "delete from audit_event",
            "update audit_event set outcome = 'FAILURE'",
            "truncate audit_event")) {
      assertThatThrownBy(() -> appJdbc.sql(sql).update())
          .as(sql)
          .rootCause()
          .hasMessageContaining("permission denied");
    }
  }

  /**
   * SEC2-03: o modo {@code migrate} da imaxe (servizo de Compose dun só uso) migra como dono e sae
   * con 0; sobre unha base xa migrada só valida. A API, en {@code prod}, non migra.
   */
  @Test
  void migrateCommandRunsAsTheOwnerAndExits() {
    int exit =
        FurarMigration.run(
            "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
            "--spring.datasource.username=" + PostgresContainers.APP_USER,
            "--spring.flyway.user=" + PostgresContainers.OWNER_USER,
            "--spring.flyway.password=" + PostgresContainers.OWNER_PASSWORD);
    assertThat(exit).isZero();
    assertThat(
            appJdbc
                .sql("select count(*) from flyway_schema_history where not success")
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  void checkFlagsASuperuserConnection() throws Exception {
    try (Connection admin =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      try (ResultSet rs =
          admin.createStatement().executeQuery("select current_setting('is_superuser')")) {
        rs.next();
        assertThat(rs.getString(1)).isEqualTo("on");
      }
      var superuser = JdbcClient.create(new SingleConnectionDataSource(admin, true));
      MockEnvironment prod = new MockEnvironment();
      prod.setActiveProfiles("prod");
      DatabaseRoleCheck strict = newCheck(superuser, prod);
      assertThat(strict.problems()).anyMatch(p -> p.contains("is a superuser"));
      assertThatThrownBy(strict::afterSingletonsInstantiated)
          .hasMessageContaining("Refusing to start with profile 'prod'");
      // Fóra de prod só avisa.
      newCheck(superuser, new MockEnvironment()).afterSingletonsInstantiated();
    }
  }

  private static DatabaseRoleCheck newCheck(JdbcClient jdbc, MockEnvironment env) {
    return new DatabaseRoleCheck(jdbc, env);
  }
}
