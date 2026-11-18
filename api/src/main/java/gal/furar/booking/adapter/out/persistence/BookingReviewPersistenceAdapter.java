// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.BookingStatistics.ReviewBucket;
import gal.furar.booking.StatsUnit;
import gal.furar.booking.application.port.out.BookingReviews;
import gal.furar.booking.application.port.out.StatsWindow;
import gal.furar.booking.domain.Review;
import gal.furar.booking.domain.ReviewOrder;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Valoracións no concello actual: falla sen concello ou sen transacción (ADR-0006). */
@Component
class BookingReviewPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements BookingReviews {

  private final BookingReviewSql sql;

  BookingReviewPersistenceAdapter(BookingReviewSql sql) {
    this.sql = sql;
  }

  @Override
  public boolean insert(Review review) {
    return sql.insert(tenant().id(), review);
  }

  @Override
  public Map<UUID, Integer> starsOf(Collection<UUID> bookingIds) {
    UUID municipality = tenant().id();
    return bookingIds.isEmpty() ? Map.of() : sql.starsOf(municipality, bookingIds);
  }

  @Override
  public Optional<Review> find(UUID id) {
    return sql.find(tenant().id(), id);
  }

  @Override
  public boolean hide(UUID id, @Nullable Instant hiddenAt, @Nullable String hiddenBy) {
    return sql.hide(tenant().id(), id, hiddenAt, hiddenBy) > 0;
  }

  @Override
  public Tally tally(UUID facilityId) {
    long[] row = sql.tally(tenant().id(), facilityId);
    return new Tally(row[0], row[1]);
  }

  @Override
  public List<Review> ofFacility(UUID facilityId, ReviewOrder order, int limit, long offset) {
    return sql.ofFacility(tenant().id(), facilityId, order, limit, offset);
  }

  @Override
  public List<Review> search(ReviewFilter filter, int limit, long offset) {
    return sql.search(tenant().id(), filter, limit, offset);
  }

  @Override
  public Counts counts(ReviewFilter filter) {
    Map<Integer, Long> byStars = new HashMap<>();
    long total = 0;
    long withComment = 0;
    boolean all = filter.stars().isEmpty();
    for (long[] row : sql.countsByStars(tenant().id(), filter)) {
      int stars = (int) row[0];
      byStars.put(stars, row[1]);
      if (all || filter.stars().contains(stars)) {
        total += row[1];
        withComment += row[2];
      }
    }
    return new Counts(total, withComment, byStars);
  }

  @Override
  public List<ReviewBucket> perBucket(StatsWindow window, StatsUnit unit) {
    return sql.perBucket(tenant().id(), window, unit);
  }
}
