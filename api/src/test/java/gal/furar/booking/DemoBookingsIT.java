// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.PostgresContainers;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.function.Consumer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Reservas da demo (D2-08) nunha base baleira co perfil {@code demo}: migran despois das
 * instalacións, hai pasadas, de hoxe e futuras, de {@code vecino-naron} e doutras persoas, cos
 * cinco estados de cobro da pantalla do xestor; a ocupación e a historia van con cada reserva, e
 * volver aplicar o script non duplica nada; as de Verín (FUR-VR2…), igual. Só se contan as súas
 * (código FUR-DE2…): a historia de D3-05 ({@code R__demo_naron_history.sql}) vai na mesma base e
 * próbaa {@code DemoIndicatorsIT}.
 */
class DemoBookingsIT {

  static final PostgreSQLContainer POSTGRES = PostgresContainers.shared();
  static final String SCRIPT = "db/demo/R__demo_naron_bookings.sql";
  static final String VERIN_SCRIPT = "db/demo/R__demo_verin_bookings.sql";
  static final String VECINO = "019a0b6e-0000-7000-8000-000000000901";

  static final String COUNTS =
      """
      select (select count(*) from booking where code like 'FUR-DE2_')
             || '/' || (select count(*) from booking_occupancy o join booking b on b.id = o.booking_id
                        where b.code like 'FUR-DE2_')
             || '/' || (select count(*) from booking_occupancy o join booking b on b.id = o.booking_id
                        where b.code like 'FUR-DE2_' and o.active)
             || '/' || (select count(*) from booking_event e join booking b on b.id = e.booking_id
                        where b.code like 'FUR-DE2_')
      """;

  /**
   * As de Verín (código FUR-VR2…): reservas, ocupacións, activas, eventos e titulares sen conta.
   */
  static final String VERIN_COUNTS =
      """
      select (select count(*) from booking where code like 'FUR-VR2_')
             || '/' || (select count(*) from booking_occupancy o join booking b on b.id = o.booking_id
                        where b.code like 'FUR-VR2_')
             || '/' || (select count(*) from booking_occupancy o join booking b on b.id = o.booking_id
                        where b.code like 'FUR-VR2_' and o.active)
             || '/' || (select count(*) from booking_event e join booking b on b.id = e.booking_id
                        where b.code like 'FUR-VR2_')
             || '/' || (select count(*) from booking
                        where code like 'FUR-VR2_' and holder_subject not like 'demo-person-%')
      """;

  @Test
  void anEmptyDatabaseGetsPastTodayAndFutureBookingsInEveryPaymentState() {
    inFreshDatabase(
        db -> {
          flyway(db).migrate();
          JdbcClient jdbc = owner(db);

          assertThat(
                  jdbc.sql(
                          """
                          select distinct status || ':' || payment_status from booking
                          where code like 'FUR-DE2_' order by 1
                          """)
                      .query(String.class)
                      .list())
              .containsExactly(
                  "CANCELLED_BY_MUNICIPALITY:REFUNDED",
                  "CANCELLED_BY_MUNICIPALITY:TO_REFUND",
                  "CANCELLED_BY_PERSON:PENDING",
                  "CANCELLED_BY_PERSON:TO_REFUND",
                  "CONFIRMED:PAID",
                  "CONFIRMED:PENDING");
          assertThat(
                  jdbc.sql(
                          """
                          select distinct sign((lower(period) at time zone 'Europe/Madrid')::date
                                               - current_date)::int
                          from booking where code like 'FUR-DE2_' order by 1
                          """)
                      .query(Integer.class)
                      .list())
              .as("pasadas, de hoxe e futuras")
              .containsExactly(-1, 0, 1);
          assertThat(
                  jdbc.sql(
                          """
                          select count(*) filter (where lower(period) > now()) || '/'
                                 || count(*) filter (where upper(period) <= now())
                          from booking where holder_subject = :s and status = 'CONFIRMED'
                          """)
                      .param("s", VECINO)
                      .query(String.class)
                      .single())
              .as("vecino-naron ten próximas e historial")
              .matches("[1-9]\\d*/[1-9]\\d*");
          assertThat(
                  jdbc.sql(
                          """
                          select count(*) from booking b
                          where (b.status = 'CONFIRMED') <> exists (
                            select 1 from booking_occupancy o where o.booking_id = b.id and o.active)
                          """)
                      .query(Integer.class)
                      .single())
              .as("só as confirmadas ocupan")
              .isZero();
          assertThat(
                  jdbc.sql(
                          """
                          select count(*) from booking b
                          where not exists (select 1 from booking_event e
                                            where e.booking_id = b.id and e.kind = 'CREATED')
                             or (b.status <> 'CONFIRMED' and not exists (
                                   select 1 from booking_event e
                                   where e.booking_id = b.id and e.kind = b.status))
                          """)
                      .query(Integer.class)
                      .single())
              .as("cada reserva coa súa historia")
              .isZero();

          String before = jdbc.sql(COUNTS).query(String.class).single();
          assertThat(before).isEqualTo("19/19/15/35");
          runScript(db, SCRIPT);
          assertThat(jdbc.sql(COUNTS).query(String.class).single()).isEqualTo(before);

          // Verín: pasadas, de hoxe e futuras, de persoas ficticias sen conta, e idempotentes.
          String verin = jdbc.sql(VERIN_COUNTS).query(String.class).single();
          assertThat(verin).isEqualTo("11/11/9/18/0");
          runScript(db, VERIN_SCRIPT);
          assertThat(jdbc.sql(VERIN_COUNTS).query(String.class).single()).isEqualTo(verin);
        });
  }

  private static void runScript(String db, String script) {
    try (InputStream in = DemoBookingsIT.class.getClassLoader().getResourceAsStream(script);
        Connection connection =
            DriverManager.getConnection(
                url(db), PostgresContainers.OWNER_USER, PostgresContainers.OWNER_PASSWORD);
        Statement statement = connection.createStatement()) {
      assertThat(in).isNotNull();
      statement.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException | SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Flyway flyway(String db) {
    return Flyway.configure()
        .dataSource(url(db), PostgresContainers.OWNER_USER, PostgresContainers.OWNER_PASSWORD)
        .locations("classpath:db/migration", "classpath:db/demo")
        .placeholders(PostgresContainers.flywayPlaceholders())
        .load();
  }

  private static JdbcClient owner(String db) {
    return JdbcClient.create(
        new SimpleDriverDataSource(
            new org.postgresql.Driver(),
            url(db),
            PostgresContainers.OWNER_USER,
            PostgresContainers.OWNER_PASSWORD));
  }

  private static String url(String db) {
    return POSTGRES.getJdbcUrl().replaceFirst("/furar(\\?|$)", "/" + db + "$1");
  }

  /** Base nova, dono furar_owner (como en produción), borrada ao rematar. */
  private static void inFreshDatabase(Consumer<String> test) {
    String db = "furar_demo_" + UUID.randomUUID().toString().substring(0, 8);
    JdbcClient admin =
        JdbcClient.create(
            new SimpleDriverDataSource(
                new org.postgresql.Driver(),
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()));
    admin.sql("create database " + db + " owner " + PostgresContainers.OWNER_USER).update();
    try {
      test.accept(db);
    } finally {
      admin.sql("drop database if exists " + db + " with (force)").update();
    }
  }
}
