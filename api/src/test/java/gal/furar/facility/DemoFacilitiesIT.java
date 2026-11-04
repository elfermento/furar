// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.facility.adapter.out.geocoder.FixedAddressSearch;
import gal.furar.facility.domain.AddressSuggestion;
import gal.furar.facility.domain.SearchArea;
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
 * Instalacións da demo (D1-17) nunha base baleira co perfil {@code demo} ({@code db/demo}): migra
 * (os concellos antes cas instalacións), ningunha queda en borrador salvo a Piscina municipal de
 * Ferrol, que o está a propósito, os prezos de Verín son os da ordenanza, os enderezos da comarca
 * están no buscador fixo e volver aplicar os scripts non duplica nada. O borrador compróbase coas
 * mesmas regras ca {@code FacilityDrafts} (deporte, uso vivo e tempada con tramos que remata hoxe
 * ou despois).
 */
class DemoFacilitiesIT {

  static final PostgreSQLContainer POSTGRES = PostgresContainers.shared();
  static final String SCRIPT = "db/demo/R__demo_municipality_facilities.sql";

  /** As instalacións reais da comarca de Verín (outro script, aplicado despois). */
  static final String VERIN_SCRIPT = "db/demo/R__demo_municipality_facilities_verin.sql";

  /**
   * As tempadas pasadas e os bloqueos da historia de reservas (R__demo_naron_history.sql, D3-05):
   * van na mesma base pero non son deste script; próbaos {@code DemoIndicatorsIT}.
   */
  static final String HISTORY = "019a0b6e-0000-7000-8000-0000000005%";

  static final String DRAFTS =
      """
      select f.name
      from facility f
      where not exists (select 1 from facility_sport s where s.facility_id = f.id)
         or not exists (select 1 from facility_use u
                        where u.facility_id = f.id and u.archived_at is null)
         or not exists (select 1 from season s
                        where s.facility_id = f.id
                          and s.end_date >= (now() at time zone 'Europe/Madrid')::date
                          and exists (select 1 from season_hours h where h.season_id = s.id))
      order by 1
      """;

  static final String COUNTS =
      """
      select (select count(*) from facility) || '/' || (select count(*) from facility_sport)
             || '/' || (select count(*) from facility_amenity)
             || '/' || (select count(*) from facility_use)
             || '/' || (select count(*) from facility_use_part)
             || '/' || (select count(*) from facility_use_period)
             || '/' || (select count(*) from season where id::text not like :history)
             || '/' || (select count(*) from season_hours
                        where season_id::text not like :history)
             || '/' || (select count(*) from schedule_exception)
             || '/' || (select count(*) from block where id::text not like :history)
             || '/' || (select count(*) from custom_field_value where target = 'FACILITY')
      """;

  @Test
  void anEmptyDatabaseGetsTheDemoFacilitiesWithOnlyOneDraft() {
    inFreshDatabase(
        db -> {
          flyway(db).migrate();
          JdbcClient jdbc = owner(db);

          assertThat(
                  jdbc.sql(
                          """
                          select m.slug || ':' || f.name from facility f
                          join municipality m on m.id = f.municipality_id order by 1
                          """)
                      .query(String.class)
                      .list())
              .containsExactly(
                  "castrelo-do-val:Pavillón municipal (Centro Multiusos)",
                  "castrelo-do-val:Piscina municipal",
                  "castrelo-do-val:Pista de pádel municipal",
                  "castrelo-do-val:Pista multideporte",
                  "cualedro:Piscinas municipais",
                  "cualedro:Pista multixogos",
                  "ferrol:Piscina municipal",
                  "ferrol:Pista de atletismo da Malata",
                  "laza:Pavillón polideportivo municipal",
                  "laza:Pista de pádel de O Toural",
                  "laza:Pista polideportiva de O Toural",
                  "monterrei:Piscinas municipais",
                  "monterrei:Pistas de pádel de Vilaza",
                  "naron:Campo de fútbol 1 de O Alto",
                  "naron:Pavillón de Piñeiros",
                  "naron:Pista de pádel 1 de O Alto",
                  "naron:Pista de pádel 2 de O Alto",
                  "oimbra:Pavillón polideportivo municipal",
                  "oimbra:Piscinas municipais",
                  "oimbra:Pista de pádel",
                  "rios:Piscina municipal",
                  "verin:Campo de Fútbol José Arjiz",
                  "verin:Campo de fútbol de A Granxa",
                  "verin:Frontón municipal",
                  "verin:Pavillón Municipal de Deportes",
                  "verin:Pavillón Polideportivo Municipal de A Granxa",
                  "verin:Piscina Climatizada Municipal",
                  "verin:Piscinas descubertas municipais de A Granxa",
                  "verin:Pista de tenis de A Granxa",
                  "verin:Pista polideportiva exterior de A Granxa",
                  "verin:Pistas de pádel de A Granxa",
                  "vilardevos:Piscinas municipais");
          // Só a de Ferrol: as piscinas de verán da comarca levan a tempada do verán seguinte.
          assertThat(
                  jdbc.sql(DRAFTS.replace("select f.name", "select f.name || ' ' || f.ine_code"))
                      .query(String.class)
                      .list())
              .containsExactly("Piscina municipal 15036");

          // Verín: prezos da ordenanza (non empadroados, sen luz), en céntimos; null = sen prezo.
          assertThat(
                  jdbc.sql(
                          """
                          select f.name || ' · ' || u.name || ' · ' || u.price_kind || ' '
                                 || coalesce(u.price_cents::text, '-')
                          from facility_use u join facility f on f.id = u.facility_id
                          where f.ine_code = '32085' and u.price_kind = 'PAID'
                          order by 1
                          """)
                      .query(String.class)
                      .list())
              .containsExactly(
                  "Campo de fútbol de A Granxa · Fútbol 11 · PAID 5000",
                  "Campo de fútbol de A Granxa · Fútbol 7 · PAID 3750",
                  "Campo de fútbol de A Granxa · Partido de fútbol (2 horas) · PAID 6250",
                  "Frontón municipal · Frontón · PAID 380",
                  "Pavillón Municipal de Deportes · Pista · PAID 1250",
                  "Pavillón Polideportivo Municipal de A Granxa · Pista · PAID 1250",
                  "Piscina Climatizada Municipal · Baño libre adultos · PAID 500",
                  "Piscina Climatizada Municipal · Baño libre infantil · PAID 400",
                  "Piscinas descubertas municipais de A Granxa · Entrada adultos · PAID 175",
                  "Piscinas descubertas municipais de A Granxa · Entrada infantil (de 2 a 14 anos)"
                      + " · PAID 90",
                  "Pista de tenis de A Granxa · Partido · PAID 380",
                  "Pistas de pádel de A Granxa · Partido · PAID 380");
          // Cada enderezo das instalacións da comarca está no buscador fixo (ADR-0023).
          FixedAddressSearch fixed = new FixedAddressSearch();
          assertThat(
                  jdbc.sql(
                          """
                          select distinct street, coalesce(' ' || street_number, '') || ', '
                                                  || locality, ine_code
                          from facility where ine_code not like '15%'
                          """)
                      .query(
                          (rs, n) ->
                              fixed
                                  .suggest(rs.getString(1), new SearchArea(null, rs.getString(3)))
                                  .stream()
                                  .map(AddressSuggestion::label)
                                  .toList()
                                  .contains(rs.getString(1) + rs.getString(2)))
                      .list())
              .hasSize(18)
              .containsOnly(true);

          // Enteira, partes e prazas; un só uso que bloquea os demais, coas súas datas.
          assertThat(
                  jdbc.sql("select distinct rental_mode from facility_use order by 1")
                      .query(String.class)
                      .list())
              .containsExactly("PARTS", "SEATS", "WHOLE");
          assertThat(
                  jdbc.sql(
                          """
                          select u.name from facility_use u
                          join facility_use_period p on p.use_id = u.id
                          where u.blocks_others
                          """)
                      .query(String.class)
                      .list())
              .containsExactly("Torneo de Nadal");

          // O bloqueo: de 8:00 a 12:00 en Galicia, gardado como tstzrange.
          assertThat(
                  jdbc.sql(
                          """
                          select to_char(lower(period) at time zone 'Europe/Madrid', 'HH24:MI')
                                 || '-'
                                 || to_char(upper(period) at time zone 'Europe/Madrid', 'HH24:MI')
                                 || ' '
                                 || ((lower(period) at time zone 'Europe/Madrid')::date = date)
                          from block where id::text not like :history
                          """)
                      .param("history", HISTORY)
                      .query(String.class)
                      .list())
              .containsExactly("08:00-12:00 true");

          // Un campo propio das instalacións (bloque 1b), público, con valor en tres de Narón.
          assertThat(
                  jdbc.sql(
                          """
                          select c.code || ':' || u.visibility || ':' || u.required
                          from custom_field_usage u join custom_field c on c.id = u.field_id
                          where u.target = 'FACILITY'
                          """)
                      .query(String.class)
                      .list())
              .containsExactly("bike_parking:PUBLIC:false");
          assertThat(
                  jdbc.sql(
                          """
                          select f.name || '=' || v.bool_value from custom_field_value v
                          join facility f on f.id = v.entity_id
                          where v.target = 'FACILITY' order by 1
                          """)
                      .query(String.class)
                      .list())
              .containsExactly(
                  "Campo de fútbol 1 de O Alto=true",
                  "Pavillón de Piñeiros=false",
                  "Pista de pádel 1 de O Alto=true");

          // A fila de festivos (8) é a do domingo en cada tempada.
          assertThat(
                  jdbc.sql(
                          """
                          select count(*) from season s
                          where (select array_agg(int4range(start_minute, end_minute)
                                                  order by start_minute)
                                 from season_hours where season_id = s.id and day = 8)
                                is distinct from
                                (select array_agg(int4range(start_minute, end_minute)
                                                  order by start_minute)
                                 from season_hours where season_id = s.id and day = 7)
                          """)
                      .query(Integer.class)
                      .single())
              .isZero();

          // Repetible e idempotente: volver aplicar o script non engade nin cambia nada.
          String before = jdbc.sql(COUNTS).param("history", HISTORY).query(String.class).single();
          // Narón e Ferrol, 6 instalacións; a comarca de Verín, 26.
          assertThat(before).isEqualTo("32/48/45/42/11/1/34/292/3/1/3");
          runScript(db, SCRIPT);
          runScript(db, VERIN_SCRIPT);
          assertThat(jdbc.sql(COUNTS).param("history", HISTORY).query(String.class).single())
              .isEqualTo(before);
        });
  }

  private static void runScript(String db, String script) {
    try (InputStream in = DemoFacilitiesIT.class.getClassLoader().getResourceAsStream(script);
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
