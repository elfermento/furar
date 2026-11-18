// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * A regra da penalización das faltas sen Spring (ADR-0032, puntos 7 e 11): a xanela dos días que
 * contan e o final do bloqueo calcúlanse en hora de Galicia, tamén cando cambia a hora.
 */
class NoShowRuleTest {

  static final Instant LONG_AGO = Instant.parse("2020-01-01T00:00:00Z");

  @Test
  void offByDefaultAndWithoutAWindow() {
    NoShowRule off = new NoShowRule(null, 3, 30, 15);
    assertThat(off.enabled()).isFalse();
    assertThatThrownBy(() -> off.countFrom(Instant.parse("2026-11-09T18:40:00Z")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void theWindowStartsAtMidnightInGaliciaThatManyDaysAgo() {
    NoShowRule rule = new NoShowRule(LONG_AGO, 3, 30, 15);
    // Luns 9 de novembro de 2026, 19:40 en Galicia (UTC+1): 30 días atrás é o 10 de outubro, que
    // aínda ía co horario de verán (UTC+2).
    Instant now = Instant.parse("2026-11-09T18:40:00Z");

    assertThat(rule.countFrom(now)).isEqualTo(Instant.parse("2026-10-09T22:00:00Z"));
  }

  @Test
  void faultsBeforeThePenaltyWasTurnedOnDoNotCount() {
    Instant since = Instant.parse("2026-11-01T10:00:00Z");
    NoShowRule rule = new NoShowRule(since, 3, 30, 15);

    assertThat(rule.countFrom(Instant.parse("2026-11-09T18:40:00Z"))).isEqualTo(since);
    assertThat(rule.countFrom(Instant.parse("2026-12-20T18:40:00Z")))
        .as("pasada a xanela, manda a xanela")
        .isEqualTo(Instant.parse("2026-11-19T23:00:00Z"));
  }

  @Test
  void theThresholdIsReachedAtThatManyFaults() {
    NoShowRule rule = new NoShowRule(LONG_AGO, 3, 30, 15);
    assertThat(rule.reached(2)).isFalse();
    assertThat(rule.reached(3)).isTrue();
    assertThat(rule.reached(4)).isTrue();
  }

  @Test
  void theBlockEndsAtTheEndOfTheDayInGaliciaOfTodayPlusTheBlockDays() {
    NoShowRule rule = new NoShowRule(LONG_AGO, 3, 30, 15);
    Instant now = Instant.parse("2026-11-09T18:40:00Z");

    Instant end = rule.blockEnd(now);

    assertThat(end).isEqualTo(Instant.parse("2026-11-24T23:00:00Z"));
    assertThat(NoShowRule.lastBlockedDay(end)).isEqualTo(LocalDate.of(2026, 11, 24));
  }

  @Test
  void theBlockEndFollowsTheClockChange() {
    NoShowRule rule = new NoShowRule(LONG_AGO, 3, 30, 15);
    // 20 de marzo de 2027, 23:30 en Galicia (UTC+1); o 28 de marzo cambia a hora (UTC+2).
    Instant now = Instant.parse("2027-03-20T22:30:00Z");

    Instant end = rule.blockEnd(now);

    assertThat(end).isEqualTo(Instant.parse("2027-04-04T22:00:00Z"));
    assertThat(NoShowRule.lastBlockedDay(end)).isEqualTo(LocalDate.of(2027, 4, 4));
  }
}
