// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

/**
 * Texto en galego e castelán. Os dous son obrigatorios: a app móstrase completa nos dous idiomas.
 */
public record LocalizedText(String gl, String es) {

  public static final int MAX_LENGTH = 500;

  public LocalizedText {
    if (isBlank(gl) || isBlank(es)) {
      throw new IllegalArgumentException("LocalizedText needs gl and es");
    }
    if (gl.length() > MAX_LENGTH || es.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("LocalizedText too long");
    }
    gl = gl.strip();
    es = es.strip();
  }

  public String in(Language language) {
    return language == Language.ES ? es : gl;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
