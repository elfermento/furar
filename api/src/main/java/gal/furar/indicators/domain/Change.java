// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.domain;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * A comparación co período anterior (plan do dominio 3, §1.2.5): en porcentaxe nas contas, horas e
 * diñeiro; en puntos na ocupación. Sen valor anterior (0 ou sen horas abertas) non hai cambio: «sen
 * datos do período anterior».
 *
 * @param kind porcentaxe ou puntos
 * @param value o cambio (p. ex. 25 = +25 % ou +25 puntos); baleiro sen valor anterior
 */
public record Change(Kind kind, OptionalDouble value) {

  /** Como se mide o cambio. */
  public enum Kind {
    PERCENT,
    POINTS
  }

  /** Cara a onde vai: subir en verde, baixar en vermello, igual en gris. */
  public enum Direction {
    UP,
    DOWN,
    SAME,
    NONE
  }

  private static final double HUNDRED = 100.0;

  public Change {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(value, "value");
  }

  /** Cambio en porcentaxe; sen valor se o anterior é 0. */
  public static Change percent(double current, double previous) {
    if (previous == 0) {
      return new Change(Kind.PERCENT, OptionalDouble.empty());
    }
    return new Change(Kind.PERCENT, OptionalDouble.of((current - previous) / previous * HUNDRED));
  }

  /** Cambio en puntos entre dúas proporcións; sen valor se algunha non ten denominador. */
  public static Change points(Ratio current, Ratio previous) {
    OptionalDouble now = current.value();
    OptionalDouble before = previous.value();
    if (now.isEmpty() || before.isEmpty()) {
      return new Change(Kind.POINTS, OptionalDouble.empty());
    }
    return new Change(
        Kind.POINTS, OptionalDouble.of((now.getAsDouble() - before.getAsDouble()) * HUNDRED));
  }

  public Direction direction() {
    if (value.isEmpty()) {
      return Direction.NONE;
    }
    double v = value.getAsDouble();
    if (v > 0) {
      return Direction.UP;
    }
    return v < 0 ? Direction.DOWN : Direction.SAME;
  }
}
