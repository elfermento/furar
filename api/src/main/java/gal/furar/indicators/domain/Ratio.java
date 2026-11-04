// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.domain;

import java.util.OptionalDouble;

/**
 * Unha proporción limitada ao 100 % (plan do dominio 3, §1.2.4): a ocupación son as horas
 * reservadas sobre as horas abertas. Sen horas abertas non hai valor (o «pechado» do mapa de
 * calor).
 *
 * @param part o numerador (p. ex. minutos reservados xa ponderados)
 * @param whole o denominador (p. ex. minutos abertos)
 */
public record Ratio(double part, double whole) {

  public Ratio {
    if (part < 0 || whole < 0 || Double.isNaN(part) || Double.isNaN(whole)) {
      throw new IllegalArgumentException(
          "A ratio needs non-negative numbers: " + part + "/" + whole);
    }
  }

  /** Se hai denominador. */
  public boolean defined() {
    return whole > 0;
  }

  /** O numerador sen pasar do denominador: o que se devolve como «reservado» na ocupación. */
  public double cappedPart() {
    return Math.min(part, whole);
  }

  /** A proporción entre 0 e 1; baleira sen denominador. */
  public OptionalDouble value() {
    return defined() ? OptionalDouble.of(cappedPart() / whole) : OptionalDouble.empty();
  }
}
