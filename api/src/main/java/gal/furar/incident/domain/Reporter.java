// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.domain;

import gal.furar.shared.domain.Language;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Quen avisou, tal como veu no token ao avisar (ADR-0036): conta, nome, correo e idioma. Todo nulo
 * (agás o idioma) cando a incidencia está anonimizada.
 */
public record Reporter(
    @Nullable String subject, @Nullable String name, @Nullable String email, Language language) {

  static final int NAME_MAX = 200;
  static final int EMAIL_MAX = 254;

  public Reporter {
    Objects.requireNonNull(language, "language");
    name = trim(name, NAME_MAX);
    email = email == null || email.isBlank() || email.length() > EMAIL_MAX ? null : email.strip();
  }

  /** Xa sen persoa (baixa da conta). */
  public static Reporter anonymous(Language language) {
    return new Reporter(null, null, null, language);
  }

  private static @Nullable String trim(@Nullable String value, int max) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String stripped = value.strip();
    return stripped.length() > max ? stripped.substring(0, max) : stripped;
  }

  @Override
  public String toString() {
    return "Reporter[language=" + language + "]";
  }
}
