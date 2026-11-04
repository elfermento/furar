// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Rangos de datas cos dous extremos incluídos. */
class UsePeriodTest {

  static UsePeriod p(String from, String to) {
    return new UsePeriod(LocalDate.parse(from), LocalDate.parse(to));
  }

  @Test
  void bothEndsCount() {
    assertThat(p("2026-07-01", "2026-07-15").overlaps(p("2026-07-15", "2026-07-20"))).isTrue();
    assertThat(p("2026-07-01", "2026-07-14").overlaps(p("2026-07-15", "2026-07-20"))).isFalse();
    assertThat(p("2026-07-10", "2026-07-10").overlaps(p("2026-07-01", "2026-07-31"))).isTrue();
  }

  @Test
  void uncoveredSubtractsEveryCoveredRange() {
    assertThat(
            UsePeriod.uncovered(
                List.of(p("2026-07-01", "2026-07-31")),
                List.of(p("2026-07-05", "2026-07-10"), p("2026-07-20", "2026-08-10"))))
        .containsExactly(p("2026-07-01", "2026-07-04"), p("2026-07-11", "2026-07-19"));
    assertThat(UsePeriod.uncovered(List.of(p("2026-07-01", "2026-07-31")), List.of()))
        .containsExactly(p("2026-07-01", "2026-07-31"));
    assertThat(
            UsePeriod.uncovered(
                List.of(p("2026-07-01", "2026-07-02")), List.of(p("2026-06-01", "2026-07-31"))))
        .isEmpty();
  }
}
