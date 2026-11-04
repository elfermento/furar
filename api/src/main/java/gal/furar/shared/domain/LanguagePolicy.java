// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Idiomas que todo concello desta instalación ten que ofrecer (ADR-0003). Nunha instalación galega,
 * {@code gl} (Lei 3/1983 de normalización lingüística, Lei 5/1997 de Administración local de
 * Galicia); noutra comunidade, o seu idioma cooficial. Sae da configuración ({@code
 * furar.languages.required}), non do código.
 *
 * @param required idiomas obrigatorios (pode estar baleiro)
 */
public record LanguagePolicy(Set<Language> required) {

  /** Política dunha instalación en Galicia. */
  public static final LanguagePolicy GALICIA = new LanguagePolicy(Set.of(Language.GL));

  public LanguagePolicy {
    Objects.requireNonNull(required, "required");
    required = required.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(required));
  }

  /** O primeiro idioma obrigatorio que falta, na orde do catálogo. */
  public Optional<Language> firstMissing(Collection<Language> offered) {
    for (Language language : Language.values()) {
      if (required.contains(language) && !offered.contains(language)) {
        return Optional.of(language);
      }
    }
    return Optional.empty();
  }
}
