// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.domain;

import java.time.Instant;
import java.util.Objects;

/** Un tramo de tempo real: inicio incluído e fin excluído. */
public record TimeSpan(Instant start, Instant end) {

  public TimeSpan {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    if (end.isBefore(start)) {
      throw new IllegalArgumentException("The span ends before it starts: " + start + ".." + end);
    }
  }
}
