// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Borrador calculado: falta o que non estea cumprido, sempre na mesma orde. */
class RequirementTest {

  @Test
  void nothingMetMeansEverythingMissing() {
    assertThat(Requirement.missing(List.of()))
        .containsExactly(Requirement.SPORTS, Requirement.USES, Requirement.SCHEDULE);
  }

  @Test
  void onlyWhatIsNotMetIsMissing() {
    assertThat(Requirement.missing(List.of(Requirement.SCHEDULE, Requirement.SPORTS)))
        .containsExactly(Requirement.USES);
    assertThat(Requirement.missing(EnumSet.allOf(Requirement.class))).isEmpty();
  }
}
