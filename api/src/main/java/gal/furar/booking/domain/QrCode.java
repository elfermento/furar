// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.util.Objects;

/**
 * Un código QR como camiño SVG (ADR-0025, punto 13): {@code size} módulos por lado e un {@code
 * path} cun subcamiño por cada tira horizontal de módulos escuros, en coordenadas de módulo. Sen
 * zona tranquila: a marxe branca ponna quen o pinta.
 */
public record QrCode(int size, String path) {

  public QrCode {
    Objects.requireNonNull(path, "path");
    if (size < 21) {
      throw new IllegalArgumentException("A QR code has at least 21 modules: " + size);
    }
  }
}
