// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Intervalo {@code [from, to)} en UTC, como o {@code tstzrange '[)'} da base.
 *
 * @param from inicio, incluído
 * @param to fin, excluído
 */
public record TimeWindow(Instant from, Instant to) {

  public TimeWindow {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (!to.isAfter(from)) {
      throw new IllegalArgumentException("Empty window: " + from + " - " + to);
    }
  }

  public boolean contains(Instant at) {
    return !at.isBefore(from) && at.isBefore(to);
  }
}
