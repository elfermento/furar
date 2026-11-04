// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Peso das reservas nas horas reservadas (plan do dominio 3, §1.2.4 e P3). */
class UseWeightTest {

  @Test
  void wholeCountsOne() {
    assertThat(UseWeight.of("WHOLE", 3, 20)).isEqualTo(UseWeight.WHOLE);
    assertThat(UseWeight.WHOLE.weigh(90)).isEqualTo(90.0);
  }

  @Test
  void partsCountOneOverLiveParts() {
    UseWeight half = UseWeight.of("PARTS", 2, null);
    assertThat(half.divisor()).isEqualTo(2);
    assertThat(half.weigh(60)).isEqualTo(30.0);
    assertThat(UseWeight.of("PARTS", 3, null).weigh(60)).isEqualTo(20.0);
  }

  @Test
  void seatsCountOneOverCapacity() {
    UseWeight seat = UseWeight.of("SEATS", 0, 30);
    assertThat(seat.divisor()).isEqualTo(30);
    assertThat(seat.weigh(60)).isEqualTo(2.0);
    assertThat(UseWeight.of("SEATS", 0, 1)).isEqualTo(UseWeight.WHOLE);
  }

  /** Datos raros: nunca divide por cero e as horas non se perden (cóntanse enteiras). */
  @Test
  void oddDataFallsBackToWhole() {
    assertThat(UseWeight.of("PARTS", 0, null)).isEqualTo(UseWeight.WHOLE);
    assertThat(UseWeight.of("PARTS", -1, null)).isEqualTo(UseWeight.WHOLE);
    assertThat(UseWeight.of("SEATS", 0, null)).isEqualTo(UseWeight.WHOLE);
    assertThat(UseWeight.of("SEATS", 0, 0)).isEqualTo(UseWeight.WHOLE);
    assertThat(UseWeight.of("SEATS", 0, -5)).isEqualTo(UseWeight.WHOLE);
    assertThat(UseWeight.of("PARTS", 0, null).weigh(60)).isEqualTo(60.0);
  }

  @Test
  void rejectsUnknownModesAndBadDivisors() {
    assertThatThrownBy(() -> UseWeight.of("HALF", 2, 2))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new UseWeight(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> UseWeight.of(null, 2, 2)).isInstanceOf(NullPointerException.class);
  }
}
