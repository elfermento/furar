// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import gal.furar.shared.domain.Language;
import org.jspecify.annotations.Nullable;

/**
 * Texto propio dunha páxina legal en gl e es: HTML xa saneado coa lista branca do servidor ({@code
 * LegalHtmlSanitizer}). Un idioma pode faltar: a web amosa entón o outro.
 *
 * @param gl texto en galego, ou nulo
 * @param es texto en castelán, ou nulo
 */
public record LegalText(@Nullable String gl, @Nullable String es) {

  /** Tamaño máximo de cada idioma, xa saneado (a base comproba o mesmo). */
  public static final int MAX = 40_000;

  public LegalText {
    gl = gl == null || gl.isBlank() ? null : gl.strip();
    es = es == null || es.isBlank() ? null : es.strip();
  }

  public boolean isEmpty() {
    return gl == null && es == null;
  }

  public @Nullable String in(Language language) {
    return language == Language.ES ? es : gl;
  }
}
