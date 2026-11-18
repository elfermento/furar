// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** O filtro dos agregados: ao revés ou de máis de cinco anos e un día é un erro de programación. */
class StatsQueryTest {

  static final LocalDate FROM = LocalDate.parse("2023-01-01");

  @Test
  void acceptsUpToFiveYearsAndADay() {
    assertThat(new StatsQuery(FROM, FROM, null, Set.of()).to()).isEqualTo(FROM);
    assertThat(new StatsQuery(FROM, FROM.plusYears(5), null, Set.of()).to())
        .isEqualTo(LocalDate.parse("2028-01-01"));
  }

  @Test
  void rejectsBackwardsAndTooLongRanges() {
    assertThatThrownBy(() -> new StatsQuery(FROM, FROM.minusDays(1), null, Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new StatsQuery(FROM, FROM.plusYears(5).plusDays(1), null, Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
