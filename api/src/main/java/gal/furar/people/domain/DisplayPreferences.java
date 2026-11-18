// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.domain;

import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * As preferencias de accesibilidade da conta (ADR-0031, revisión do 2026-11-11): tamaño do texto,
 * alto contraste e menos movemento. Poden revelar unha discapacidade: só se garda isto, sen
 * historial, e nunca vai a rexistros co seu valor.
 *
 * @param textSize {@code normal}, {@code large} ou {@code x-large}
 * @param highContrast nulo = segue o sistema
 * @param reducedMotion nulo = segue o sistema
 */
public record DisplayPreferences(
    String textSize, @Nullable Boolean highContrast, @Nullable Boolean reducedMotion) {

  /** Os tamaños do texto que hai. */
  public static final Set<String> TEXT_SIZES = Set.of("normal", "large", "x-large");

  public DisplayPreferences {
    Objects.requireNonNull(textSize, "textSize");
    if (!TEXT_SIZES.contains(textSize)) {
      throw new IllegalArgumentException("Unknown text size");
    }
  }
}
