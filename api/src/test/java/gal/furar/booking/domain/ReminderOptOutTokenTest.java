// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A ligazón «Non quero recordatorios» (ADR-0035): válida, caducada, manipulada ou allea. */
class ReminderOptOutTokenTest {

  static final String SECRET = "a-test-secret-that-is-long-enough-for-hmac";
  static final UUID MUNICIPALITY = UUID.fromString("019a0b6e-0000-7000-8000-0000000000aa");
  static final UUID BOOKING = UUID.fromString("019a0b6e-0000-7000-8000-00000000b001");
  static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

  @Test
  void aFreshLinkSaysWhichBookingAndCarriesNoPersonalData() {
    String token = ReminderOptOutToken.issue(SECRET, MUNICIPALITY, BOOKING, NOW);

    assertThat(token).startsWith("v1." + BOOKING + ".").doesNotContain("@");
    assertThat(ReminderOptOutToken.verify(SECRET, MUNICIPALITY, token, NOW.plusSeconds(60)))
        .isEqualTo(new ReminderOptOutToken.Valid(BOOKING));
  }

  @Test
  void itExpires() {
    String token = ReminderOptOutToken.issue(SECRET, MUNICIPALITY, BOOKING, NOW);
    Instant later = NOW.plus(ReminderOptOutToken.VALIDITY).plus(Duration.ofSeconds(1));

    assertThat(ReminderOptOutToken.verify(SECRET, MUNICIPALITY, token, later))
        .isInstanceOf(ReminderOptOutToken.Expired.class);
  }

  @Test
  void aTamperedForeignOrMalformedLinkIsInvalid() {
    String token = ReminderOptOutToken.issue(SECRET, MUNICIPALITY, BOOKING, NOW);
    String otherBooking = token.replace(BOOKING.toString(), UUID.randomUUID().toString());
    String longer =
        token.replaceFirst(
            "\\.(\\d+)\\.", "." + NOW.plus(Duration.ofDays(3650)).getEpochSecond() + ".");

    assertThat(ReminderOptOutToken.verify(SECRET, MUNICIPALITY, otherBooking, NOW))
        .isInstanceOf(ReminderOptOutToken.Invalid.class);
    assertThat(ReminderOptOutToken.verify(SECRET, MUNICIPALITY, longer, NOW))
        .isInstanceOf(ReminderOptOutToken.Invalid.class);
    assertThat(ReminderOptOutToken.verify(SECRET, UUID.randomUUID(), token, NOW))
        .isInstanceOf(ReminderOptOutToken.Invalid.class);
    assertThat(
            ReminderOptOutToken.verify(
                "another-secret-that-is-long-enough!!", MUNICIPALITY, token, NOW))
        .isInstanceOf(ReminderOptOutToken.Invalid.class);
    for (String bad : new String[] {"", "x", "v1.a.b.c", "v1..", token + "%%", "v2.x.1.AAAA"}) {
      assertThat(ReminderOptOutToken.verify(SECRET, MUNICIPALITY, bad, NOW))
          .as(bad)
          .isInstanceOf(ReminderOptOutToken.Invalid.class);
    }
    assertThat(ReminderOptOutToken.verify(SECRET, MUNICIPALITY, null, NOW))
        .isInstanceOf(ReminderOptOutToken.Invalid.class);
  }
}
