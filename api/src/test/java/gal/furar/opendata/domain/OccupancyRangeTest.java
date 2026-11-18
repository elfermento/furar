// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** As datas da ocupación (ADR-0034, punto 10) e as filas dun día gardado (punto 16). */
class OccupancyRangeTest {

  static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

  @Test
  void withoutDatesTheLastFullMonth() {
    OccupancyRange r = OccupancyRange.of(null, null, TODAY, 12);
    assertThat(r.from()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(r.to()).isEqualTo(LocalDate.of(2026, 10, 31));
    assertThat(OccupancyRange.lastFullMonth(LocalDate.of(2026, 1, 3)).from())
        .isEqualTo(LocalDate.of(2025, 12, 1));
  }

  @Test
  void validDatesInsideTheLast12Months() {
    OccupancyRange r = OccupancyRange.of("2025-11-11", "2026-11-10", TODAY, 12);
    assertThat(r.days()).isEqualTo(365);
  }

  @Test
  void rejectsOneDateBadDatesOutsideTheWindowAndTooLong() {
    for (String[] bad :
        List.of(
            new String[] {"2026-10-01", null},
            new String[] {null, "2026-10-01"},
            new String[] {"2026-10-1", "2026-10-31"},
            new String[] {"2025-11-10", "2025-12-01"},
            new String[] {"2026-11-01", "2026-11-11"},
            new String[] {"2026-10-31", "2026-10-01"})) {
      assertThatThrownBy(() -> OccupancyRange.of(bad[0], bad[1], TODAY, 12))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("common.invalid-parameter");
    }
    assertThatThrownBy(() -> OccupancyRange.of("2026-09-01", "2026-10-01", TODAY, 1))
        .isInstanceOf(DomainException.class);
    assertThat(OccupancyRange.of("2026-09-01", "2026-09-30", TODAY, 1).days()).isEqualTo(30);
  }

  @Test
  void expandsQuartersWithOffsetsAndSkipsClosedOnes() {
    UUID facility = UUID.randomUUID();
    UUID use = UUID.randomUUID();
    Integer[] quarters = new Integer[96];
    Integer[] uses = new Integer[96];
    quarters[40] = 100;
    uses[40] = 1;
    quarters[95] = 0;
    List<QuarterRow> rows =
        QuarterRow.expand(
            facility,
            LocalDate.of(2026, 10, 15),
            java.util.Arrays.asList(quarters),
            List.of(use),
            java.util.Arrays.asList(uses));
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).start().toString()).isEqualTo("2026-10-15T10:00+02:00");
    assertThat(rows.get(0).useId()).isEqualTo(use);
    assertThat(rows.get(1).end().toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 16));
    assertThat(rows.get(1).useId()).isNull();
  }

  @Test
  void theRepeatedHourOfOctoberComesTwiceWithBothOffsets() {
    Integer[] quarters = new Integer[100];
    // 02:00 a 03:00 pasa dúas veces: tramos 8–11 (+02:00) e 12–15 (+01:00).
    quarters[8] = 0;
    quarters[12] = 0;
    List<QuarterRow> rows =
        QuarterRow.expand(
            UUID.randomUUID(),
            LocalDate.of(2026, 10, 25),
            java.util.Arrays.asList(quarters),
            List.of(),
            java.util.Arrays.asList(new Integer[100]));
    assertThat(rows.get(0).start().getOffset()).isEqualTo(ZoneOffset.ofHours(2));
    assertThat(rows.get(1).start().getOffset()).isEqualTo(ZoneOffset.ofHours(1));
    assertThat(rows.get(0).start().toLocalTime()).isEqualTo(rows.get(1).start().toLocalTime());
  }
}
