// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

import gal.furar.shared.domain.Audience;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldValue;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Valida os valores dunha escritura contra as definicións do concello (ADR-0011). Regras:
 *
 * <ul>
 *   <li>claves sen definición, ou de campos que quen escribe non ve → {@code unknown-field};
 *   <li>só contan os campos usados no destino ({@link FieldUsage}), co obrigatorio e a visibilidade
 *       dese uso;
 *   <li>campos desactivados: consérvase o valor anterior; cambialo → {@code inactive-field};
 *   <li>campos invisibles para quen escribe: consérvase o valor anterior;
 *   <li>{@code null} ou ausente = sen valor (erro se é obrigatorio);
 *   <li>máximo {@link #MAX_BYTES} de JSON por entidade → {@code limit-exceeded}.
 * </ul>
 */
public final class ValuesValidator {

  public static final int MAX_BYTES = 32 * 1024;
  public static final String FIELD_PREFIX = "customFields.";
  private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

  private ValuesValidator() {}

  /** Valida e devolve o mapa de valores resultante (sen crear aínda {@code CustomFieldValues}). */
  public static Map<String, CustomFieldValue> validate(
      CustomFieldTarget target,
      List<CustomFieldDefinition> definitions,
      Map<String, ?> raw,
      CustomFieldValues previous,
      Audience writer) {
    Map<String, ?> input = raw == null ? Map.of() : raw;
    Map<String, CustomFieldDefinition> byCode = new LinkedHashMap<>();
    Map<String, FieldUsage> usages = new HashMap<>();
    for (CustomFieldDefinition d : definitions) {
      d.usage(target)
          .ifPresent(
              u -> {
                byCode.put(d.code(), d);
                usages.put(d.code(), u);
              });
    }
    List<FieldViolation> violations = new ArrayList<>();
    Map<String, CustomFieldValue> result = new LinkedHashMap<>();

    for (String key : input.keySet()) {
      FieldUsage usage = usages.get(key);
      if (usage == null || !usage.visibility().visibleTo(writer)) {
        violations.add(FieldViolation.of(FIELD_PREFIX + key, CustomFieldError.UNKNOWN_FIELD));
      }
    }

    for (CustomFieldDefinition def : byCode.values()) {
      String code = def.code();
      FieldUsage usage = usages.get(code);
      Object value = input.get(code);
      var before = previous.get(code);
      if (!usage.visibility().visibleTo(writer)) {
        before.ifPresent(v -> result.put(code, v));
        continue;
      }
      if (!def.isActive()) {
        if (input.containsKey(code) && !sameAsPrevious(value, before.orElse(null))) {
          violations.add(FieldViolation.of(FIELD_PREFIX + code, CustomFieldError.INACTIVE_FIELD));
        }
        before.ifPresent(v -> result.put(code, v));
        continue;
      }
      if (value == null) {
        if (usage.required()) {
          violations.add(FieldViolation.of(FIELD_PREFIX + code, CustomFieldError.REQUIRED));
        }
        continue;
      }
      Outcome outcome = check(def, usage.required(), value, before.orElse(null));
      if (outcome.violation() != null) {
        violations.add(outcome.violation());
      } else {
        result.put(code, outcome.value());
      }
    }

    if (!violations.isEmpty()) {
      throw DomainException.validation(violations);
    }
    return result;
  }

  /**
   * Valores que chegan dun ficheiro {@code furar-export} (ADR-0018). Foron válidos cando se
   * gardaron, así que non se volven aplicar obrigatoriedade, regras nin visibilidade (poden ter
   * cambiado despois), pero cada clave ten que ter definición (activa ou desactivada) e o valor, o
   * tipo dela; as opcións poden estar desactivadas, non inventadas.
   */
  public static Map<String, CustomFieldValue> restore(
      CustomFieldTarget target, List<CustomFieldDefinition> definitions, Map<String, ?> raw) {
    Map<String, CustomFieldDefinition> byCode = new HashMap<>();
    definitions.stream().filter(d -> d.usedIn(target)).forEach(d -> byCode.put(d.code(), d));
    List<FieldViolation> violations = new ArrayList<>();
    Map<String, CustomFieldValue> result = new LinkedHashMap<>();
    (raw == null ? Map.<String, Object>of() : raw)
        .forEach(
            (code, value) -> {
              CustomFieldDefinition def = byCode.get(code);
              if (def == null) {
                violations.add(
                    FieldViolation.of(FIELD_PREFIX + code, CustomFieldError.UNKNOWN_FIELD));
              } else if (value != null) {
                Outcome outcome = restoreValue(def, value);
                if (outcome.violation() != null) {
                  violations.add(outcome.violation());
                } else {
                  result.put(code, outcome.value());
                }
              }
            });
    if (!violations.isEmpty()) {
      throw DomainException.validation(violations);
    }
    return result;
  }

  private static Outcome restoreValue(CustomFieldDefinition def, Object value) {
    String code = def.code();
    Set<String> options = new HashSet<>();
    def.spec().options().forEach(o -> options.add(o.code()));
    return switch (def.type()) {
      case TEXT, LONG_TEXT ->
          value instanceof String s
              ? Outcome.ok(new CustomFieldValue.Text(s))
              : Outcome.error(code, CustomFieldError.INVALID_TYPE);
      case NUMBER ->
          value instanceof Number n && isFinite(n)
              ? Outcome.ok(new CustomFieldValue.Number(new BigDecimal(n.toString())))
              : Outcome.error(code, CustomFieldError.INVALID_TYPE);
      case DATE -> {
        if (!(value instanceof String s) || !ISO_DATE.matcher(s).matches()) {
          yield Outcome.error(code, CustomFieldError.INVALID_DATE);
        }
        try {
          yield Outcome.ok(new CustomFieldValue.Date(LocalDate.parse(s)));
        } catch (DateTimeParseException e) {
          yield Outcome.error(code, CustomFieldError.INVALID_DATE);
        }
      }
      case BOOLEAN ->
          value instanceof Boolean b
              ? Outcome.ok(new CustomFieldValue.Bool(b))
              : Outcome.error(code, CustomFieldError.INVALID_TYPE);
      case SELECT -> {
        if (!(value instanceof String s)) {
          yield Outcome.error(code, CustomFieldError.INVALID_TYPE);
        }
        yield options.contains(s)
            ? Outcome.ok(new CustomFieldValue.Choice(s))
            : Outcome.error(code, CustomFieldError.INVALID_OPTION, Map.of("option", s));
      }
      case MULTI_SELECT -> {
        if (!(value instanceof List<?> list)
            || list.stream().anyMatch(i -> !(i instanceof String))) {
          yield Outcome.error(code, CustomFieldError.INVALID_TYPE);
        }
        List<String> codes = list.stream().map(String.class::cast).toList();
        if (new HashSet<>(codes).size() != codes.size()) {
          yield Outcome.error(code, CustomFieldError.DUPLICATE_OPTION);
        }
        for (String option : codes) {
          if (!options.contains(option)) {
            yield Outcome.error(code, CustomFieldError.INVALID_OPTION, Map.of("option", option));
          }
        }
        yield Outcome.ok(new CustomFieldValue.Choices(codes));
      }
    };
  }

  /** Comproba o tamaño máximo dos valores dunha entidade. */
  public static void checkSize(CustomFieldValues values) {
    int size = values.jsonSizeInBytes();
    if (size > MAX_BYTES) {
      throw new DomainException(
          CustomFieldError.LIMIT_EXCEEDED, Map.of("limit", MAX_BYTES, "size", size));
    }
  }

  private static boolean sameAsPrevious(Object value, CustomFieldValue previous) {
    if (value == null) {
      return previous == null;
    }
    if (previous == null) {
      return false;
    }
    Object json = previous.toJson();
    if (json instanceof BigDecimal d && value instanceof Number n) {
      return d.compareTo(new BigDecimal(n.toString())) == 0;
    }
    return Objects.equals(json, value);
  }

  private record Outcome(CustomFieldValue value, FieldViolation violation) {
    static Outcome ok(CustomFieldValue value) {
      return new Outcome(value, null);
    }

    static Outcome error(String code, CustomFieldError error) {
      return new Outcome(null, FieldViolation.of(FIELD_PREFIX + code, error));
    }

    static Outcome error(String code, CustomFieldError error, Map<String, Object> params) {
      return new Outcome(null, FieldViolation.of(FIELD_PREFIX + code, error, params));
    }
  }

  private static Outcome check(
      CustomFieldDefinition def, boolean required, Object value, CustomFieldValue before) {
    String code = def.code();
    FieldRules rules = def.rules();
    return switch (def.type()) {
      case TEXT, LONG_TEXT -> {
        if (!(value instanceof String s)) {
          yield Outcome.error(code, CustomFieldError.INVALID_TYPE);
        }
        int length = s.codePointCount(0, s.length());
        if (required && length == 0) {
          yield Outcome.error(code, CustomFieldError.REQUIRED);
        }
        if (rules.minLength() != null && length < rules.minLength()) {
          yield Outcome.error(code, CustomFieldError.TOO_SHORT, Map.of("min", rules.minLength()));
        }
        int max = rules.effectiveMaxLength(def.type());
        if (length > max) {
          yield Outcome.error(code, CustomFieldError.TOO_LONG, Map.of("max", max));
        }
        if (rules.format() != null && !rules.format().matches(s)) {
          yield Outcome.error(
              code,
              CustomFieldError.INVALID_FORMAT,
              Map.of("format", rules.format().name().toLowerCase(java.util.Locale.ROOT)));
        }
        yield Outcome.ok(new CustomFieldValue.Text(s));
      }
      case NUMBER -> {
        if (!(value instanceof Number n) || !isFinite(n)) {
          yield Outcome.error(code, CustomFieldError.INVALID_TYPE);
        }
        BigDecimal d = value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
        if (rules.min() != null && d.compareTo(rules.min()) < 0) {
          yield Outcome.error(code, CustomFieldError.BELOW_MIN, Map.of("min", rules.min()));
        }
        if (rules.max() != null && d.compareTo(rules.max()) > 0) {
          yield Outcome.error(code, CustomFieldError.ABOVE_MAX, Map.of("max", rules.max()));
        }
        int decimals = rules.decimals() == null ? FieldRules.MAX_DECIMALS : rules.decimals();
        if (Math.max(d.stripTrailingZeros().scale(), 0) > decimals) {
          yield Outcome.error(
              code, CustomFieldError.TOO_MANY_DECIMALS, Map.of("decimals", decimals));
        }
        yield Outcome.ok(new CustomFieldValue.Number(d));
      }
      case DATE -> {
        if (!(value instanceof String s)) {
          yield Outcome.error(code, CustomFieldError.INVALID_TYPE);
        }
        LocalDate date;
        try {
          if (!ISO_DATE.matcher(s).matches()) {
            throw new DateTimeParseException("not ISO", s, 0);
          }
          date = LocalDate.parse(s);
        } catch (DateTimeParseException e) {
          yield Outcome.error(code, CustomFieldError.INVALID_DATE);
        }
        if (rules.minDate() != null && date.isBefore(rules.minDate())) {
          yield Outcome.error(
              code, CustomFieldError.DATE_TOO_EARLY, Map.of("min", rules.minDate().toString()));
        }
        if (rules.maxDate() != null && date.isAfter(rules.maxDate())) {
          yield Outcome.error(
              code, CustomFieldError.DATE_TOO_LATE, Map.of("max", rules.maxDate().toString()));
        }
        yield Outcome.ok(new CustomFieldValue.Date(date));
      }
      case BOOLEAN ->
          value instanceof Boolean b
              ? Outcome.ok(new CustomFieldValue.Bool(b))
              : Outcome.error(code, CustomFieldError.INVALID_TYPE);
      case SELECT -> {
        if (!(value instanceof String s)) {
          yield Outcome.error(code, CustomFieldError.INVALID_TYPE);
        }
        boolean keepsInactive = before != null && s.equals(before.toJson());
        if (!def.hasActiveOption(s) && !keepsInactive) {
          yield Outcome.error(code, CustomFieldError.INVALID_OPTION, Map.of("option", s));
        }
        yield Outcome.ok(new CustomFieldValue.Choice(s));
      }
      case MULTI_SELECT -> {
        if (!(value instanceof List<?> list)
            || list.stream().anyMatch(i -> !(i instanceof String))) {
          yield Outcome.error(code, CustomFieldError.INVALID_TYPE);
        }
        List<String> codes = list.stream().map(String.class::cast).toList();
        Set<?> beforeCodes =
            before != null && before.toJson() instanceof List<?> l ? Set.copyOf(l) : Set.of();
        if (new HashSet<>(codes).size() != codes.size()) {
          yield Outcome.error(code, CustomFieldError.DUPLICATE_OPTION);
        }
        for (String option : codes) {
          if (!def.hasActiveOption(option) && !beforeCodes.contains(option)) {
            yield Outcome.error(code, CustomFieldError.INVALID_OPTION, Map.of("option", option));
          }
        }
        int min = rules.minItems() == null ? 0 : rules.minItems();
        if (required && codes.isEmpty()) {
          yield Outcome.error(code, CustomFieldError.REQUIRED);
        }
        if (codes.size() < min) {
          yield Outcome.error(code, CustomFieldError.TOO_FEW_OPTIONS, Map.of("min", min));
        }
        if (rules.maxItems() != null && codes.size() > rules.maxItems()) {
          yield Outcome.error(
              code, CustomFieldError.TOO_MANY_OPTIONS, Map.of("max", rules.maxItems()));
        }
        yield Outcome.ok(new CustomFieldValue.Choices(codes));
      }
    };
  }

  private static boolean isFinite(Number n) {
    if (n instanceof Double d) {
      return Double.isFinite(d);
    }
    if (n instanceof Float f) {
      return Float.isFinite(f);
    }
    return true;
  }
}
