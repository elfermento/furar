// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** {@link FacilitySummary#withdrawn()}: os outros módulos non comparan o estado como texto. */
class FacilitySummaryTest {

  static FacilitySummary withStatus(String status) {
    return new FacilitySummary(
        UUID.randomUUID(),
        "Pista",
        status,
        true,
        false,
        new FacilitySummary.Address("Rúa do Alto", "12", "15570", "Narón"));
  }

  @Test
  void onlyWithdrawnIsWithdrawn() {
    assertThat(withStatus("WITHDRAWN").withdrawn()).isTrue();
    assertThat(withStatus("ACTIVE").withdrawn()).isFalse();
    assertThat(withStatus("TEMPORARILY_CLOSED").withdrawn()).isFalse();
  }
}
