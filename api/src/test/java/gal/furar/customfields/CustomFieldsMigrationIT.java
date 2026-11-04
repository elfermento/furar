// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.testsupport.PostgresContainers;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * D18 (plan do dominio 1, D1-10a): a migración a táboas tipadas sobre unha base co esquema anterior
 * e valores dos 7 tipos copia definicións, opcións, usos e valores sen perda; se un valor non ten
 * onde ir, a migración enteira falla (bloque {@code DO} cos recontos); e unha base baleira co
 * perfil {@code demo} (db/demo) migra. Cada caso nunha base propia do mesmo contedor, que se borra
 * ao final.
 */
class CustomFieldsMigrationIT {

  static final String BEFORE = "202610231130";
  static final String BEFORE_SINGLE_USAGE = "202611041000";
  static final UUID NARON = UUID.fromString("019a0b6e-0000-7000-8000-0000000000f1");
  static final PostgreSQLContainer POSTGRES = PostgresContainers.shared();

  @Test
  void oldSchemaWithValuesOfTheSevenTypesIsCopied() {
    inFreshDatabase(
        db -> {
          flyway(db, BEFORE, "classpath:db/migration").migrate();
          JdbcClient jdbc = owner(db);
          seedOldModel(jdbc, true);
          flyway(db, null, "classpath:db/migration").migrate();

          Map<String, String> stored = new TreeMap<>();
          jdbc.sql(
                  """
                  select f.code,
                         concat_ws('|', f.type, u.target, u.visibility, u.required::text,
                                   't:' || v.text_value, 'n:' || v.number_value::text,
                                   'd:' || v.date_value::text, 'b:' || v.bool_value::text,
                                   'o:' || o.code,
                                   (select 'm:' || string_agg(mo.code, ',' order by mo.code)
                                    from custom_field_value_option c
                                    join custom_field_option mo on mo.id = c.option_id
                                    where c.value_id = v.id)) as row
                  from custom_field f
                  join custom_field_usage u on u.field_id = f.id
                  left join custom_field_value v on v.field_id = f.id and v.entity_id = :m
                  left join custom_field_option o on o.id = v.option_id
                  where f.municipality_id = :m
                  """)
              .param("m", NARON)
              .query((rs, n) -> stored.put(rs.getString("code"), rs.getString("row")))
              .list();
          assertThat(stored)
              .containsEntry("note", "TEXT|MUNICIPALITY|PUBLIC|true|t:Pavillón «O Alto»")
              .containsEntry("story", "LONG_TEXT|MUNICIPALITY|STAFF|false|t:liña 1\nliña 2")
              .containsEntry("amount", "NUMBER|MUNICIPALITY|PUBLIC|false|n:12345.6789")
              .containsEntry("opened_on", "DATE|MUNICIPALITY|PUBLIC|false|d:2026-02-28")
              .containsEntry("has_defibrillator", "BOOLEAN|MUNICIPALITY|PUBLIC|false|b:false")
              .containsEntry("zone", "SELECT|MUNICIPALITY|PUBLIC|false|o:old")
              .containsEntry("zones", "MULTI_SELECT|MUNICIPALITY|OWNER|false|m:north,south")
              .containsEntry("empty_one", "TEXT|MUNICIPALITY|STAFF|false")
              .hasSize(8);

          // Regras e opcións pasan a columnas e filas; a opción arquivada segue arquivada.
          assertThat(
                  jdbc.sql(
                          "select concat_ws('|', max_length, text_format) from custom_field"
                              + " where code = 'note'")
                      .query(String.class)
                      .single())
              .isEqualTo("20|URL");
          assertThat(
                  jdbc.sql(
                          "select concat_ws('|', decimals, min_number, max_number) from custom_field"
                              + " where code = 'amount'")
                      .query(String.class)
                      .single())
              .isEqualTo("4|0|99999");
          assertThat(
                  jdbc.sql(
                          "select string_agg(o.code || ':' || o.inactive, ',' order by o.code)"
                              + " from custom_field_option o join custom_field f on f.id ="
                              + " o.field_id where f.code = 'zone'")
                      .query(String.class)
                      .single())
              .isEqualTo("north:false,old:true,south:false");

          // O modelo anterior desaparece.
          assertThat(
                  jdbc.sql(
                          "select count(*) from information_schema.columns where table_name ="
                              + " 'municipality' and column_name = 'custom_fields'")
                      .query(Integer.class)
                      .single())
              .isZero();
          assertThat(
                  jdbc.sql("select to_regclass('custom_field_definition') is null")
                      .query(Boolean.class)
                      .single())
              .isTrue();
        });
  }

  @Test
  void aValueWithoutDefinitionStopsTheMigration() {
    inFreshDatabase(
        db -> {
          flyway(db, BEFORE, "classpath:db/migration").migrate();
          JdbcClient jdbc = owner(db);
          seedOldModel(jdbc, false);
          jdbc.sql(
                  "update municipality set custom_fields = custom_fields || '{\"ghost\": \"x\"}'"
                      + " where id = :m")
              .param("m", NARON)
              .update();
          assertThatThrownBy(() -> flyway(db, null, "classpath:db/migration").migrate())
              .isInstanceOf(FlywayException.class)
              .hasMessageContaining("recontos distintos");
          // Nada a medias: a migración corre nunha transacción.
          assertThat(
                  jdbc.sql("select to_regclass('custom_field_definition') is not null")
                      .query(Boolean.class)
                      .single())
              .isTrue();
          assertThat(
                  jdbc.sql("select to_regclass('custom_field') is null")
                      .query(Boolean.class)
                      .single())
              .isTrue();
        });
  }

  @Test
  void anEmptyDatabaseMigratesWithTheDemoData() {
    inFreshDatabase(
        db -> {
          flyway(db, null, "classpath:db/migration", "classpath:db/demo").migrate();
          List<String> values =
              owner(db)
                  .sql(
                      """
                      select m.slug || ':' || f.code || '=' || v.text_value
                      from custom_field_value v
                      join custom_field f on f.id = v.field_id
                      join municipality m on m.id = v.entity_id
                      order by 1
                      """)
                  .query(String.class)
                  .list();
          assertThat(values)
              .containsExactly("naron:ridega_code=15-054", "naron:web=https://naron.gal");
          // Repetible e idempotente: volver aplicala non duplica nada.
          flyway(db, null, "classpath:db/migration", "classpath:db/demo").migrate();
          assertThat(
                  owner(db)
                      .sql("select count(*) from custom_field_value where target = 'MUNICIPALITY'")
                      .query(Integer.class)
                      .single())
              .isEqualTo(2);
        });
  }

  /**
   * Revisión do 2026-11-04 (un campo, un sitio): un campo usado en dous destinos pártese. Queda co
   * destino con máis valores; o outro pasa a un campo novo «<código>_<destino>» coas mesmas opcións
   * e os seus valores, sen perda. O arquivado pasa a desactivado.
   */
  @Test
  void aFieldUsedInTwoPlacesIsSplitWithoutLosingValues() {
    inFreshDatabase(
        db -> {
          flyway(db, BEFORE, "classpath:db/migration").migrate();
          JdbcClient jdbc = owner(db);
          seedOldModel(jdbc, true);
          flyway(db, BEFORE_SINGLE_USAGE, "classpath:db/migration").migrate();
          UUID pool = UUID.randomUUID();
          UUID court = UUID.randomUUID();
          jdbc.sql(
                  """
                  insert into custom_field_usage (id, municipality_id, field_id, target, required,
                                                  visibility, personal_data)
                  select gen_random_uuid(), municipality_id, id, 'FACILITY', false, 'STAFF',
                         personal_data
                  from custom_field where code in ('zone', 'zones', 'note')
                  """)
              .update();
          for (UUID facility : List.of(pool, court)) {
            jdbc.sql(
                    """
                    insert into custom_field_value (id, municipality_id, field_id, field_type,
                                                    target, entity_id, option_id)
                    select gen_random_uuid(), f.municipality_id, f.id, f.type, 'FACILITY', :e, o.id
                    from custom_field f join custom_field_option o on o.field_id = f.id
                    where f.code = 'zone' and o.code = 'north'
                    """)
                .param("e", facility)
                .update();
          }
          for (UUID facility : List.of(pool, court)) {
            UUID choices = UUID.randomUUID();
            jdbc.sql(
                    """
                    insert into custom_field_value (id, municipality_id, field_id, field_type,
                                                    target, entity_id)
                    select :v, municipality_id, id, type, 'FACILITY', :e
                    from custom_field where code = 'zones'
                    """)
                .param("v", choices)
                .param("e", facility)
                .update();
            jdbc.sql(
                    """
                    insert into custom_field_value_option (municipality_id, value_id, field_id,
                                                           option_id)
                    select f.municipality_id, :v, f.id, o.id
                    from custom_field f join custom_field_option o on o.field_id = f.id
                    where f.code = 'zones' and o.code in ('north', 'old')
                    """)
                .param("v", choices)
                .update();
          }
          jdbc.sql("update custom_field set status = 'ARCHIVED' where code = 'story'").update();

          flyway(db, null, "classpath:db/migration").migrate();

          Map<String, String> places = new TreeMap<>();
          jdbc.sql(
                  """
                  select f.code, u.target, f.status,
                         (select count(*) from custom_field_value v where v.field_id = f.id) as n
                  from custom_field f join custom_field_usage u on u.field_id = f.id
                  """)
              .query(
                  (rs, n) ->
                      places.put(
                          rs.getString("code"),
                          rs.getString("target")
                              + "|"
                              + rs.getString("status")
                              + "|"
                              + rs.getLong("n")))
              .list();
          assertThat(places)
              .containsEntry("zone", "FACILITY|ACTIVE|2")
              .containsEntry("zone_municipality", "MUNICIPALITY|ACTIVE|1")
              .containsEntry("zones", "FACILITY|ACTIVE|2")
              .containsEntry("zones_municipality", "MUNICIPALITY|ACTIVE|1")
              .containsEntry("note", "MUNICIPALITY|ACTIVE|1")
              .containsEntry("note_facility", "FACILITY|ACTIVE|0")
              .containsEntry("story", "MUNICIPALITY|INACTIVE|1")
              .hasSize(11);
          // A opción escollida segue sendo a mesma, agora do campo novo.
          assertThat(
                  jdbc.sql(
                          """
                          select o.code from custom_field_value v
                          join custom_field f on f.id = v.field_id
                          join custom_field_option o on o.id = v.option_id and o.field_id = f.id
                          where f.code = 'zone_municipality'
                          """)
                      .query(String.class)
                      .single())
              .isEqualTo("old");
          assertThat(
                  jdbc.sql(
                          """
                          select string_agg(o.code || ':' || o.inactive, ',' order by o.code)
                          from custom_field_value_option c
                          join custom_field f on f.id = c.field_id
                          join custom_field_option o on o.id = c.option_id and o.field_id = f.id
                          where f.code = 'zones_municipality'
                          """)
                      .query(String.class)
                      .single())
              .isEqualTo("north:false,south:false");
        });
  }

  /** O modelo anterior: definicións con opcións e regras en jsonb e valores no concello. */
  private static void seedOldModel(JdbcClient jdbc, boolean withValues) {
    jdbc.sql(
            """
            insert into municipality (id, slug, name, languages, default_language, theme,
                                      custom_fields, created_at, updated_at, version)
            values (:m, 'naron', 'Narón', array['gl', 'es'], 'gl',
                    '{"primary": "#0b4f8a", "onPrimary": "#ffffff", "accent": "#b3541e",
                      "background": "#ffffff", "text": "#1a1a1a"}',
                    cast(:values as jsonb), now(), now(), 3)
            """)
        .param("m", NARON)
        .param(
            "values",
            withValues
                ? """
                  {"note": "Pavillón «O Alto»", "story": "liña 1\\nliña 2", "amount": 12345.6789,
                   "opened_on": "2026-02-28", "has_defibrillator": false, "zone": "old",
                   "zones": ["south", "north"], "empty_one": null}
                  """
                : "{}")
        .update();
    String options =
        """
        [{"code": "north", "label": {"gl": "Norte", "es": "Norte"}, "archived": false},
         {"code": "south", "label": {"gl": "Sur", "es": "Sur"}},
         {"code": "old", "label": {"gl": "Vella", "es": "Vieja"}, "archived": true}]
        """;
    definition(
        jdbc, "note", "TEXT", "{\"maxLength\": 20, \"format\": \"URL\"}", "[]", true, "PUBLIC");
    definition(jdbc, "story", "LONG_TEXT", "{}", "[]", false, "STAFF");
    definition(
        jdbc,
        "amount",
        "NUMBER",
        "{\"decimals\": 4, \"min\": 0, \"max\": 99999}",
        "[]",
        false,
        "PUBLIC");
    definition(jdbc, "opened_on", "DATE", "{}", "[]", false, "PUBLIC");
    definition(jdbc, "has_defibrillator", "BOOLEAN", "{}", "[]", false, "PUBLIC");
    definition(jdbc, "zone", "SELECT", "{}", options, false, "PUBLIC");
    definition(jdbc, "zones", "MULTI_SELECT", "{\"maxItems\": 2}", options, false, "OWNER");
    definition(jdbc, "empty_one", "TEXT", "{}", "[]", false, "STAFF");
  }

  private static void definition(
      JdbcClient jdbc,
      String code,
      String type,
      String rules,
      String options,
      boolean required,
      String visibility) {
    jdbc.sql(
            """
            insert into custom_field_definition
              (id, municipality_id, target, code, type, label, required, rules, options, position,
               visibility, personal_data, status, created_at, updated_at, version)
            values (:id, :m, 'MUNICIPALITY', :code, :type, '{"gl": "Etiqueta", "es": "Etiqueta"}',
                    :required, cast(:rules as jsonb), cast(:options as jsonb), 0, :visibility,
                    false, 'ACTIVE', now(), now(), 0)
            """)
        .param("id", UUID.randomUUID())
        .param("m", NARON)
        .param("code", code)
        .param("type", type)
        .param("required", required)
        .param("rules", rules)
        .param("options", options)
        .param("visibility", visibility)
        .update();
  }

  private static Flyway flyway(String db, String target, String... locations) {
    var config =
        Flyway.configure()
            .dataSource(url(db), PostgresContainers.OWNER_USER, PostgresContainers.OWNER_PASSWORD)
            .locations(locations)
            .placeholders(PostgresContainers.flywayPlaceholders());
    if (target != null) {
      config.target(target);
    }
    return config.load();
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
    String db = "furar_mig_" + UUID.randomUUID().toString().substring(0, 8);
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
