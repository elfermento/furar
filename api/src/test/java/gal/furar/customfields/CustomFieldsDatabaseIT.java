// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.testsupport.AbstractIT;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.NestedExceptionUtils;

/**
 * D18 (especificación 1b): a base de datos garante o tipo de cada valor e a coherencia de opcións,
 * usos e datos persoais, tamén para quen escriba por SQL. Escribe como dono (sen RLS) para probar
 * só as restricións.
 */
class CustomFieldsDatabaseIT extends AbstractIT {

  static final String CHECK = "23514";
  static final String FOREIGN_KEY = "23503";
  static final String UNIQUE = "23505";

  UUID municipality;
  UUID text;
  UUID zone;
  UUID level;

  @BeforeEach
  void setUp() {
    String slug = uniqueSlug("db");
    municipality = id(createMunicipality(slug));
    text = insertField(municipality, "note", "STAFF");
    Map<String, Object> options =
        Map.of(
            "options",
            List.of(
                Map.of("code", "north", "label", Map.of("gl", "Norte", "es", "Norte")),
                Map.of("code", "south", "label", Map.of("gl", "Sur", "es", "Sur"))));
    zone = id(createField(slug, field("zone", "SELECT", "PUBLIC", options)));
    level = id(createField(slug, field("levels", "MULTI_SELECT", "PUBLIC", options)));
  }

  @Test
  void aValueGoesOnlyInTheColumnOfItsType() {
    assertState(
        CHECK,
        "insert into custom_field_value (id, municipality_id, field_id, field_type, target,"
            + " entity_id, number_value) values (?, ?, ?, 'TEXT', 'MUNICIPALITY', ?, 1)",
        UUID.randomUUID(),
        municipality,
        text,
        municipality);
    // A copia do tipo ten que casar co campo.
    assertState(
        FOREIGN_KEY,
        "insert into custom_field_value (id, municipality_id, field_id, field_type, target,"
            + " entity_id, number_value) values (?, ?, ?, 'NUMBER', 'MUNICIPALITY', ?, 1)",
        UUID.randomUUID(),
        municipality,
        text,
        municipality);
    UUID ok = insertText(text, "ben");
    assertThat(ok).isNotNull();
  }

  @Test
  void optionsMustBelongToTheField() {
    UUID optionOfLevels = option(level, "north");
    assertState(
        FOREIGN_KEY,
        "insert into custom_field_value (id, municipality_id, field_id, field_type, target,"
            + " entity_id, option_id) values (?, ?, ?, 'SELECT', 'MUNICIPALITY', ?, ?)",
        UUID.randomUUID(),
        municipality,
        zone,
        municipality,
        optionOfLevels);

    UUID value = UUID.randomUUID();
    jdbc.sql(
            "insert into custom_field_value (id, municipality_id, field_id, field_type, target,"
                + " entity_id) values (?, ?, ?, 'MULTI_SELECT', 'MUNICIPALITY', ?)")
        .params(value, municipality, level, municipality)
        .update();
    assertState(
        FOREIGN_KEY,
        "insert into custom_field_value_option (municipality_id, value_id, field_id, option_id)"
            + " values (?, ?, ?, ?)",
        municipality,
        value,
        level,
        option(zone, "north"));
    // Un campo de texto non ten opcións.
    assertState(
        FOREIGN_KEY,
        "insert into custom_field_option (id, municipality_id, field_id, field_type, code, label)"
            + " values (?, ?, ?, 'SELECT', 'x', '{\"gl\": \"x\", \"es\": \"x\"}')",
        UUID.randomUUID(),
        municipality,
        text);
  }

  @Test
  void personalDataIsNeverPublicInAnyUsage() {
    // O uso de zone é público: marcar o campo como dato persoal propágase ao uso e falla.
    assertState(CHECK, "update custom_field set personal_data = true where id = ?", zone);
    jdbc.sql("update custom_field set personal_data = true where id = ?").params(text).update();
    Boolean copied =
        jdbc.sql("select personal_data from custom_field_usage where field_id = ?")
            .params(text)
            .query(Boolean.class)
            .single();
    assertThat(copied).as("on update cascade").isTrue();
    assertState(
        CHECK, "update custom_field_usage set visibility = 'PUBLIC' where field_id = ?", text);
  }

  @Test
  void theTypeAndTheUsageOfAFieldWithValuesCannotChange() {
    insertText(text, "valor");
    assertState(FOREIGN_KEY, "update custom_field set type = 'LONG_TEXT' where id = ?", text);
    assertState(FOREIGN_KEY, "delete from custom_field_usage where field_id = ?", text);
    // Un valor só nun destino onde se usa o campo.
    assertState(
        FOREIGN_KEY,
        "insert into custom_field_value (id, municipality_id, field_id, field_type, target,"
            + " entity_id, text_value) values (?, ?, ?, 'TEXT', 'FACILITY', ?, 'x')",
        UUID.randomUUID(),
        municipality,
        text,
        UUID.randomUUID());
  }

  /** Revisión do 2026-11-04: un uso por campo; a reserva sen campos públicos. */
  @Test
  void aFieldHasOneUsageAndBookingFieldsAreNotPublic() {
    assertState(
        UNIQUE,
        "insert into custom_field_usage (id, municipality_id, field_id, target, required,"
            + " visibility, personal_data) values (?, ?, ?, 'FACILITY', false, 'STAFF', false)",
        UUID.randomUUID(),
        municipality,
        text);
    UUID booking = insertField(municipality, "level", "STAFF");
    jdbc.sql("update custom_field_usage set target = 'BOOKING' where field_id = ?")
        .params(booking)
        .update();
    assertState(
        CHECK, "update custom_field_usage set visibility = 'PUBLIC' where field_id = ?", booking);
    // Unha opción con valores non se pode borrar (a API responde antes, 409).
    UUID value = UUID.randomUUID();
    jdbc.sql(
            "insert into custom_field_value (id, municipality_id, field_id, field_type, target,"
                + " entity_id, option_id) values (?, ?, ?, 'SELECT', 'MUNICIPALITY', ?, ?)")
        .params(value, municipality, zone, municipality, option(zone, "south"))
        .update();
    assertState(FOREIGN_KEY, "delete from custom_field_option where id = ?", option(zone, "south"));
  }

  private UUID insertText(UUID field, String value) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            "insert into custom_field_value (id, municipality_id, field_id, field_type, target,"
                + " entity_id, text_value) values (?, ?, ?, 'TEXT', 'MUNICIPALITY', ?, ?)")
        .params(id, municipality, field, municipality, value)
        .update();
    return id;
  }

  private UUID option(UUID field, String code) {
    return jdbc.sql("select id from custom_field_option where field_id = ? and code = ?")
        .params(field, code)
        .query(UUID.class)
        .single();
  }

  private void assertState(String state, String sql, Object... params) {
    assertThatThrownBy(() -> jdbc.sql(sql).params(params).update())
        .satisfies(
            e -> {
              Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
              assertThat(root).as(sql).isInstanceOf(SQLException.class);
              assertThat(((SQLException) root).getSQLState())
                  .as(root.getMessage())
                  .isEqualTo(state);
            });
  }
}
