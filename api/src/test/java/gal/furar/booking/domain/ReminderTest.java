// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Cando toca o recordatorio (ADR-0035): con prazo, sen prazo e reservas feitas tarde. */
class ReminderTest {

  static final Instant START = Instant.parse("2026-11-20T18:00:00Z");
  static final Instant CREATED = Instant.parse("2026-11-01T10:00:00Z");

  @Test
  void withADeadlineIt24HoursBeforeTheDeadline() {
    Instant deadline = START.minus(Duration.ofHours(12));
    Instant at = Reminder.at(deadline);

    assertThat(at).isEqualTo(Instant.parse("2026-11-19T06:00:00Z"));
    assertThat(Reminder.hasDeadline(deadline, START)).isTrue();
    assertThat(Reminder.due(CREATED, deadline, at.minusSeconds(1))).isFalse();
    assertThat(Reminder.due(CREATED, deadline, at)).isTrue();
    assertThat(Reminder.due(CREATED, deadline, deadline.minusSeconds(1))).isTrue();
    assertThat(Reminder.due(CREATED, deadline, deadline)).as("o prazo xa pasou").isFalse();
  }

  @Test
  void withoutADeadlineIt24HoursBeforeTheStart() {
    // Sen prazo, o prazo é o inicio.
    assertThat(Reminder.hasDeadline(START, START)).isFalse();
    assertThat(Reminder.at(START)).isEqualTo(Instant.parse("2026-11-19T18:00:00Z"));
    assertThat(Reminder.due(CREATED, START, Instant.parse("2026-11-19T18:00:00Z"))).isTrue();
    assertThat(Reminder.due(CREATED, START, START)).isFalse();
  }

  @Test
  void notForABookingMadeAfterTheMoment() {
    Instant at = Reminder.at(START);
    assertThat(Reminder.due(at, START, at.plusSeconds(60))).as("feita xusto entón").isTrue();
    assertThat(Reminder.due(at.plusSeconds(1), START, at.plusSeconds(60))).isFalse();
  }
}
