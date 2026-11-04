// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

import gal.furar.shared.domain.CustomFieldTarget;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON Schema 2020-12 dos campos personalizados dunha entidade, para terceiros e importacións
 * (ADR-0011), cos campos usados nese destino e o obrigatorio de cada uso. O que JSON Schema non
 * pode dicir (rango de datas, visibilidade, dato persoal) vai en extensións {@code x-furar-*} e só
 * o comproba o validador Java.
 */
public final class JsonSchemaGenerator {

  public static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";

  private JsonSchemaGenerator() {}

  /** Esquema das definicións activas indicadas (xa filtradas pola audiencia). */
  public static Map<String, Object> generate(
      CustomFieldTarget target, List<CustomFieldDefinition> definitions) {
    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    for (CustomFieldDefinition def : definitions) {
      var usage = def.usage(target);
      if (!def.isActive() || usage.isEmpty()) {
        continue;
      }
      properties.put(def.code(), property(def, usage.get()));
      if (usage.get().required()) {
        required.add(def.code());
      }
    }
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("$schema", DIALECT);
    schema.put("title", "customFields:" + target.code());
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.put("properties", properties);
    if (!required.isEmpty()) {
      schema.put("required", required);
    }
    return schema;
  }

  private static Map<String, Object> property(CustomFieldDefinition def, FieldUsage usage) {
    FieldRules rules = def.rules();
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("title", def.label().gl());
    if (def.spec().help() != null) {
      p.put("description", def.spec().help().gl());
    }
    p.put("x-furar-type", def.type().name());
    p.put("x-furar-label", Map.of("gl", def.label().gl(), "es", def.label().es()));
    p.put("x-furar-visibility", usage.visibility().name());
    p.put("x-furar-personalData", def.spec().personalData());
    boolean nullable = !usage.required();
    switch (def.type()) {
      case TEXT, LONG_TEXT -> {
        p.put("type", nullable ? List.of("string", "null") : "string");
        int min = rules.minLength() == null ? 0 : rules.minLength();
        if (usage.required()) {
          min = Math.max(min, 1);
        }
        if (min > 0) {
          p.put("minLength", min);
        }
        p.put("maxLength", rules.effectiveMaxLength(def.type()));
        if (rules.format() != null) {
          p.put("pattern", rules.format().regex());
          p.put("x-furar-format", rules.format().name());
        }
      }
      case NUMBER -> {
        p.put("type", nullable ? List.of("number", "null") : "number");
        if (rules.min() != null) {
          p.put("minimum", rules.min());
        }
        if (rules.max() != null) {
          p.put("maximum", rules.max());
        }
        int decimals = rules.decimals() == null ? FieldRules.MAX_DECIMALS : rules.decimals();
        p.put("multipleOf", BigDecimal.ONE.movePointLeft(decimals));
      }
      case DATE -> {
        p.put("type", nullable ? List.of("string", "null") : "string");
        p.put("format", "date");
        p.put("pattern", "^\\d{4}-\\d{2}-\\d{2}$");
        if (rules.minDate() != null) {
          p.put("x-furar-minDate", rules.minDate().toString());
        }
        if (rules.maxDate() != null) {
          p.put("x-furar-maxDate", rules.maxDate().toString());
        }
      }
      case BOOLEAN -> p.put("type", nullable ? List.of("boolean", "null") : "boolean");
      case SELECT -> {
        List<Object> values = new ArrayList<>(def.activeOptionCodes());
        if (nullable) {
          values.add(null);
        }
        p.put("enum", values);
      }
      case MULTI_SELECT -> {
        p.put("type", nullable ? List.of("array", "null") : "array");
        p.put("items", Map.of("enum", def.activeOptionCodes()));
        p.put("uniqueItems", true);
        int min = rules.minItems() == null ? 0 : rules.minItems();
        if (usage.required()) {
          min = Math.max(min, 1);
        }
        if (min > 0) {
          p.put("minItems", min);
        }
        if (rules.maxItems() != null) {
          p.put("maxItems", rules.maxItems());
        }
      }
    }
    return p;
  }
}
