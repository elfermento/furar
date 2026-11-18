// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** A hora de exclusividade da lista de espera e a pausa nocturna (ADR-0041). */
class WaitlistClockTest {

  static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
  final WaitlistClock clock =
      new WaitlistClock(Duration.ofHours(1), LocalTime.of(22, 0), LocalTime.of(8, 0), ZONE);

  static Instant at(String local) {
    return LocalDateTime.parse(local).atZone(ZONE).toInstant();
  }

  @Test
  void byDayItLastsAnHour() {
    WaitlistNotice n = clock.notice(at("2026-12-01T10:00"));
    assertThat(n.notifiedAt()).isEqualTo(at("2026-12-01T10:00"));
    assertThat(n.exclusiveUntil()).isEqualTo(at("2026-12-01T11:00"));
    assertThat(n.mailedAt()).isNull();
  }

  @Test
  void endingExactlyAtTenIsNotPaused() {
    assertThat(clock.notice(at("2026-12-01T21:00")).exclusiveUntil())
        .isEqualTo(at("2026-12-01T22:00"));
  }

  @Test
  void crossingTenPausesAndResumesAtEight() {
    WaitlistNotice n = clock.notice(at("2026-12-01T21:30"));
    assertThat(n.notifiedAt()).isEqualTo(at("2026-12-01T21:30"));
    assertThat(n.exclusiveUntil()).isEqualTo(at("2026-12-02T08:30"));
  }

  @Test
  void atNightTheNoticeWaitsUntilEight() {
    WaitlistNotice late = clock.notice(at("2026-12-01T23:15"));
    assertThat(late.notifiedAt()).isEqualTo(at("2026-12-02T08:00"));
    assertThat(late.exclusiveUntil()).isEqualTo(at("2026-12-02T09:00"));
    WaitlistNotice early = clock.notice(at("2026-12-02T07:59"));
    assertThat(early.notifiedAt()).isEqualTo(at("2026-12-02T08:00"));
    assertThat(clock.quiet(at("2026-12-02T08:00"))).isFalse();
    assertThat(clock.quiet(at("2026-12-01T22:00"))).isTrue();
  }

  @Test
  void aLongExclusiveTimeSkipsSeveralNights() {
    WaitlistClock day =
        new WaitlistClock(Duration.ofHours(20), LocalTime.of(22, 0), LocalTime.of(8, 0), ZONE);
    // 12:00–22:00 (10 h) e 8:00–18:00 do día seguinte (10 h).
    assertThat(day.notice(at("2026-12-01T12:00")).exclusiveUntil())
        .isEqualTo(at("2026-12-02T18:00"));
  }

  @Test
  void theNightAroundTheClockChangeStillEndsAtEightLocal() {
    // A noite do 24 ao 25 de outubro de 2026 ten unha hora máis: a pausa remata ás 8:00 locais.
    assertThat(clock.notice(at("2026-10-24T21:30")).exclusiveUntil())
        .isEqualTo(at("2026-10-25T08:30"));
  }

  @Test
  void withoutQuietHoursItIsJustTheDuration() {
    WaitlistClock always =
        new WaitlistClock(Duration.ofHours(1), LocalTime.of(0, 0), LocalTime.of(0, 0), ZONE);
    assertThat(always.notice(at("2026-12-01T23:30")).exclusiveUntil())
        .isEqualTo(at("2026-12-02T00:30"));
  }

  @Test
  void aDaytimeQuietWindowAlsoWorks() {
    WaitlistClock siesta =
        new WaitlistClock(Duration.ofHours(1), LocalTime.of(14, 0), LocalTime.of(16, 0), ZONE);
    assertThat(siesta.notice(at("2026-12-01T13:30")).exclusiveUntil())
        .isEqualTo(at("2026-12-01T16:30"));
    assertThat(siesta.notice(at("2026-12-01T15:00")).notifiedAt())
        .isEqualTo(at("2026-12-01T16:00"));
  }
}
