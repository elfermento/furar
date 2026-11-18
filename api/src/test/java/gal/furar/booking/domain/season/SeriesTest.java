// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain.season;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.domain.Price;
import org.junit.jupiter.api.Test;

class SeriesTest {

  @Test
  void aSeasonOfAPaidUseStartsPendingAndAnyOtherStartsFree() {
    assertThat(Series.PaymentStatus.initial(Price.paid(800)))
        .isEqualTo(Series.PaymentStatus.PENDING);
    assertThat(Series.PaymentStatus.initial(Price.free())).isEqualTo(Series.PaymentStatus.FREE);
    assertThat(Series.PaymentStatus.initial(Price.none())).isEqualTo(Series.PaymentStatus.FREE);
  }
}
