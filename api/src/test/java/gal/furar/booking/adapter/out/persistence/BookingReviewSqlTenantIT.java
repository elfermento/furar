// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.booking.StatsUnit;
import gal.furar.booking.application.port.out.BookingReviews.ReviewFilter;
import gal.furar.booking.application.port.out.StatsWindow;
import gal.furar.booking.domain.Review;
import gal.furar.booking.domain.ReviewOrder;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link BookingReviewSql} só le, garda e oculta as valoracións do
 * seu concello, coa RLS da transacción e co concello como parámetro; e a base garda unha por
 * reserva sen abortar a transacción.
 */
class BookingReviewSqlTenantIT extends AbstractTenantIT {

  static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
  static final Instant START = NOW.minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
  static final StatsWindow ALL_TIME =
      new StatsWindow(NOW.minus(30, ChronoUnit.DAYS), NOW.plusSeconds(60), null, Set.of());

  @Autowired BookingReviewSql subject;
  @Autowired TransactionTemplate tx;

  private int seeded;

  <T> T as(UUID municipality, Supplier<T> work) {
    return TenantContext.runAs(TenantRef.of(municipality), () -> tx.execute(s -> work.get()));
  }

  /** Unha reserva pasada (coa súa instalación e uso) e a súa valoración, por JDBC. */
  Review seedReview(UUID municipality, int stars, @Nullable String comment) {
    AttendanceSeeds.Seeded booking =
        AttendanceSeeds.booking(jdbc, municipality, START, "NONE", "RV", seeded++);
    UUID use =
        jdbc.sql("select use_id from booking where id = :id")
            .param("id", booking.booking())
            .query(UUID.class)
            .single();
    Review review =
        new Review(
            UUID.randomUUID(),
            booking.booking(),
            booking.facility(),
            use,
            stars,
            comment,
            NOW,
            null);
    jdbc.sql(
            """
            insert into booking_review
              (id, municipality_id, booking_id, facility_id, use_id, stars, comment, created_at)
            values (:id, :m, :b, :f, :u, :stars, :comment, :at)
            """)
        .param("id", review.id())
        .param("m", municipality)
        .param("b", review.bookingId())
        .param("f", review.facilityId())
        .param("u", review.useId())
        .param("stars", stars)
        .param("comment", comment)
        .param("at", BookingSql.utc(NOW))
        .update();
    return review;
  }

  int hiddenOf(UUID municipality) {
    return jdbc.sql(
            "select count(*) from booking_review where municipality_id = :m and hidden_at is not"
                + " null")
        .param("m", municipality)
        .query(Integer.class)
        .single();
  }

  @Override
  protected Object seed(UUID municipality) {
    return seedReview(municipality, 4, "Boa pista").id();
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return as(
        municipality,
        () ->
            subject.search(municipality, new ReviewFilter(ALL_TIME, Set.of()), 50, 0).stream()
                .<Object>map(Review::id)
                .toList());
  }

  /** Aínda pedindo co concello alleo como parámetro, a RLS da transacción non deixa ver nada. */
  @Test
  void theParameterAloneDoesNotOpenAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("review-a")));
    UUID b = id(createMunicipality(uniqueSlug("review-b")));
    Review theirs = seedReview(b, 2, "Mal");

    assertThat(as(a, () -> subject.find(b, theirs.id()))).isEmpty();
    assertThat(as(a, () -> subject.starsOf(b, List.of(theirs.bookingId())))).isEmpty();
    assertThat(as(a, () -> subject.tally(b, theirs.facilityId()))).containsExactly(0L, 0L);
    for (ReviewOrder order : ReviewOrder.values()) {
      assertThat(as(a, () -> subject.ofFacility(b, theirs.facilityId(), order, 10, 0))).isEmpty();
    }
    assertThat(as(a, () -> subject.countsByStars(b, new ReviewFilter(ALL_TIME, Set.of()))))
        .isEmpty();
    assertThat(as(a, () -> subject.perBucket(b, ALL_TIME, StatsUnit.YEAR))).isEmpty();
    assertThat(as(a, () -> subject.hide(b, theirs.id(), NOW, "sub-xestor"))).isZero();
    assertThat(hiddenOf(b)).isZero();
  }

  /** Gardar no concello alleo: a RLS ({@code with check}) non deixa. */
  @Test
  void itCannotWriteIntoAnotherMunicipality() {
    UUID a = id(createMunicipality(uniqueSlug("review-wa")));
    UUID b = id(createMunicipality(uniqueSlug("review-wb")));
    AttendanceSeeds.Seeded booking = AttendanceSeeds.booking(jdbc, b, START, "NONE", "RW", 0);
    Review review =
        new Review(
            UUID.randomUUID(),
            booking.booking(),
            booking.facility(),
            UUID.randomUUID(),
            5,
            null,
            NOW,
            null);

    assertThatThrownBy(() -> as(a, () -> subject.insert(b, review)))
        .isInstanceOf(DataAccessException.class);
  }

  /** Unha por reserva: a segunda non entra e a transacción segue viva. */
  @Test
  void aSecondReviewOfTheSameBookingDoesNotEnterAndDoesNotAbort() {
    UUID m = id(createMunicipality(uniqueSlug("review-once")));
    Review first = seedReview(m, 4, null);
    Review again =
        new Review(
            UUID.randomUUID(),
            first.bookingId(),
            first.facilityId(),
            first.useId(),
            1,
            "Outra",
            NOW,
            null);

    List<Object> result =
        as(m, () -> List.of(subject.insert(m, again), subject.find(m, first.id()).orElseThrow()));

    assertThat(result.getFirst()).isEqualTo(false);
    assertThat(((Review) result.get(1)).stars()).isEqualTo(4);
  }

  /** Nas catro ordes e coa media, a do propio concello; ocultar só toca un comentario. */
  @Test
  void ordersTalliesAndHidesItsOwn() {
    UUID m = id(createMunicipality(uniqueSlug("review-own")));
    Review low = seedReview(m, 1, "Fría");
    Review high = seedReview(m, 5, null);
    UUID facility = low.facilityId();
    jdbc.sql("update booking_review set facility_id = :f where id = :id")
        .param("f", facility)
        .param("id", high.id())
        .update();
    jdbc.sql("update booking_review set created_at = created_at - interval '1 day' where id = :id")
        .param("id", high.id())
        .update();

    assertThat(as(m, () -> ids(subject.ofFacility(m, facility, ReviewOrder.NEWEST, 10, 0))))
        .containsExactly(low.id(), high.id());
    assertThat(as(m, () -> ids(subject.ofFacility(m, facility, ReviewOrder.OLDEST, 10, 0))))
        .containsExactly(high.id(), low.id());
    assertThat(as(m, () -> ids(subject.ofFacility(m, facility, ReviewOrder.HIGHEST, 10, 0))))
        .containsExactly(high.id(), low.id());
    assertThat(as(m, () -> ids(subject.ofFacility(m, facility, ReviewOrder.LOWEST, 10, 0))))
        .containsExactly(low.id(), high.id());
    assertThat(as(m, () -> subject.tally(m, facility))).containsExactly(2L, 6L);
    assertThat(as(m, () -> subject.hide(m, high.id(), NOW, "sub-xestor")))
        .as("sen comentario non hai que ocultar")
        .isZero();
    assertThat(as(m, () -> subject.hide(m, low.id(), NOW, "sub-xestor"))).isEqualTo(1);
    assertThat(as(m, () -> subject.find(m, low.id()).orElseThrow().hidden())).isTrue();
    assertThat(as(m, () -> subject.tally(m, facility)))
        .as("oculto, segue contando")
        .containsExactly(2L, 6L);
    assertThat(as(m, () -> subject.starsOf(m, List.of(low.bookingId(), high.bookingId()))))
        .containsEntry(low.bookingId(), 1)
        .containsEntry(high.bookingId(), 5);
  }

  static List<UUID> ids(List<Review> reviews) {
    return reviews.stream().map(Review::id).toList();
  }
}
