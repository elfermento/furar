// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import static gal.furar.booking.domain.Bookings.HOLDER;
import static gal.furar.booking.domain.Bookings.NOW;
import static gal.furar.booking.domain.Bookings.assertFails;
import static gal.furar.booking.domain.Bookings.confirmed;
import static gal.furar.booking.domain.Bookings.whole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.Language;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * A reserva que fai o persoal no nome dunha persoa sen conta (especificación, bloque 5, decidido o
 * 2026-11-04): quen a fixo, para quen é, o cambio sen o prazo da persoa e a corrección dos datos.
 */
class BookingForAnotherPersonTest {

  static final String STAFF = "sub-staff";
  static final Holder ANA =
      Holder.walkIn("Ana Pérez", "ana@example.org", "666 123 456", Language.GL);

  private static Booking forAna(BookingRules rules) {
    return Booking.createFor(
        BookingId.newId(),
        new BookingCode("FUR-AB23"),
        whole(48, 1),
        Price.paid(800),
        rules,
        ANA,
        STAFF,
        NOW);
  }

  @Test
  void aWalkInHolderHasNoAccountAndMayLackTheEmail() {
    Holder noMail = Holder.walkIn("Ana", null, null, Language.ES);
    assertThat(noMail.hasAccount()).isFalse();
    assertThat(noMail.email()).isNull();
    assertThat(HOLDER.hasAccount()).isTrue();
    // Coa conta, sempre correo e nunca teléfono (P7).
    assertThatThrownBy(() -> new Holder("sub", "Ana", null, null, Language.GL))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Holder("sub", "Ana", "a@b.gal", "600", Language.GL))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Holder.walkIn("Ana", null, "abc", Language.GL))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Holder.walkIn("", null, null, Language.GL))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theStaffBookingKeepsWhoMadeItAndTheSameRules() {
    Booking booking = forAna(BookingRules.none());
    assertThat(booking.bookedBy()).isEqualTo(STAFF);
    assertThat(booking.holder()).isEqualTo(ANA);
    assertThat(booking.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(confirmed(Price.free(), BookingRules.none()).bookedBy()).isNull();
    assertFails(
        BookingError.TOO_LONG,
        () ->
            Booking.createFor(
                BookingId.newId(),
                new BookingCode("FUR-AB23"),
                whole(48, 3),
                Price.free(),
                new BookingRules(null, null, 2, null, null, null),
                ANA,
                STAFF,
                NOW));
  }

  @Test
  void withoutAccountTheBookingIsAlwaysMadeByTheStaff() {
    assertThatThrownBy(
            () ->
                Booking.create(
                    BookingId.newId(),
                    new BookingCode("FUR-AB23"),
                    whole(48, 1),
                    Price.free(),
                    BookingRules.none(),
                    ANA,
                    NOW))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                Booking.createFor(
                    BookingId.newId(),
                    new BookingCode("FUR-AB23"),
                    whole(48, 1),
                    Price.free(),
                    BookingRules.none(),
                    HOLDER,
                    STAFF,
                    NOW))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void theMunicipalityChangesAfterThePersonsDeadline() {
    Booking booking = forAna(new BookingRules(null, null, null, null, null, 24));
    Instant late = booking.cancelUntil().plus(Duration.ofMinutes(1));
    assertFails(
        BookingError.CANCEL_DEADLINE_PASSED,
        () -> booking.reschedule(whole(47, 1), Price.paid(800), BookingRules.none(), late));
    Booking moved =
        booking.rescheduleByMunicipality(whole(47, 1), Price.paid(800), BookingRules.none(), late);
    assertThat(moved.slot()).isEqualTo(whole(47, 1));
    assertThat(moved.bookedBy()).isEqualTo(STAFF);
    assertThat(moved.code()).isEqualTo(booking.code());
    Booking cancelled = booking.cancelByMunicipality(late, STAFF);
    assertFails(
        BookingError.NOT_CANCELLABLE,
        () ->
            cancelled.rescheduleByMunicipality(
                whole(47, 1), Price.paid(800), BookingRules.none(), late));
  }

  @Test
  void onlyTheStaffBookingsCorrectWhoTheyAreFor() {
    Booking booking = forAna(BookingRules.none());
    Holder fixed = Holder.walkIn("Ana Pérez Rey", null, "666 123 456", Language.GL);
    Booking corrected = booking.withHolder(fixed, NOW);
    assertThat(corrected.holder()).isEqualTo(fixed);
    assertThat(corrected.slot()).isEqualTo(booking.slot());
    assertFails(
        BookingError.HOLDER_NOT_EDITABLE,
        () -> confirmed(Price.free(), BookingRules.none()).withHolder(fixed, NOW));
    assertFails(BookingError.HOLDER_NOT_EDITABLE, () -> booking.withHolder(HOLDER, NOW));
  }
}
