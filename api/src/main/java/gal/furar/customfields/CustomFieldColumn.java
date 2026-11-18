// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields;

import gal.furar.shared.domain.LocalizedText;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Un campo personalizado como columna dunha descarga (CSV): o código, o nome nos dous idiomas e,
 * nos de opcións, o nome de cada opción (tamén das desactivadas, que poden ter valores), na orde da
 * definición.
 *
 * @param options nome de cada opción polo seu código; baleiro se o campo non ten opcións
 * @param type o tipo do campo ({@code TEXT}, {@code LONG_TEXT}, {@code NUMBER}, {@code DATE},
 *     {@code BOOLEAN}, {@code SELECT} ou {@code MULTI_SELECT})
 * @param active falso se o campo está desactivado (os seus valores gardados séguense lendo)
 */
public record CustomFieldColumn(
    String code,
    LocalizedText label,
    Map<String, LocalizedText> options,
    String type,
    boolean active) {

  public CustomFieldColumn {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(type, "type");
    options =
        options == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(options));
  }

  public CustomFieldColumn(String code, LocalizedText label, Map<String, LocalizedText> options) {
    this(code, label, options, "TEXT", true);
  }
}
