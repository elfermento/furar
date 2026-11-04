// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

import org.jspecify.annotations.Nullable;

/**
 * Textos propios das páxinas legais tal como chegan (edición e importación): HTML sen sanear, que o
 * caso de uso pasa pola lista branca antes de gardalo. Un texto nulo queda sen texto.
 */
public record LegalTextsData(
    @Nullable Text privacy, @Nullable Text accessibility, @Nullable Text legalNotice) {

  /** Un texto en gl e es; calquera dos dous pode faltar. */
  public record Text(@Nullable String gl, @Nullable String es) {}
}
