// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** O límite fixo das esperas (ADR-0033, punto 4): tres por conta e concello, coma a base. */
class WaitLimitTest {

  @Test
  void thereAreThreeOrdinalsInOrder() {
    assertThat(WaitLimit.MAX).isEqualTo(3);
    assertThat(WaitLimit.ordinals()).containsExactly(1, 2, 3);
  }
}
