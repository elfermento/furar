// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.Period;
import org.junit.jupiter.api.Test;

/** Os tres tramos do rexistro (ADR-0020, revisión do 2026-10-29): visible, bloqueado e borrado. */
class AuditRetentionTest {

  static final Instant NOW = Instant.parse("2029-03-01T10:00:00Z");

  @Test
  void defaultsAreOneYearVisibleAndThreeKept() {
    assertThat(AuditRetention.DEFAULT.visible()).isEqualTo(Period.ofYears(1));
    assertThat(AuditRetention.DEFAULT.retention()).isEqualTo(Period.ofYears(3));
  }

  @Test
  void cutoffsAreCalendarYearsInUtc() {
    AuditRetention policy = AuditRetention.DEFAULT;
    assertThat(policy.visibleSince(NOW)).isEqualTo(Instant.parse("2028-03-01T10:00:00Z"));
    assertThat(policy.keptSince(NOW)).isEqualTo(Instant.parse("2026-03-01T10:00:00Z"));
    // Desde o 29 de febreiro dun bisesto, un ano antes é o 28.
    assertThat(policy.visibleSince(Instant.parse("2028-02-29T00:00:00Z")))
        .isEqualTo(Instant.parse("2027-02-28T00:00:00Z"));
  }

  @Test
  void anEventIsVisibleThenBlockedThenGone() {
    AuditRetention policy = AuditRetention.DEFAULT;
    Instant visibleSince = policy.visibleSince(NOW);
    Instant keptSince = policy.keptSince(NOW);

    assertThat(policy.isBlocked(NOW.minusSeconds(60), NOW)).as("recent").isFalse();
    assertThat(policy.isBlocked(visibleSince, NOW)).as("exactly a year: still visible").isFalse();
    assertThat(policy.isBlocked(visibleSince.minusMillis(1), NOW)).isTrue();
    assertThat(policy.isBlocked(keptSince, NOW)).as("exactly three years: still kept").isTrue();
    assertThat(policy.isBlocked(keptSince.minusMillis(1), NOW)).as("purged").isFalse();
  }

  @Test
  void retentionIsAtLeastAYearAndLongerThanVisible() {
    assertThat(new AuditRetention(Period.ofMonths(6), Period.ofYears(1)).retention())
        .isEqualTo(Period.ofYears(1));
    assertThat(new AuditRetention(Period.ofYears(1), Period.ofMonths(13)).retention())
        .isEqualTo(Period.ofMonths(13));

    assertThatThrownBy(() -> new AuditRetention(Period.ofDays(30), Period.ofDays(364)))
        .hasMessageContaining("at least P1Y");
    assertThatThrownBy(() -> new AuditRetention(Period.ofYears(1), Period.ofYears(1)))
        .hasMessageContaining("must be longer");
    assertThatThrownBy(() -> new AuditRetention(Period.ofYears(2), Period.ofMonths(18)))
        .hasMessageContaining("must be longer");
    assertThatThrownBy(() -> new AuditRetention(Period.ZERO, Period.ofYears(3)))
        .hasMessageContaining("must be positive");
    assertThatThrownBy(() -> new AuditRetention(Period.ofDays(-1), Period.ofYears(3)))
        .hasMessageContaining("must be positive");
  }
}
