// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import java.util.Arrays;
import java.util.Optional;

/** Idiomas da aplicación. O galego é o idioma por defecto. */
public enum Language {
  GL("gl"),
  ES("es");

  private final String code;

  Language(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static Optional<Language> fromCode(String code) {
    return Arrays.stream(values()).filter(l -> l.code.equals(code)).findFirst();
  }
}
