// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields;

import gal.furar.shared.domain.LocalizedText;
import java.util.Map;
import java.util.Objects;

/**
 * Un campo personalizado como columna dunha descarga (CSV): o código, o nome nos dous idiomas e,
 * nos de opcións, o nome de cada opción (tamén das desactivadas, que poden ter valores).
 *
 * @param options nome de cada opción polo seu código; baleiro se o campo non ten opcións
 */
public record CustomFieldColumn(
    String code, LocalizedText label, Map<String, LocalizedText> options) {

  public CustomFieldColumn {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(label, "label");
    options = options == null ? Map.of() : Map.copyOf(options);
  }
}
