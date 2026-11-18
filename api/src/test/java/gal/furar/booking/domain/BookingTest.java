// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import static gal.furar.booking.domain.Bookings.FACILITY;
import static gal.furar.booking.domain.Bookings.HOLDER;
import static gal.furar.booking.domain.Bookings.NOW;
import static gal.furar.booking.domain.Bookings.USE;
import static gal.furar.booking.domain.Bookings.assertFails;
import static gal.furar.booking.domain.Bookings.confirmed;
import static gal.furar.booking.domain.Bookings.paid;
import static gal.furar.booking.domain.Bookings.whole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * O agregado reserva sen Spring (ADR-0025, puntos 8, 11 e 12): creación, cada transición de estado
 * e de pago, cambio e as invariantes da táboa {@code booking}.
 */
class BookingTest {

  static final Instant LATER = NOW.plus(Duration.ofHours(1));

  // --- Crear ---

  @Test
  void aPaidUseLeavesThePaymentPendingWithTheTotalOfItsSlots() {
    Booking booking =
        Booking.create(
            BookingId.newId(),
            new BookingCode("FUR-AB23"),
            whole(48, 2),
            Price.paid(800),
            BookingRules.none(),
            HOLDER,
            NOW);
    assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
    assertThat(booking.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(booking.price()).isEqualTo(Price.paid(1600));
    assertThat(booking.version()).isNull();
    assertThat(booking.createdAt()).isEqualTo(NOW);
    assertThat(booking.cancelledAt()).isNull();
  }

  @Test
  void freeAndUnpricedUsesHaveNothingToCollect() {
    assertThat(confirmed(Price.free(), BookingRules.none()).paymentStatus())
        .isEqualTo(PaymentStatus.NONE);
    assertThat(confirmed(Price.none(), BookingRules.none()).paymentStatus())
        .isEqualTo(PaymentStatus.NONE);
  }

  @Test
  void theDeadlineToCancelIsFrozenFromTheRules() {
    Booking withRule = confirmed(Price.free(), rules(24));
    assertThat(withRule.cancelUntil())
        .isEqualTo(withRule.slot().start().minus(Duration.ofHours(24)));
    Booking withoutRule = confirmed(Price.free(), BookingRules.none());
    assertThat(withoutRule.cancelUntil()).isEqualTo(withoutRule.slot().start());
    assertThat(withRule.rules()).isEqualTo(rules(24));
  }

  @Test
  void createChecksAdvanceAndLength() {
    BookingRules rules = new BookingRules(null, null, 2, null, null, null);
    assertFails(
        BookingError.TOO_LONG,
        () ->
            Booking.create(
                BookingId.newId(),
                new BookingCode("FUR-AB23"),
                whole(48, 3),
                Price.free(),
                rules,
                HOLDER,
                NOW));
    assertFails(
        BookingError.TOO_SOON,
        () ->
            Booking.create(
                BookingId.newId(),
                new BookingCode("FUR-AB23"),
                whole(0, 1),
                Price.free(),
                rules,
                HOLDER,
                NOW));
  }

  // --- Cancelar ---

  @Test
  void thePersonCancelsBeforeTheDeadline() {
    Booking cancelled = confirmed(Price.free(), rules(24)).cancelByPerson(LATER, "sub-1");
    assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED_BY_PERSON);
    assertThat(cancelled.cancelledAt()).isEqualTo(LATER);
    assertThat(cancelled.cancelledBy()).isEqualTo("sub-1");
    assertThat(cancelled.updatedAt()).isEqualTo(LATER);
    assertThat(cancelled.paymentStatus()).isEqualTo(PaymentStatus.NONE);
  }

  @Test
  void thePersonCannotCancelOnceTheDeadlineHasPassed() {
    Booking booking = confirmed(Price.free(), rules(24));
    assertFails(
        BookingError.CANCEL_DEADLINE_PASSED,
        () -> booking.cancelByPerson(booking.cancelUntil(), "sub-1"));
    assertThat(booking.canCancel(booking.cancelUntil())).isFalse();
    assertThat(booking.canCancel(booking.cancelUntil().minusSeconds(1))).isTrue();
  }

  @Test
  void cancellingAPaidBookingLeavesItToRefund() {
    assertThat(paid().cancelByPerson(LATER, "sub-1").paymentStatus())
        .isEqualTo(PaymentStatus.TO_REFUND);
    assertThat(paid().cancelByMunicipality(LATER, "staff-1").paymentStatus())
        .isEqualTo(PaymentStatus.TO_REFUND);
  }

  @Test
  void cancellingAPendingBookingKeepsItPending() {
    Booking cancelled =
        confirmed(Price.paid(800), BookingRules.none()).cancelByPerson(LATER, "sub-1");
    assertThat(cancelled.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
  }

  @Test
  void theMunicipalityCancelsEvenAfterTheDeadline() {
    Booking booking = confirmed(Price.free(), rules(24));
    Instant late = booking.slot().start().minusSeconds(60);
    Booking cancelled = booking.cancelByMunicipality(late, "staff-1");
    assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED_BY_MUNICIPALITY);
    assertThat(cancelled.cancelledBy()).isEqualTo("staff-1");
  }

  @Test
  void aCancelledBookingIsNotCancelledOrChangedAgain() {
    Booking cancelled = confirmed(Price.free(), BookingRules.none()).cancelByPerson(LATER, "s");
    assertFails(BookingError.NOT_CANCELLABLE, () -> cancelled.cancelByPerson(LATER, "s"));
    assertFails(BookingError.NOT_CANCELLABLE, () -> cancelled.cancelByMunicipality(LATER, "x"));
    assertFails(
        BookingError.NOT_CANCELLABLE,
        () -> cancelled.reschedule(whole(50, 1), Price.free(), BookingRules.none(), LATER));
    assertThat(cancelled.canCancel(LATER)).isFalse();
  }

  // --- Pago ---

  @Test
  void pendingBecomesPaidAndBack() {
    Booking pending = confirmed(Price.paid(800), BookingRules.none());
    Booking paid = pending.changePayment(PaymentStatus.PAID, LATER);
    assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.PAID);
    assertThat(paid.updatedAt()).isEqualTo(LATER);
    assertThat(paid.changePayment(PaymentStatus.PENDING, LATER).paymentStatus())
        .isEqualTo(PaymentStatus.PENDING);
  }

  @Test
  void toRefundBecomesRefundedAndBack() {
    Booking toRefund = paid().cancelByPerson(LATER, "sub-1");
    Booking refunded = toRefund.changePayment(PaymentStatus.REFUNDED, LATER);
    assertThat(refunded.paymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    assertThat(refunded.status()).isEqualTo(BookingStatus.CANCELLED_BY_PERSON);
    assertThat(refunded.changePayment(PaymentStatus.TO_REFUND, LATER).paymentStatus())
        .isEqualTo(PaymentStatus.TO_REFUND);
  }

  @ParameterizedTest
  @EnumSource(PaymentStatus.class)
  void everyOtherPaymentStepIsRejected(PaymentStatus target) {
    Booking pending = confirmed(Price.paid(800), BookingRules.none());
    Booking paid = paid();
    Booking toRefund = paid().cancelByPerson(LATER, "sub-1");
    Booking refunded = toRefund.changePayment(PaymentStatus.REFUNDED, LATER);
    Booking free = confirmed(Price.free(), BookingRules.none());
    Booking cancelledPending = pending.cancelByPerson(LATER, "sub-1");
    check(pending, target, EnumSet.of(PaymentStatus.PAID));
    check(paid, target, EnumSet.of(PaymentStatus.PENDING));
    check(toRefund, target, EnumSet.of(PaymentStatus.REFUNDED));
    check(refunded, target, EnumSet.of(PaymentStatus.TO_REFUND));
    check(free, target, EnumSet.noneOf(PaymentStatus.class));
    // Cancelada sen pagar: xa non se cobra.
    check(cancelledPending, target, EnumSet.noneOf(PaymentStatus.class));
  }

  private static void check(Booking booking, PaymentStatus target, EnumSet<PaymentStatus> valid) {
    if (valid.contains(target)) {
      assertThat(booking.changePayment(target, LATER).paymentStatus()).isEqualTo(target);
    } else {
      assertFails(
          BookingError.PAYMENT_TRANSITION_INVALID, () -> booking.changePayment(target, LATER));
    }
  }

  @Test
  void eachPaymentStepLeavesItsEvent() {
    assertThat(BookingEventKind.ofPayment(PaymentStatus.PAID)).isEqualTo(BookingEventKind.PAID);
    assertThat(BookingEventKind.ofPayment(PaymentStatus.REFUNDED))
        .isEqualTo(BookingEventKind.REFUNDED);
    assertThat(BookingEventKind.ofPayment(PaymentStatus.PENDING))
        .isEqualTo(BookingEventKind.PAYMENT_UNDONE);
    assertThat(BookingEventKind.ofPayment(PaymentStatus.TO_REFUND))
        .isEqualTo(BookingEventKind.PAYMENT_UNDONE);
    assertThatThrownBy(() -> BookingEventKind.ofPayment(PaymentStatus.NONE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // --- Cambiar ---

  @Test
  void rescheduleKeepsIdentityAndRecomputesPriceRulesAndDeadline() {
    Booking booking = confirmed(Price.paid(800), BookingRules.none());
    Booking changed = booking.reschedule(whole(72, 2), Price.paid(800), rules(12), LATER);
    assertThat(changed.id()).isEqualTo(booking.id());
    assertThat(changed.code()).isEqualTo(booking.code());
    assertThat(changed.slot()).isEqualTo(whole(72, 2));
    assertThat(changed.price()).isEqualTo(Price.paid(1600));
    assertThat(changed.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(changed.rules()).isEqualTo(rules(12));
    assertThat(changed.cancelUntil()).isEqualTo(whole(72, 2).start().minus(Duration.ofHours(12)));
    assertThat(changed.updatedAt()).isEqualTo(LATER);
    assertThat(changed.createdAt()).isEqualTo(booking.createdAt());
  }

  @Test
  void rescheduleFollowsThePriceKindOfTheNewUse() {
    Booking free = confirmed(Price.free(), BookingRules.none());
    assertThat(free.reschedule(whole(50, 1), Price.paid(500), BookingRules.none(), LATER))
        .extracting(Booking::paymentStatus)
        .isEqualTo(PaymentStatus.PENDING);
    Booking pending = confirmed(Price.paid(500), BookingRules.none());
    assertThat(pending.reschedule(whole(50, 1), Price.free(), BookingRules.none(), LATER))
        .extracting(Booking::paymentStatus)
        .isEqualTo(PaymentStatus.NONE);
  }

  @Test
  void aPaidBookingOnlyChangesToTheSamePrice() {
    Booking paid = paid();
    assertFails(
        BookingError.CHANGE_PRICE_PAID,
        () -> paid.reschedule(whole(50, 2), Price.paid(800), BookingRules.none(), LATER));
    Booking moved = paid.reschedule(whole(50, 1), Price.paid(800), BookingRules.none(), LATER);
    assertThat(moved.paymentStatus()).isEqualTo(PaymentStatus.PAID);
  }

  @Test
  void rescheduleStaysInTheSameFacilityAndBeforeTheDeadline() {
    Booking booking = confirmed(Price.free(), rules(24));
    BookedSlot elsewhere =
        new BookedSlot(
            UUID.randomUUID(),
            USE,
            null,
            RentalMode.WHOLE,
            whole(50, 1).start(),
            whole(50, 1).end(),
            1);
    assertFails(
        BookingError.CHANGE_OTHER_FACILITY,
        () -> booking.reschedule(elsewhere, Price.free(), BookingRules.none(), LATER));
    assertFails(
        BookingError.CANCEL_DEADLINE_PASSED,
        () ->
            booking.reschedule(
                whole(50, 1), Price.free(), BookingRules.none(), booking.cancelUntil()));
    assertFails(
        BookingError.TOO_LONG,
        () ->
            booking.reschedule(
                whole(50, 3),
                Price.free(),
                new BookingRules(null, null, 2, null, null, null),
                LATER));
  }

  // --- Realizada e invariantes ---

  @Test
  void aConfirmedBookingIsDoneOnceItEnds() {
    Booking booking = confirmed(Price.free(), BookingRules.none());
    assertThat(booking.isDone(booking.slot().end().minusSeconds(1))).isFalse();
    assertThat(booking.isDone(booking.slot().end())).isTrue();
    assertThat(booking.cancelByPerson(LATER, "s").isDone(booking.slot().end())).isFalse();
  }

  @Test
  void rehydrateRejectsWhatTheTableRejects() {
    BookedSlot slot = whole(48, 1);
    assertThatThrownBy(
            () ->
                Booking.rehydrate(
                    BookingId.newId(),
                    new BookingCode("FUR-AB23"),
                    slot,
                    BookingStatus.CONFIRMED,
                    PaymentStatus.TO_REFUND,
                    Price.paid(800),
                    BookingRules.none(),
                    slot.start(),
                    HOLDER,
                    null,
                    null,
                    0,
                    NOW,
                    NOW))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                Booking.rehydrate(
                    BookingId.newId(),
                    new BookingCode("FUR-AB23"),
                    slot,
                    BookingStatus.CONFIRMED,
                    PaymentStatus.PENDING,
                    Price.free(),
                    BookingRules.none(),
                    slot.start(),
                    HOLDER,
                    null,
                    null,
                    0,
                    NOW,
                    NOW))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                Booking.rehydrate(
                    BookingId.newId(),
                    new BookingCode("FUR-AB23"),
                    slot,
                    BookingStatus.CANCELLED_BY_PERSON,
                    PaymentStatus.NONE,
                    Price.free(),
                    BookingRules.none(),
                    slot.start(),
                    HOLDER,
                    null,
                    null,
                    0,
                    NOW,
                    NOW))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aBookedSlotHasAPartOnlyByParts() {
    Instant start = whole(48, 1).start();
    Instant end = whole(48, 1).end();
    assertThatThrownBy(() -> new BookedSlot(FACILITY, USE, null, RentalMode.PARTS, start, end, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new BookedSlot(FACILITY, USE, UUID.randomUUID(), RentalMode.SEATS, start, end, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BookedSlot(FACILITY, USE, null, RentalMode.WHOLE, end, end, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  static BookingRules rules(int cancelHours) {
    return new BookingRules(null, null, null, null, null, cancelHours);
  }
}
