// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * A penalización das faltas do concello (ADR-0032, puntos 2, 7 e 8): apagada por defecto, de 1 a 20
 * faltas e de 1 a 365 días, e o momento en que se acende.
 */
class NoShowPolicyTest {

  static final Instant NOW = Instant.parse("2026-11-09T18:40:00Z");

  @Test
  void offByDefaultWithThreeFaultsInThirtyDaysAndFifteenDaysBlocked() {
    assertThat(NoShowPolicy.OFF.enabled()).isFalse();
    assertThat(NoShowPolicy.OFF.threshold()).isEqualTo(3);
    assertThat(NoShowPolicy.OFF.windowDays()).isEqualTo(30);
    assertThat(NoShowPolicy.OFF.blockDays()).isEqualTo(15);
  }

  @Test
  void theNumbersHaveToBeInRange() {
    assertThat(new NoShowPolicy(null, 1, 1, 1).threshold()).isEqualTo(1);
    assertThat(new NoShowPolicy(null, 20, 365, 365).windowDays()).isEqualTo(365);
    for (int[] bad :
        new int[][] {
          {0, 30, 15}, {21, 30, 15}, {3, 0, 15}, {3, 366, 15}, {3, 30, 0}, {3, 30, 366}
        }) {
      assertThatThrownBy(() -> new NoShowPolicy(null, bad[0], bad[1], bad[2]))
          .isInstanceOfSatisfying(
              DomainException.class,
              e -> assertThat(e.errorCode()).isEqualTo(MunicipalityError.NO_SHOW_POLICY_INVALID));
    }
  }

  @Test
  void turningItOnKeepsTheMomentAndTurningItOffForgetsIt() {
    NoShowPolicy on = NoShowPolicy.OFF.changed(true, 2, 60, 7, NOW);
    assertThat(on).isEqualTo(new NoShowPolicy(NOW, 2, 60, 7));

    // Cambiar os números con ela acesa non move desde cando conta.
    NoShowPolicy changed = on.changed(true, 4, 60, 7, NOW.plusSeconds(3600));
    assertThat(changed.since()).isEqualTo(NOW);
    assertThat(changed.threshold()).isEqualTo(4);

    NoShowPolicy off = changed.changed(false, 4, 60, 7, NOW.plusSeconds(7200));
    assertThat(off.enabled()).isFalse();
    assertThat(off.threshold()).as("os números quedan gardados").isEqualTo(4);

    // Acendela de novo empeza a contar desde ese momento.
    assertThat(off.changed(true, 4, 60, 7, NOW.plusSeconds(9000)).since())
        .isEqualTo(NOW.plusSeconds(9000));
  }
}
