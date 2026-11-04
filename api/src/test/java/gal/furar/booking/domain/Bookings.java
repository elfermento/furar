// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.ErrorCode;
import gal.furar.shared.domain.Language;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/** Datos de proba das reservas: un martes de novembro, pista enteira de 60 min. */
final class Bookings {

  /** Martes 3 de novembro de 2026, 10:00 en Galicia (UTC+1). */
  static final Instant NOW = Instant.parse("2026-11-03T09:00:00Z");

  static final UUID FACILITY = UUID.fromString("019a0b6e-0000-7000-8000-0000000000f1");
  static final UUID USE = UUID.fromString("019a0b6e-0000-7000-8000-0000000000a1");
  static final Holder HOLDER = new Holder("sub-1", "Ana Rey", "ana@example.org", Language.GL);

  private Bookings() {}

  /** {@code slots} franxas de 60 min que empezan {@code hoursFromNow} horas despois de agora. */
  static BookedSlot whole(long hoursFromNow, int slots) {
    Instant start = NOW.plus(Duration.ofHours(hoursFromNow));
    return new BookedSlot(
        FACILITY, USE, null, RentalMode.WHOLE, start, start.plus(Duration.ofHours(slots)), slots);
  }

  static Booking confirmed(Price unitPrice, BookingRules rules) {
    return Booking.create(
        BookingId.newId(),
        new BookingCode("FUR-AB23"),
        whole(48, 1),
        unitPrice,
        rules,
        HOLDER,
        NOW);
  }

  static Booking paid() {
    return confirmed(Price.paid(800), BookingRules.none()).changePayment(PaymentStatus.PAID, NOW);
  }

  static void assertFails(ErrorCode code, ThrowingCallable action) {
    assertThatThrownBy(action)
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
  }
}
