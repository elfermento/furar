// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import gal.furar.shared.domain.Language;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Locale;

/** Idioma da resposta segundo {@code Accept-Language}: galego ou castelán, galego por defecto. */
public final class Locales {

  public static final Locale GALICIAN = Locale.of("gl");
  public static final Locale SPANISH = Locale.of("es");

  private Locales() {}

  public static Language language(HttpServletRequest request) {
    if (request.getHeader("Accept-Language") == null) {
      return Language.GL;
    }
    for (Locale locale : Collections.list(request.getLocales())) {
      var language = Language.fromCode(locale.getLanguage());
      if (language.isPresent()) {
        return language.get();
      }
    }
    return Language.GL;
  }

  public static Locale locale(Language language) {
    return language == Language.ES ? SPANISH : GALICIAN;
  }
}
