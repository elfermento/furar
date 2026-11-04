// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Valores de campos personalizados dunha entidade, inmutables. Só se crean con {@link
 * #trusted(Map)} (desde o validador de {@code customfields}) ou con {@link #fromStorage(Map)}
 * (desde un adaptador de persistencia); a regra A16 garánteo. Así, todo o que chega a un agregado
 * pasou polo validador (ADR-0011).
 */
public final class CustomFieldValues {

  private static final CustomFieldValues EMPTY = new CustomFieldValues(new TreeMap<>());

  private final Map<String, CustomFieldValue> values;

  private CustomFieldValues(TreeMap<String, CustomFieldValue> values) {
    this.values = Collections.unmodifiableMap(values);
  }

  public static CustomFieldValues empty() {
    return EMPTY;
  }

  /** Só para o validador de {@code customfields} (A16). */
  public static CustomFieldValues trusted(Map<String, CustomFieldValue> validated) {
    return new CustomFieldValues(new TreeMap<>(validated));
  }

  /**
   * Reconstrúe os valores gardados. Só para adaptadores de persistencia (A16): o almacén tipado de
   * {@code customfields} pasa os valores xa tipados ({@link CustomFieldValue}); un valor en forma
   * JSON (texto, número, booleano, lista) tamén se admite, e entón a lectura tipada por definición
   * (datas e opcións) faina {@code CustomFieldProjector}.
   */
  public static CustomFieldValues fromStorage(Map<String, ?> json) {
    if (json == null || json.isEmpty()) {
      return EMPTY;
    }
    TreeMap<String, CustomFieldValue> parsed = new TreeMap<>();
    json.forEach(
        (code, raw) -> {
          if (raw != null) {
            parsed.put(code, fromJson(code, raw));
          }
        });
    return new CustomFieldValues(parsed);
  }

  private static CustomFieldValue fromJson(String code, Object raw) {
    return switch (raw) {
      case CustomFieldValue typed -> typed;
      case String s -> new CustomFieldValue.Text(s);
      case Boolean b -> new CustomFieldValue.Bool(b);
      case BigDecimal d -> new CustomFieldValue.Number(d);
      case java.lang.Number n -> new CustomFieldValue.Number(new BigDecimal(n.toString()));
      case List<?> list ->
          new CustomFieldValue.Choices(list.stream().map(String::valueOf).toList());
      default ->
          throw new IllegalArgumentException(
              "Unsupported stored value for custom field " + code + ": " + raw.getClass());
    };
  }

  public Map<String, CustomFieldValue> asMap() {
    return values;
  }

  public Optional<CustomFieldValue> get(String code) {
    return Optional.ofNullable(values.get(code));
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  /** Forma JSON (claves ordenadas) para devolver na API ou no ficheiro de portabilidade. */
  public Map<String, Object> toJson() {
    Map<String, Object> json = new LinkedHashMap<>();
    values.forEach((code, value) -> json.put(code, value.toJson()));
    return Collections.unmodifiableMap(json);
  }

  /** Tamaño en bytes UTF-8 do JSON compacto, para o límite de 32 KB por entidade. */
  public int jsonSizeInBytes() {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, CustomFieldValue> e : values.entrySet()) {
      if (!first) {
        sb.append(',');
      }
      first = false;
      appendString(sb, e.getKey());
      sb.append(':');
      appendJson(sb, e.getValue().toJson());
    }
    return sb.append('}').toString().getBytes(StandardCharsets.UTF_8).length;
  }

  private static void appendJson(StringBuilder sb, Object value) {
    switch (value) {
      case String s -> appendString(sb, s);
      case BigDecimal d -> sb.append(d.toPlainString());
      case List<?> list -> {
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
          if (i > 0) {
            sb.append(',');
          }
          appendJson(sb, list.get(i));
        }
        sb.append(']');
      }
      default -> sb.append(value);
    }
  }

  private static void appendString(StringBuilder sb, String s) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof CustomFieldValues other && other.values.equals(values);
  }

  @Override
  public int hashCode() {
    return values.hashCode();
  }

  @Override
  public String toString() {
    return "CustomFieldValues" + values;
  }
}
