// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.FurarApplication;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.PostgresContainers;
import gal.furar.testsupport.TestJwtConfig;
import gal.furar.testsupport.TestSupportConfig;
import gal.furar.testsupport.TestTokens;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import tools.jackson.databind.JsonNode;

/**
 * D3-05: a historia de demo de Narón ({@code R__demo_naron_history.sql}) enche o panel. Nunha base
 * nova cos scripts de demo, o mes pasado ten datos nos seis gráficos e unha ocupación media entre o
 * 30 e o 80 %, e os doce meses da evolución teñen reservas; a semana en curso, coas reservas xa
 * feitas dos próximos días, non se afunde. Ferrol queda baleiro. Volver aplicar o script non cambia
 * nada; ao día seguinte Flyway volve aplicar as sementes (placeholder {@code demo_day}) e estas só
 * engaden o novo, sen duplicar nin romper nada.
 *
 * <p>Contexto propio (base {@code furar_demo_indicators}, como co perfil {@code demo}); péchase ao
 * rematar para non ocupar a caché de contextos.
 */
@SpringBootTest(
    classes = FurarApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestJwtConfig.class, TestSupportConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoIndicatorsIT {

  static final PostgreSQLContainer POSTGRES = PostgresContainers.shared();
  static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
  static final String DB = "furar_demo_" + UUID.randomUUID().toString().substring(0, 8);
  static final String SCRIPT = "db/demo/R__demo_naron_history.sql";

  /**
   * As sementes de demo na orde de Flyway; todas menos a primeira levan o placeholder {@code
   * demo_day}.
   */
  static final List<String> SEEDS =
      List.of(
          "db/demo/R__demo_municipalities.sql",
          "db/demo/R__demo_municipality_facilities.sql",
          "db/demo/R__demo_municipality_facilities_verin.sql",
          "db/demo/R__demo_naron_bookings.sql",
          SCRIPT,
          "db/demo/R__demo_staff.sql",
          "db/demo/R__demo_verin_bookings.sql");

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
    registry.add("spring.datasource.url", DemoIndicatorsIT::url);
    registry.add("spring.datasource.username", PostgresContainers::appUser);
    registry.add("spring.datasource.password", () -> PostgresContainers.APP_PASSWORD);
    registry.add("spring.flyway.user", () -> PostgresContainers.OWNER_USER);
    registry.add("spring.flyway.password", () -> PostgresContainers.OWNER_PASSWORD);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/demo");
  }

  @LocalServerPort int port;

  @Test
  @Order(1)
  void lastMonthOfNaronFillsTheSixChartsWithARealisticOccupancy() {
    LocalDate lastMonth = LocalDate.now(ZONE).withDayOfMonth(1).minusMonths(1);
    JsonNode panel = panel("naron", "period=MONTH&date=" + lastMonth);

    JsonNode kpis = panel.get("kpis");
    JsonNode occupancy = kpis.get("occupancy").get("value");
    double rate =
        occupancy.get("reservedMinutes").asDouble() / occupancy.get("openMinutes").asDouble();
    System.err.printf(
        "DemoIndicatorsIT %s: %d reservas, %.1f %% ocupación (%.0f/%d min), %.1f h, %d persoas,"
            + " %d céntimos cobrados%n",
        lastMonth,
        kpis.get("bookings").get("value").asLong(),
        100 * rate,
        occupancy.get("reservedMinutes").asDouble(),
        occupancy.get("openMinutes").asLong(),
        kpis.get("reservedHours").get("value").asDouble() / 60,
        kpis.get("people").get("value").asLong(),
        kpis.get("paidCents").get("value").asLong());
    assertThat(rate).as("ocupación media do mes pasado").isBetween(0.30, 0.80);
    for (String kpi : List.of("bookings", "reservedHours", "people", "paidCents")) {
      assertThat(kpis.get(kpi).get("value").asDouble()).as(kpi).isPositive();
      assertThat(kpis.get(kpi).get("previous").asDouble()).as(kpi + " anterior").isPositive();
    }

    // Os seis gráficos, con datos.
    assertThat(sum(panel.get("occupancyByFacility"), f -> f.get("reservedMinutes").asDouble()))
        .as("ocupación por instalación")
        .isPositive();
    assertThat(panel.get("occupancyByFacility")).hasSize(4);
    assertThat(panel.get("dailyOccupancy"))
        .as("ocupación semana a semana")
        .allSatisfy(w -> assertThat(w.get("reservedMinutes").asDouble()).isPositive());
    assertThat(sum(panel.get("peakHours"), c -> c.get("reservedMinutes").asDouble()))
        .as("horas punta")
        .isPositive();
    assertThat(sum(panel.get("cancellations"), c -> c.get("inTime").asDouble()))
        .as("cancelacións en prazo")
        .isPositive();
    // Ingresos cos catro estados: cobrado, pendente, a devolver e devolto.
    for (String state : List.of("paidCents", "pendingCents", "toRefundCents", "refundedCents")) {
      double cents = sum(panel.get("revenue"), r -> r.get(state).asDouble());
      System.err.printf(
          "DemoIndicatorsIT %s ingresos %s: %.0f céntimos%n", lastMonth, state, cents);
      assertThat(cents).as("ingresos " + state).isPositive();
    }
    assertThat(panel.get("people"))
        .as("persoas por mes")
        .hasSize(6)
        .allSatisfy(m -> assertThat(m.get("people").asLong()).isPositive());

    // As tardes enchen máis cás mañás (horas punta).
    assertThat(rate(panel, 19)).isGreaterThan(rate(panel, 10));

    // Doce meses de evolución, todos con reservas, e máis ocupación ca hai un ano.
    for (String kpi : List.of("bookings", "reservedHours", "people", "paidCents")) {
      JsonNode evolution = kpis.get(kpi).get("evolution");
      assertThat(evolution).as(kpi).hasSize(12);
      assertThat(evolution)
          .as(kpi)
          .allSatisfy(p -> assertThat(p.get("value").asDouble()).isPositive());
    }
    JsonNode months = kpis.get("occupancy").get("evolution");
    assertThat(months).hasSize(12);
    assertThat(months)
        .allSatisfy(p -> assertThat(p.get("value").get("reservedMinutes").asDouble()).isPositive());
    assertThat(share(months.get(11).get("value"))).isGreaterThan(share(months.get(0).get("value")));

    // O ano que remata o mes pasado: doce meses con datos.
    JsonNode year =
        panel(
            "naron",
            "period=CUSTOM&from="
                + lastMonth.minusMonths(11)
                + "&to="
                + lastMonth.plusMonths(1).minusDays(1));
    assertThat(year.get("dailyOccupancy"))
        .hasSize(12)
        .allSatisfy(m -> assertThat(m.get("reservedMinutes").asDouble()).isPositive());
    assertThat(year.get("cancellations"))
        .anySatisfy(m -> assertThat(m.get("byMunicipality").asLong()).isPositive());
    List<Long> refunds = new ArrayList<>();
    year.get("revenue")
        .forEach(
            r -> refunds.add(r.get("toRefundCents").asLong() + r.get("refundedCents").asLong()));
    assertThat(refunds).anySatisfy(r -> assertThat(r).isPositive());

    // O mes en curso: informativo (o día 1 só hai as reservas xa feitas dos próximos días).
    JsonNode current = panel("naron", "period=MONTH&date=" + LocalDate.now(ZONE));
    JsonNode currentKpis = current.get("kpis");
    System.err.printf(
        "DemoIndicatorsIT mes en curso: %d reservas, %.1f %% ocupación, %d persoas%n",
        currentKpis.get("bookings").get("value").asLong(),
        100 * share(currentKpis.get("occupancy").get("value")),
        currentKpis.get("people").get("value").asLong());
    for (String state : List.of("paidCents", "pendingCents", "toRefundCents", "refundedCents")) {
      System.err.printf(
          "DemoIndicatorsIT mes en curso ingresos %s: %.0f céntimos%n",
          state, sum(current.get("revenue"), r -> r.get(state).asDouble()));
    }

    // A semana en curso non se afunde: polo menos o 40 % das reservas da anterior (os próximos
    // días só levan as xa feitas, de 2 a 6 días antes).
    JsonNode week = panel("naron", "period=WEEK&date=" + LocalDate.now(ZONE)).get("kpis");
    long thisWeek = week.get("bookings").get("value").asLong();
    long lastWeek = week.get("bookings").get("previous").asLong();
    System.err.printf(
        "DemoIndicatorsIT semana en curso: %d reservas (%d a anterior)%n", thisWeek, lastWeek);
    assertThat(thisWeek).as("semana en curso").isGreaterThanOrEqualTo(Math.round(0.4 * lastWeek));

    // Ferrol queda baleiro a propósito.
    JsonNode ferrol = panel("ferrol", "period=MONTH&date=" + lastMonth);
    assertThat(ferrol.get("kpis").get("bookings").get("value").asLong()).isZero();
  }

  @Test
  @Order(2)
  void theHistoryHasFictitiousPeopleOnlyFreeSlotsAndReapplyingChangesNothing() {
    JdbcClient owner = owner();
    String counts =
        """
        select (select count(*) from booking) || '/' || (select count(*) from booking_occupancy)
               || '/' || (select count(*) from booking_event) || '/' || (select count(*) from season)
               || '/' || (select count(*) from season_hours) || '/' || (select count(*) from block)
               || '/' || (select md5(string_agg(id || code || status || payment_status
                                                || updated_at, ',' order by id)) from booking)
        """;
    String before = owner.sql(counts).query(String.class).single();

    List<String> history =
        owner
            .sql(
                """
                select count(*) || '/' || count(distinct holder_subject) || '/'
                       || count(*) filter (where holder_email not like '%@example.org') || '/'
                       || count(*) filter (where holder_subject !~ '^demo-person-h[0-9]{3}$') || '/'
                       || count(*) filter (where (lower(period) at time zone 'Europe/Madrid')::date
                                                 > current_date + 6) || '/'
                       || count(*) filter (where created_at >= now() or cancelled_at >= now())
                       || '/'
                       || count(*) filter (where lower(period) >= now() and price_kind = 'PAID'
                                                 and payment_status <> 'PENDING') || '/'
                       || count(*) filter (where municipality_id <> :naron) || '/'
                       || count(*) filter (where lower(period) >= now())
                from booking where id::text like '019a0b6e-0000-7000-8001-%'
                """)
            .param("naron", UUID.fromString("019a0b6e-0000-7000-8000-000000000001"))
            .query(String.class)
            .list();
    String[] parts = history.getFirst().split("/");
    System.err.printf(
        "DemoIndicatorsIT historia: %s reservas, %s persoas, %s por xogar%n",
        parts[0], parts[1], parts[8]);
    assertThat(Integer.parseInt(parts[0])).as("reservas").isBetween(4000, 6500);
    assertThat(Integer.parseInt(parts[1])).as("persoas").isBetween(450, 600);
    // Ficticias, ata dentro de seis días, nada feito despois de agora, por xogar sen cobrar e só
    // en Narón; e hai reservas xa feitas para os próximos días.
    assertThat(List.of(parts).subList(2, 8)).containsOnly("0");
    assertThat(Integer.parseInt(parts[8])).as("por xogar").isPositive();

    // Cancelacións en prazo, ao redor do 8 %; a semana de obras, polo concello.
    double cancelled =
        owner
            .sql(
                """
                select avg(case when status = 'CANCELLED_BY_PERSON'
                                 and cancelled_at < cancel_until then 1.0 else 0 end)
                from booking where id::text like '019a0b6e-0000-7000-8001-%'
                """)
            .query(Double.class)
            .single();
    assertThat(cancelled).isBetween(0.06, 0.10);

    // Cada reserva coa súa historia; ningunha confirmada fóra do horario da súa tempada.
    assertThat(
            owner
                .sql(
                    """
                    select count(*) from booking b
                    where not exists (select 1 from booking_event e
                                      where e.booking_id = b.id and e.kind = 'CREATED')
                       or (b.status <> 'CONFIRMED' and not exists (
                             select 1 from booking_event e
                             where e.booking_id = b.id and e.kind = b.status))
                       or (b.status = 'CONFIRMED') <> exists (
                             select 1 from booking_occupancy o
                             where o.booking_id = b.id and o.active)
                    """)
                .query(Integer.class)
                .single())
        .isZero();
    assertThat(
            owner
                .sql(
                    """
                    select count(*) from booking b
                    where b.id::text like '019a0b6e-0000-7000-8001-%'
                      and not exists (
                        select 1 from season s
                        join season_hours h on h.season_id = s.id
                        where s.facility_id = b.facility_id
                          and (lower(b.period) at time zone 'Europe/Madrid')::date
                              between s.start_date and s.end_date
                          and h.day = case
                                when exists (select 1 from public_holiday p
                                             where p.date = (lower(b.period)
                                                             at time zone 'Europe/Madrid')::date)
                                  then 8
                                else extract(isodow from lower(b.period)
                                                         at time zone 'Europe/Madrid')::int end
                          and (lower(b.period) at time zone 'Europe/Madrid')::time
                              >= make_time(h.start_minute / 60, h.start_minute % 60, 0)
                          and (upper(b.period) at time zone 'Europe/Madrid')
                              <= (lower(b.period) at time zone 'Europe/Madrid')::date
                                 + make_interval(mins => h.end_minute))
                    """)
                .query(Integer.class)
                .single())
        .as("dentro do horario")
        .isZero();

    runScript();
    assertThat(owner.sql(counts).query(String.class).single()).isEqualTo(before);
  }

  /**
   * Ao día seguinte: o placeholder {@code demo_day} cambia o checksum das sementes con datas e
   * Flyway vólveas aplicar (sen cambiar nada o mesmo día); co reloxo da base un día adiante, as
   * sementes só engaden o día novo da historia, sen duplicar nin romper, e volver aplicalas segue
   * sen cambiar nada.
   */
  @Test
  @Order(3)
  void theNextDayFlywayReappliesTheSeedsAndTheyOnlyAddTheNewDay() {
    JdbcClient owner = owner();
    String counts =
        """
        select (select count(*) from booking) || '/' || (select count(*) from booking_occupancy)
               || '/' || (select count(*) from booking_event) || '/' || (select count(*) from season)
               || '/' || (select count(*) from block) || '/' || (select count(*) from staff_member)
        """;
    String before = owner.sql(counts).query(String.class).single();
    LocalDate today = LocalDate.now(ZONE);

    assertThat(flyway(today).migrate().migrationsExecuted).as("o mesmo día").isZero();
    assertThat(flyway(today.plusDays(1)).migrate().migrationsExecuted)
        .as("ao día seguinte, as seis sementes con datas")
        .isEqualTo(6);
    assertThat(owner.sql(counts).query(String.class).single()).isEqualTo(before);

    SEEDS.forEach(seed -> run(seed, true));
    String nextDay = owner.sql(counts).query(String.class).single();
    System.err.printf("DemoIndicatorsIT ao día seguinte: %s → %s%n", before, nextDay);
    assertThat(count(nextDay, 0)).as("reservas").isGreaterThan(count(before, 0));
    assertThat(count(nextDay, 3)).as("tempadas").isEqualTo(count(before, 3));
    assertThat(
            owner
                .sql(
                    """
                    select count(*) from booking b
                    where not exists (select 1 from booking_event e
                                      where e.booking_id = b.id and e.kind = 'CREATED')
                       or (b.status = 'CONFIRMED') <> exists (
                             select 1 from booking_occupancy o
                             where o.booking_id = b.id and o.active)
                       or (select count(*) from booking_occupancy o where o.booking_id = b.id) <> 1
                       or b.created_at >= now() + interval '1 day'
                    """)
                .query(Integer.class)
                .single())
        .as("cada reserva unha vez, coa súa ocupación e a súa historia")
        .isZero();
    SEEDS.forEach(seed -> run(seed, true));
    assertThat(owner.sql(counts).query(String.class).single()).isEqualTo(nextDay);
  }

  // --- Axudas ---

  Flyway flyway(LocalDate day) {
    return Flyway.configure()
        .dataSource(url(), PostgresContainers.OWNER_USER, PostgresContainers.OWNER_PASSWORD)
        .locations("classpath:db/migration", "classpath:db/demo")
        .placeholders(Map.of("approle", PostgresContainers.appUser(), "demo_day", day.toString()))
        .load();
  }

  static long count(String counts, int index) {
    return Long.parseLong(counts.split("/")[index]);
  }

  JsonNode panel(String slug, String query) {
    Api.Response response =
        new Api(port)
            .get("/api/v1/municipalities/" + slug + "/indicators?" + query)
            .token(manager(slug))
            .send();
    assertThat(response.status()).as(query + " → " + response).isEqualTo(200);
    return response.json();
  }

  /**
   * Token da persoa xestora de demo do concello ({@code R__demo_staff.sql}, {@code sub} fixo do
   * realm de demo): o rol sae da táboa de pertenzas (ADR-0027).
   */
  static String manager(String slug) {
    String subject =
        switch (slug) {
          case "naron" -> "019a0b6e-0000-7000-8000-000000000902";
          case "ferrol" -> "019a0b6e-0000-7000-8000-000000000903";
          default -> throw new IllegalArgumentException(slug);
        };
    return TestTokens.withoutRolesClaim(subject, Map.of());
  }

  static double sum(JsonNode array, ToDoubleFunction<JsonNode> value) {
    double total = 0;
    for (JsonNode item : array) {
      total += value.applyAsDouble(item);
    }
    return total;
  }

  static double share(JsonNode occupancy) {
    return occupancy.get("reservedMinutes").asDouble() / occupancy.get("openMinutes").asDouble();
  }

  /** Ocupación dunha hora sumando os sete días da semana. */
  static double rate(JsonNode panel, int hour) {
    double reserved = 0;
    double open = 0;
    for (JsonNode cell : panel.get("peakHours")) {
      if (cell.get("hour").asInt() == hour) {
        reserved += cell.get("reservedMinutes").asDouble();
        open += cell.get("openMinutes").asDouble();
      }
    }
    return reserved / open;
  }

  void runScript() {
    run(SCRIPT, false);
  }

  /**
   * Executa unha semente tal cal ou, con {@code tomorrow}, co reloxo da base un día adiante ({@code
   * current_date} e {@code now()}): o que faría Flyway ao día seguinte.
   */
  void run(String script, boolean tomorrow) {
    try (var in = getClass().getClassLoader().getResourceAsStream(script);
        var connection =
            java.sql.DriverManager.getConnection(
                url(), PostgresContainers.OWNER_USER, PostgresContainers.OWNER_PASSWORD);
        var statement = connection.createStatement()) {
      assertThat(in).isNotNull();
      String sql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      if (tomorrow) {
        sql =
            sql.replace("current_date", "(current_date + 1)")
                .replace("now()", "(now() + interval '1 day')");
      }
      statement.execute(sql);
    } catch (java.io.IOException | java.sql.SQLException e) {
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
