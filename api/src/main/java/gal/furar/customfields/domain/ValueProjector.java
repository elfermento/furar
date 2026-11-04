// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

import gal.furar.shared.domain.Audience;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValue;
import gal.furar.shared.domain.CustomFieldValues;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Na lectura: deixa só os valores que a audiencia pode ver nese destino e tipa cada un pola súa
 * definición (datas e opcións). Os valores sen definición ou sen uso no destino non se amosan.
 */
public final class ValueProjector {

  private ValueProjector() {}

  public static Map<String, CustomFieldValue> project(
      CustomFieldTarget target,
      List<CustomFieldDefinition> definitions,
      CustomFieldValues stored,
      Audience viewer) {
    Map<String, CustomFieldValue> result = new LinkedHashMap<>();
    for (CustomFieldDefinition def : definitions) {
      if (!def.usage(target).map(u -> u.visibility().visibleTo(viewer)).orElse(false)) {
        continue;
      }
      stored.get(def.code()).map(v -> typed(def, v)).ifPresent(v -> result.put(def.code(), v));
    }
    return result;
  }

  private static CustomFieldValue typed(CustomFieldDefinition def, CustomFieldValue value) {
    if (value instanceof CustomFieldValue.Text(String text)) {
      if (def.type() == FieldType.DATE) {
        try {
          return new CustomFieldValue.Date(LocalDate.parse(text));
        } catch (DateTimeParseException e) {
          return value;
        }
      }
      if (def.type() == FieldType.SELECT) {
        return new CustomFieldValue.Choice(text);
      }
    }
    return value;
  }
}
