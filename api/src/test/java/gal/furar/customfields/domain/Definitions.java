// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.LocalizedText;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Fábrica de definicións para os tests de dominio. */
public final class Definitions {

  public static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

  private Definitions() {}

  /** Destino dos tests: o único rexistrado hoxe. */
  public static final CustomFieldTarget TARGET = CustomFieldTarget.MUNICIPALITY;

  public static DefinitionSpec spec(
      boolean required, FieldRules rules, List<FieldOption> options, Visibility visibility) {
    return spec(rules, options, false, new FieldUsage(TARGET, required, visibility));
  }

  public static DefinitionSpec spec(
      FieldRules rules, List<FieldOption> options, boolean personalData, FieldUsage... usages) {
    return new DefinitionSpec(
        new LocalizedText("Etiqueta", "Etiqueta"),
        null,
        rules,
        options,
        personalData,
        List.of(usages));
  }

  public static CustomFieldDefinition define(
      String code, FieldType type, boolean required, FieldRules rules, List<FieldOption> options) {
    return CustomFieldDefinition.create(
        CustomFieldDefinitionId.newId(),
        code,
        type,
        spec(required, rules, options, Visibility.PUBLIC),
        NOW);
  }

  public static CustomFieldDefinition define(String code, FieldType type, boolean required) {
    return define(code, type, required, FieldRules.NONE, type.hasOptions() ? options() : List.of());
  }

  public static List<FieldOption> options() {
    return List.of(
        new FieldOption("north", new LocalizedText("Norte", "Norte"), false),
        new FieldOption("south", new LocalizedText("Sur", "Sur"), false),
        new FieldOption("old", new LocalizedText("Vella", "Vieja"), true));
  }

  public static FieldRules text(Integer min, Integer max, TextFormat format) {
    return new FieldRules(min, max, null, null, null, null, null, null, null, format);
  }

  public static FieldRules number(String min, String max, Integer decimals) {
    return new FieldRules(
        null,
        null,
        min == null ? null : new BigDecimal(min),
        max == null ? null : new BigDecimal(max),
        decimals,
        null,
        null,
        null,
        null,
        null);
  }

  public static FieldRules date(String min, String max) {
    return new FieldRules(
        null,
        null,
        null,
        null,
        null,
        min == null ? null : LocalDate.parse(min),
        max == null ? null : LocalDate.parse(max),
        null,
        null,
        null);
  }

  public static FieldRules items(Integer min, Integer max) {
    return new FieldRules(null, null, null, null, null, null, null, min, max, null);
  }
}
