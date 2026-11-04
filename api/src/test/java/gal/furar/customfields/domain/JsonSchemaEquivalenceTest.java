// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

import static gal.furar.customfields.domain.Definitions.define;
import static gal.furar.customfields.domain.Definitions.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import gal.furar.shared.domain.Audience;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.DomainException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * D18 (ADR-0011): o JSON Schema xerado e o validador Java aceptan e rexeitan o mesmo. O corpus
 * xérase desde as definicións: valores frontera de cada tipo e regra (mín−1, mín, máx, máx+1,
 * opción arquivada, tipo errado, clave descoñecida…). As regras que JSON Schema non pode expresar
 * (rango de datas) márcanse «só Java»: aí Java ten que rexeitar e o esquema non pode ser máis
 * estrito.
 */
class JsonSchemaEquivalenceTest {

  static final JsonMapper JSON =
      JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
  static final SchemaRegistry REGISTRY =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

  record Case(String name, Map<String, Object> values, boolean javaOnly) {}

  @Test
  void schemaAndValidatorAgreeOnTheGeneratedCorpus() {
    for (boolean required : List.of(false, true)) {
      List<CustomFieldDefinition> defs = definitions(required);
      Schema schema =
          REGISTRY.getSchema(
              JSON.writeValueAsString(
                  JsonSchemaGenerator.generate(CustomFieldTarget.MUNICIPALITY, defs)));
      List<Case> corpus = corpus(defs);
      assertThat(corpus).hasSizeGreaterThan(60);
      List<String> mismatches = new ArrayList<>();
      int accepted = 0;
      for (Case c : corpus) {
        String json = JSON.writeValueAsString(c.values());
        boolean java = javaAccepts(defs, json);
        boolean jsonSchema = schemaAccepts(schema, json);
        accepted += java ? 1 : 0;
        if (c.javaOnly()) {
          if (java) {
            mismatches.add("java-only " + c.name() + " java=" + java + " schema=" + jsonSchema);
          }
        } else if (java != jsonSchema) {
          mismatches.add(c.name() + " " + json + " java=" + java + " schema=" + jsonSchema);
        }
      }
      assertThat(mismatches).as("required=" + required).isEmpty();
      assertThat(accepted).as("the corpus has valid cases").isGreaterThan(15);
      assertThat(corpus.size() - accepted).as("and invalid ones").isGreaterThan(30);
    }
  }

  @Test
  void schemaShape() {
    Map<String, Object> schema =
        JsonSchemaGenerator.generate(CustomFieldTarget.MUNICIPALITY, definitions(true));
    assertThat(schema)
        .containsEntry("$schema", JsonSchemaGenerator.DIALECT)
        .containsEntry("additionalProperties", false)
        .containsKey("required");
    assertThat(JSON.writeValueAsString(schema))
        .contains("\"x-furar-minDate\":\"2026-01-01\"", "\"x-furar-type\":\"MULTI_SELECT\"");
  }

  static List<CustomFieldDefinition> definitions(boolean required) {
    return List.of(
        define("short_text", FieldType.TEXT, required, Definitions.text(2, 5, null), List.of()),
        define(
            "mail",
            FieldType.TEXT,
            required,
            Definitions.text(null, null, TextFormat.EMAIL),
            List.of()),
        define("bio", FieldType.LONG_TEXT, required, Definitions.text(null, 20, null), List.of()),
        define("amount", FieldType.NUMBER, required, Definitions.number("0", "100", 2), List.of()),
        define("count", FieldType.NUMBER, required, Definitions.number("1", null, 0), List.of()),
        define("free_number", FieldType.NUMBER, required, FieldRules.NONE, List.of()),
        define(
            "day",
            FieldType.DATE,
            required,
            Definitions.date("2026-01-01", "2026-12-31"),
            List.of()),
        define("flag", FieldType.BOOLEAN, required, FieldRules.NONE, List.of()),
        define("zone", FieldType.SELECT, required, FieldRules.NONE, options()),
        define("zones", FieldType.MULTI_SELECT, required, Definitions.items(1, 2), options()));
  }

  /** Corpus xerado desde as definicións: un obxecto válido e unha variación por frontera. */
  static List<Case> corpus(List<CustomFieldDefinition> defs) {
    Map<String, Object> base = new LinkedHashMap<>();
    defs.forEach(d -> base.put(d.code(), validValue(d)));
    List<Case> cases = new ArrayList<>();
    cases.add(new Case("all valid", base, false));
    cases.add(new Case("empty object", Map.of(), false));
    cases.add(new Case("unknown key", with(base, "unknown_key", "x"), false));
    for (CustomFieldDefinition d : defs) {
      String code = d.code();
      cases.add(new Case(code + " missing", without(base, code), false));
      cases.add(new Case(code + " null", with(base, code, null), false));
      FieldRules r = d.rules();
      switch (d.type()) {
        case TEXT, LONG_TEXT -> {
          int min = r.minLength() == null ? 0 : r.minLength();
          int max = r.effectiveMaxLength(d.type());
          if (r.format() == null) {
            for (int len : List.of(0, Math.max(min - 1, 0), min, max, max + 1)) {
              cases.add(
                  new Case(code + " length " + len, with(base, code, "ñ".repeat(len)), false));
            }
          } else {
            cases.add(new Case(code + " format ok", with(base, code, "a@b.gal"), false));
            cases.add(new Case(code + " format bad", with(base, code, "a@b"), false));
            cases.add(new Case(code + " empty", with(base, code, ""), false));
          }
          cases.add(new Case(code + " wrong type", with(base, code, 7), false));
        }
        case NUMBER -> {
          int decimals = r.decimals() == null ? FieldRules.MAX_DECIMALS : r.decimals();
          BigDecimal step = BigDecimal.ONE.movePointLeft(decimals);
          if (r.min() != null) {
            cases.add(new Case(code + " min", with(base, code, r.min()), false));
            cases.add(new Case(code + " min-1", with(base, code, r.min().subtract(step)), false));
          }
          if (r.max() != null) {
            cases.add(new Case(code + " max", with(base, code, r.max()), false));
            cases.add(new Case(code + " max+1", with(base, code, r.max().add(step)), false));
          }
          BigDecimal inside = r.min() == null ? BigDecimal.TEN : r.min().add(BigDecimal.ONE);
          cases.add(new Case(code + " decimals ok", with(base, code, inside.add(step)), false));
          cases.add(
              new Case(
                  code + " decimals+1",
                  with(base, code, inside.add(step.movePointLeft(1))),
                  false));
          cases.add(
              new Case(
                  code + " integer-valued decimal", with(base, code, inside.setScale(1)), false));
          cases.add(new Case(code + " wrong type", with(base, code, "5"), false));
        }
        case DATE -> {
          cases.add(new Case(code + " min", with(base, code, r.minDate().toString()), false));
          cases.add(new Case(code + " max", with(base, code, r.maxDate().toString()), false));
          cases.add(
              new Case(
                  code + " min-1", with(base, code, r.minDate().minusDays(1).toString()), true));
          cases.add(
              new Case(
                  code + " max+1", with(base, code, r.maxDate().plusDays(1).toString()), true));
          cases.add(new Case(code + " impossible", with(base, code, "2026-02-30"), false));
          cases.add(new Case(code + " format", with(base, code, "28/02/2026"), false));
          cases.add(new Case(code + " wrong type", with(base, code, 20260228), false));
        }
        case BOOLEAN -> {
          cases.add(new Case(code + " true", with(base, code, true), false));
          cases.add(new Case(code + " false", with(base, code, false), false));
          cases.add(new Case(code + " string", with(base, code, "true"), false));
        }
        case SELECT -> {
          cases.add(new Case(code + " active", with(base, code, "south"), false));
          cases.add(new Case(code + " archived", with(base, code, "old"), false));
          cases.add(new Case(code + " unknown", with(base, code, "west"), false));
          cases.add(new Case(code + " wrong type", with(base, code, 1), false));
        }
        case MULTI_SELECT -> {
          cases.add(new Case(code + " empty", with(base, code, List.of()), false));
          cases.add(new Case(code + " one", with(base, code, List.of("north")), false));
          cases.add(new Case(code + " max", with(base, code, List.of("north", "south")), false));
          cases.add(
              new Case(code + " max+1", with(base, code, List.of("north", "south", "old")), false));
          cases.add(
              new Case(code + " duplicated", with(base, code, List.of("north", "north")), false));
          cases.add(new Case(code + " archived", with(base, code, List.of("old")), false));
          cases.add(new Case(code + " unknown", with(base, code, List.of("west")), false));
          cases.add(new Case(code + " wrong type", with(base, code, "north"), false));
          cases.add(new Case(code + " wrong item", with(base, code, List.of(1)), false));
        }
      }
    }
    return cases;
  }

  static Object validValue(CustomFieldDefinition d) {
    return switch (d.type()) {
      case TEXT, LONG_TEXT -> d.rules().format() == TextFormat.EMAIL ? "xestor@naron.gal" : "abc";
      case NUMBER ->
          d.rules().min() == null ? new BigDecimal("5") : d.rules().min().add(BigDecimal.ONE);
      case DATE -> LocalDate.of(2026, 6, 1).toString();
      case BOOLEAN -> true;
      case SELECT -> "north";
      case MULTI_SELECT -> List.of("north");
    };
  }

  static boolean javaAccepts(List<CustomFieldDefinition> defs, String json) {
    @SuppressWarnings("unchecked")
    Map<String, Object> raw = JSON.readValue(json, Map.class);
    try {
      ValuesValidator.validate(
          Definitions.TARGET, defs, raw, CustomFieldValues.empty(), Audience.STAFF);
      return true;
    } catch (DomainException e) {
      return false;
    }
  }

  static boolean schemaAccepts(Schema schema, String json) {
    JsonNode node = JSON.readTree(json);
    return schema
        .validate(node, ctx -> ctx.executionConfig(c -> c.formatAssertionsEnabled(true)))
        .isEmpty();
  }

  static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
    Map<String, Object> copy = new LinkedHashMap<>(base);
    copy.put(key, value);
    return copy;
  }

  static Map<String, Object> without(Map<String, Object> base, String key) {
    Map<String, Object> copy = new LinkedHashMap<>(base);
    copy.remove(key);
    return copy;
  }
}
