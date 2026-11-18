// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.shared.domain.LocalizedText;
import java.util.Map;
import java.util.Objects;

/**
 * Un campo propio da reserva como columna da descarga: o código, o nome e, nos de opcións, o nome
 * de cada opción.
 */
public record BookingFieldColumn(
    String code, LocalizedText label, Map<String, LocalizedText> options) {

  public BookingFieldColumn {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(label, "label");
    options = options == null ? Map.of() : Map.copyOf(options);
  }
}
