// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * O peso dunha reserva nas horas reservadas (plan do dominio 3, §1.2.4 e P3): 1 se se reserva a
 * instalación enteira, 1/nº de partes vivas por partes e 1/{@code capacity} por prazas. Gárdase
 * como divisor enteiro para non perder precisión ata o final.
 *
 * <p>Se un uso por partes xa non ten partes vivas ou un uso por prazas non ten capacidade (datos
 * antigos ou incompletos), o peso é 1: nunca se divide por cero e as horas desas reservas non se
 * perden; a ocupación queda limitada ao 100 % ({@link Ratio}).
 *
 * @param divisor o divisor das horas, sempre ≥ 1
 */
public record UseWeight(int divisor) {

  /** Peso dunha reserva enteira. */
  public static final UseWeight WHOLE = new UseWeight(1);

  public UseWeight {
    if (divisor < 1) {
      throw new IllegalArgumentException("The divisor must be at least 1: " + divisor);
    }
  }

  /**
   * O peso dun uso.
   *
   * @param rentalMode {@code WHOLE}, {@code PARTS} ou {@code SEATS}
   * @param liveParts as partes vivas do uso (só conta con {@code PARTS})
   * @param capacity as prazas do uso (só conta con {@code SEATS})
   * @throws IllegalArgumentException cun modo descoñecido
   */
  public static UseWeight of(String rentalMode, int liveParts, @Nullable Integer capacity) {
    Objects.requireNonNull(rentalMode, "rentalMode");
    return switch (rentalMode) {
      case "WHOLE" -> WHOLE;
      case "PARTS" -> liveParts >= 1 ? new UseWeight(liveParts) : WHOLE;
      case "SEATS" -> capacity != null && capacity >= 1 ? new UseWeight(capacity) : WHOLE;
      default -> throw new IllegalArgumentException("Unknown rental mode: " + rentalMode);
    };
  }

  /** Os minutos reservados xa ponderados. */
  public double weigh(long minutes) {
    return (double) minutes / divisor;
  }
}
