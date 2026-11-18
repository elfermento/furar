// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

import gal.furar.shared.domain.DomainException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Regras de validación dun campo; cada tipo admite as súas. Os nulos non aplican.
 *
 * @param minLength lonxitude mínima (texto), en caracteres Unicode
 * @param maxLength lonxitude máxima (texto ≤ 500, texto longo ≤ 5.000)
 * @param min mínimo (número), incluído
 * @param max máximo (número), incluído
 * @param decimals decimais permitidos (número), 0–6
 * @param minDate data mínima, incluída
 * @param maxDate data máxima, incluída
 * @param minItems opcións mínimas (selección múltiple)
 * @param maxItems opcións máximas (selección múltiple)
 * @param format formato predefinido (texto curto)
 */
public record FieldRules(
    @Nullable Integer minLength,
    @Nullable Integer maxLength,
    @Nullable BigDecimal min,
    @Nullable BigDecimal max,
    @Nullable Integer decimals,
    @Nullable LocalDate minDate,
    @Nullable LocalDate maxDate,
    @Nullable Integer minItems,
    @Nullable Integer maxItems,
    @Nullable TextFormat format) {

  public static final int TEXT_MAX = 500;
  public static final int LONG_TEXT_MAX = 5_000;
  public static final int MAX_DECIMALS = 6;
  public static final FieldRules NONE =
      new FieldRules(null, null, null, null, null, null, null, null, null, null);

  /** Lonxitude máxima efectiva dun texto. */
  public int effectiveMaxLength(FieldType type) {
    int cap = type == FieldType.LONG_TEXT ? LONG_TEXT_MAX : TEXT_MAX;
    return maxLength == null ? cap : Math.min(maxLength, cap);
  }

  /** Comproba que as regras teñen sentido para o tipo; se non, {@code invalid-definition}. */
  void checkFor(FieldType type, int activeOptions) {
    boolean text = type.isText();
    boolean number = type == FieldType.NUMBER;
    boolean date = type == FieldType.DATE;
    boolean multi = type == FieldType.MULTI_SELECT;
    require(text || (minLength == null && maxLength == null), "length-not-allowed");
    require(number || (min == null && max == null && decimals == null), "range-not-allowed");
    require(date || (minDate == null && maxDate == null), "date-range-not-allowed");
    require(multi || (minItems == null && maxItems == null), "items-not-allowed");
    require(type == FieldType.TEXT || format == null, "format-not-allowed");
    if (text) {
      int cap = type == FieldType.LONG_TEXT ? LONG_TEXT_MAX : TEXT_MAX;
      require(minLength == null || minLength >= 0, "min-length-negative");
      require(maxLength == null || (maxLength >= 1 && maxLength <= cap), "max-length-out-of-range");
      require(
          minLength == null || minLength <= effectiveMaxLength(type),
          "min-length-above-max-length");
    }
    if (number) {
      require(min == null || max == null || min.compareTo(max) <= 0, "min-above-max");
      require(
          decimals == null || (decimals >= 0 && decimals <= MAX_DECIMALS), "decimals-out-of-range");
    }
    if (date) {
      require(
          minDate == null || maxDate == null || !minDate.isAfter(maxDate),
          "min-date-after-max-date");
    }
    if (multi) {
      require(minItems == null || minItems >= 0, "min-items-negative");
      require(maxItems == null || maxItems >= 1, "max-items-below-one");
      require(
          minItems == null || maxItems == null || minItems <= maxItems,
          "min-items-above-max-items");
      require(minItems == null || minItems <= activeOptions, "min-items-above-options");
    }
  }

  private static void require(boolean condition, String reason) {
    if (!condition) {
      throw new DomainException(CustomFieldError.INVALID_DEFINITION, Map.of("reason", reason));
    }
  }
}
