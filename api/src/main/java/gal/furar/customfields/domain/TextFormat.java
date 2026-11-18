// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

import java.util.regex.Pattern;

/**
 * Formatos predefinidos para texto. Sen expresións libres (ReDoS e erros do xestor). Os patróns son
 * compatibles con ECMA-262 para que o JSON Schema diga o mesmo (ADR-0011).
 */
public enum TextFormat {
  EMAIL("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"),
  PHONE("^\\+?[0-9][0-9 ]{5,19}$"),
  URL("^https?://[^\\s]+$");

  private final String regex;
  private final Pattern pattern;

  TextFormat(String regex) {
    this.regex = regex;
    this.pattern = Pattern.compile(regex);
  }

  public String regex() {
    return regex;
  }

  public boolean matches(String value) {
    return pattern.matcher(value).matches();
  }
}
