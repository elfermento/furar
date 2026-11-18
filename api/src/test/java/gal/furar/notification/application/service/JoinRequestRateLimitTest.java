// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.notification.domain.JoinRequestRateLimitedException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Límite das solicitudes de alta por IP e día, na hora de Galicia (ADR-0042). */
class JoinRequestRateLimitTest {

  /** 18:10 en Galicia (UTC+1 en novembro). */
  static final Instant EVENING = Instant.parse("2026-11-17T17:10:00Z");

  @Test
  void afterTheLimitTheSameAddressWaitsUntilMidnightInGalicia() {
    JoinRequestRateLimit limit = new JoinRequestRateLimit(Clock.fixed(EVENING, ZoneOffset.UTC), 5);
    for (int i = 0; i < 5; i++) {
      limit.check("203.0.113.7");
    }
    assertThatThrownBy(() -> limit.check("203.0.113.7"))
        .isInstanceOfSatisfying(
            JoinRequestRateLimitedException.class,
            e -> Assertions.assertThat(e.retryAfterSeconds()).isEqualTo((5 * 60 + 50) * 60));
    // Outro enderezo, á súa conta.
    assertThatCode(() -> limit.check("203.0.113.8")).doesNotThrowAnyException();
  }

  @Test
  void theCountsStartAgainAtMidnightInGaliciaNotInUtc() {
    MutableClock clock = new MutableClock(EVENING);
    JoinRequestRateLimit limit = new JoinRequestRateLimit(clock, 1);
    limit.check("203.0.113.7");
    assertThatThrownBy(() -> limit.check("203.0.113.7"))
        .isInstanceOf(JoinRequestRateLimitedException.class);
    // 23:30 en UTC xa é o día seguinte en Galicia.
    clock.now = Instant.parse("2026-11-17T23:30:00Z");
    assertThatCode(() -> limit.check("203.0.113.7")).doesNotThrowAnyException();
  }

  @Test
  void anHourLaterTheSameDayItIsStillRefused() {
    MutableClock clock = new MutableClock(EVENING);
    JoinRequestRateLimit limit = new JoinRequestRateLimit(clock, 1);
    limit.check("203.0.113.7");
    clock.now = EVENING.plusSeconds(3600);
    assertThatThrownBy(() -> limit.check("203.0.113.7"))
        .isInstanceOf(JoinRequestRateLimitedException.class);
  }

  @Test
  void zeroMeansNoLimit() {
    JoinRequestRateLimit limit = new JoinRequestRateLimit(Clock.fixed(EVENING, ZoneOffset.UTC), 0);
    for (int i = 0; i < 50; i++) {
      limit.check("203.0.113.7");
    }
  }

  static final class MutableClock extends Clock {
    Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
