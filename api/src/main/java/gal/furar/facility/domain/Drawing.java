// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.LocalizedText;
import java.util.Objects;

/**
 * Un debuxo de Furar (catálogo da plataforma, igual para todos os concellos, CC0): código, nome e
 * texto alternativo en gl e es (fixos: o concello non os cambia) e medidas. Os bytes van no
 * classpath ({@code drawings/{code}.jpg}).
 */
public record Drawing(String code, LocalizedText name, LocalizedText alt, int width, int height) {

  public Drawing {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(alt, "alt");
  }
}
