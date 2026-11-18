// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import gal.furar.shared.domain.Language;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Quen se apuntou: a conta, o nome, o correo e o idioma copiados do token, coma nas reservas.
 * Anonimizada (conservación ou baixa da conta), sen conta, nome nin correo. {@link #toString()} non
 * mostra datos persoais.
 */
public record Participant(
    @Nullable String subject, @Nullable String name, @Nullable String email, Language language) {

  public Participant {
    Objects.requireNonNull(language, "language");
  }

  @Override
  public String toString() {
    return "Participant[language=" + language + "]";
  }
}
