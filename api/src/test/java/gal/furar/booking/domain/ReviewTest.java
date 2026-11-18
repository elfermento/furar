// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import static gal.furar.booking.domain.Bookings.assertFails;
import static gal.furar.booking.domain.Bookings.confirmed;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * As valoracións sen Spring (ADR-0037): a nota e o comentario e que reserva se pode valorar (só
 * unha pasada á que se foi; con control de entrada, só a validada).
 */
class ReviewTest {

  static final String STAFF = "sub-staff";

  static Booking booking() {
    return confirmed(Price.free(), BookingRules.none());
  }

  static Review review(int stars, @Nullable String comment) {
    return Review.of(UUID.randomUUID(), booking(), stars, comment, Bookings.NOW);
  }

  @Test
  void theStarsGoFromOneToFive() {
    assertThat(review(1, null).stars()).isEqualTo(1);
    assertThat(review(5, null).stars()).isEqualTo(5);
    assertFails(BookingError.REVIEW_INVALID, () -> review(0, null));
    assertFails(BookingError.REVIEW_INVALID, () -> review(6, null));
  }

  @Test
  void theCommentIsTrimmedEmptyMeansNoneAndItTakes300CharactersAtMost() {
    assertThat(review(4, "  Boa pista.  ").comment()).isEqualTo("Boa pista.");
    assertThat(review(4, "   ").comment()).isNull();
    assertThat(review(4, "a".repeat(300)).comment()).hasSize(300);
    assertThat(review(4, "ñ".repeat(300)).comment()).as("caracteres, non bytes").hasSize(300);
    assertFails(BookingError.REVIEW_INVALID, () -> review(4, "a".repeat(301)));
  }

  @Test
  void itTakesTheFacilityAndUseOfItsBooking() {
    Booking b = booking();
    Review r = Review.of(UUID.randomUUID(), b, 3, null, Bookings.NOW);
    assertThat(r.bookingId()).isEqualTo(b.id().value());
    assertThat(r.facilityId()).isEqualTo(b.slot().facilityId());
    assertThat(r.useId()).isEqualTo(b.slot().useId());
    assertThat(r.hidden()).isFalse();
  }

  @Test
  void aHiddenCommentIsNotPublicButTheStarsStay() {
    Review r = review(2, "Texto");
    Review hidden =
        new Review(
            r.id(),
            r.bookingId(),
            r.facilityId(),
            r.useId(),
            r.stars(),
            r.comment(),
            r.createdAt(),
            Bookings.NOW);
    assertThat(r.publicComment()).isEqualTo("Texto");
    assertThat(hidden.publicComment()).isNull();
    assertThat(hidden.stars()).isEqualTo(2);
  }

  @Test
  void onlyAnEndedConfirmedBookingWithoutANoShowCanBeReviewed() {
    Booking b = booking();
    Instant end = b.slot().end();

    assertThat(ReviewRule.reviewable(b, end.minusSeconds(1), null))
        .as("aínda non rematou")
        .isFalse();
    assertThat(ReviewRule.reviewable(b, end, null)).isTrue();
    assertThat(ReviewRule.reviewable(b.cancelByMunicipality(Bookings.NOW, STAFF), end, null))
        .as("cancelada")
        .isFalse();
    assertThat(ReviewRule.reviewable(b.markNoShow(end, STAFF), end, null))
        .as("non presentado")
        .isFalse();
    assertThat(ReviewRule.reviewable(b.validate(b.slot().start(), STAFF), end, null)).isTrue();
  }

  @Test
  void withEntryControlOnlyAValidatedBookingCanBeReviewed() {
    Booking b = booking();
    Instant end = b.slot().end();
    Instant before = b.slot().start().minus(Duration.ofDays(1));
    Instant after = b.slot().start().plusSeconds(1);

    assertThat(ReviewRule.reviewable(b, end, before)).as("sen validar, agarda a falta").isFalse();
    assertThat(ReviewRule.reviewable(b.validate(b.slot().start(), STAFF), end, before)).isTrue();
    assertThat(ReviewRule.reviewable(b, end, after))
        .as("o control acendeuse despois de empezar: coma sen control")
        .isTrue();
  }
}
