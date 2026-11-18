// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingPermission;
import gal.furar.booking.application.port.in.FacilityReviewsUseCase;
import gal.furar.booking.application.port.in.ManageReviewsUseCase;
import gal.furar.booking.application.port.in.ReviewMyBookingUseCase;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.application.port.out.BookingReviews;
import gal.furar.booking.application.port.out.BookingReviews.Counts;
import gal.furar.booking.application.port.out.BookingReviews.ReviewFilter;
import gal.furar.booking.application.port.out.BookingReviews.Tally;
import gal.furar.booking.application.port.out.StatsWindow;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.BookingRules;
import gal.furar.booking.domain.Review;
import gal.furar.booking.domain.ReviewOrder;
import gal.furar.booking.domain.ReviewRule;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.UseSummary;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As valoracións (ADR-0037): valorar unha reserva propia, a media e a lista pública dunha
 * instalación e a táboa da xestión con «Ocultar comentario». Valorar non toma o bloqueo da
 * instalación: non toca a ocupación, e «unha por reserva» garánteo a base.
 */
@Service
public class ReviewService
    implements ReviewMyBookingUseCase, FacilityReviewsUseCase, ManageReviewsUseCase {

  /** Rastro de ocultar un comentario. */
  static final String HIDE = "booking.review-hide";

  /** Rastro de volver mostrar un comentario. */
  static final String SHOW = "booking.review-show";

  /** Filas como moito por páxina. */
  static final int MAX_PAGE = 50;

  /** Días como moito do período da táboa: os cinco anos da evolución dos indicadores. */
  static final long MAX_DAYS = 5L * 366;

  private final BookingRepository bookings;
  private final BookingReviews reviews;
  private final FacilityQueries facilities;
  private final CurrentActor currentActor;
  private final AuditLog audit;
  private final Clock clock;

  public ReviewService(
      BookingRepository bookings,
      BookingReviews reviews,
      FacilityQueries facilities,
      CurrentActor currentActor,
      AuditLog audit,
      Clock clock) {
    this.bookings = bookings;
    this.reviews = reviews;
    this.facilities = facilities;
    this.currentActor = currentActor;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional
  @PersonUseCase
  public Review review(BookingId id, int stars, @Nullable String comment) {
    String subject =
        currentActor
            .current()
            .orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED))
            .subject();
    Booking booking =
        bookings
            .findOfHolder(id, subject)
            .orElseThrow(() -> new DomainException(BookingError.NOT_FOUND));
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    Review review = Review.of(Ids.newV7(), booking, stars, comment, now);
    if (!ReviewRule.reviewable(booking, now, entryControlSince(booking.slot().facilityId()))) {
      throw new DomainException(BookingError.REVIEW_NOT_ALLOWED);
    }
    if (!reviews.insert(review)) {
      throw new DomainException(BookingError.REVIEW_EXISTS);
    }
    return review;
  }

  /** Desde cando a instalación ten control de entrada; nulo se non o ten. */
  @Nullable Instant entryControlSince(UUID facilityId) {
    return facilities.entryControl().stream()
        .filter(e -> e.facilityId().equals(facilityId))
        .map(FacilityQueries.EntryControl::since)
        .findFirst()
        .orElse(null);
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public Summary summary(UUID facilityId) {
    requireVisible(facilityId);
    Tally tally = reviews.tally(facilityId);
    return new Summary(
        tally.reviews(), tally.reviews() == 0 ? null : (double) tally.starsSum() / tally.reviews());
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public PageResult<PublicReview> list(UUID facilityId, ReviewOrder order, int page, int size) {
    requireVisible(facilityId);
    int limit = Math.clamp(size, 1, MAX_PAGE);
    long offset = (long) Math.max(page, 0) * limit;
    List<PublicReview> items =
        reviews.ofFacility(facilityId, order, limit, offset).stream()
            .map(r -> new PublicReview(r.id(), r.stars(), r.publicComment(), r.createdAt()))
            .toList();
    return new PageResult<>(items, Math.max(page, 0), limit, reviews.tally(facilityId).reviews());
  }

  private void requireVisible(UUID facilityId) {
    if (facilities.findPublic(facilityId).isEmpty()) {
      throw new DomainException(CommonError.NOT_FOUND);
    }
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(BookingPermission.Code.REVIEWS)
  public Page list(Query query, int page, int size) {
    if (query.to().isBefore(query.from())
        || query.from().plusDays(MAX_DAYS).isBefore(query.to())
        || query.stars().stream().anyMatch(s -> s < Review.MIN_STARS || s > Review.MAX_STARS)) {
      throw new DomainException(BookingError.REVIEW_INVALID);
    }
    StatsWindow window =
        new StatsWindow(
            query.from().atStartOfDay(BookingRules.ZONE).toInstant(),
            query.to().plusDays(1).atStartOfDay(BookingRules.ZONE).toInstant(),
            query.facilityId(),
            query.useIds());
    ReviewFilter filter = new ReviewFilter(window, query.stars());
    int limit = Math.clamp(size, 1, MAX_PAGE);
    int current = Math.max(page, 0);
    List<ManagedReview> items = named(reviews.search(filter, limit, (long) current * limit));
    Counts counts = reviews.counts(filter);
    return new Page(items, current, limit, counts.total(), counts.withComment(), counts.byStars());
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(BookingPermission.Code.REVIEWS)
  public ManagedReview get(UUID id) {
    return named(List.of(load(id))).getFirst();
  }

  @Override
  @Transactional
  @RequiresPermission(BookingPermission.Code.REVIEWS)
  public ManagedReview hide(UUID id, boolean hidden) {
    TenantRef tenant = TenantContext.require();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("reviewId", id.toString());
    String action = hidden ? HIDE : SHOW;
    try {
      Review review = load(id);
      details.put("facilityId", review.facilityId().toString());
      if (review.comment() == null) {
        throw new DomainException(BookingError.REVIEW_NOT_FOUND);
      }
      if (review.hidden() == hidden) {
        return named(List.of(review)).getFirst();
      }
      String subject =
          currentActor
              .current()
              .orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED))
              .subject();
      Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
      reviews.hide(id, hidden ? now : null, hidden ? subject : null);
      // Sen o texto do comentario: só que valoración e de que instalación (ADR-0037, punto 6).
      audit.record(
          new AuditEvent(action, AuditEvent.Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
      return named(List.of(load(id))).getFirst();
    } catch (RuntimeException e) {
      audit.recordFailure(e, action, tenant.id(), tenant.slug(), details);
      throw e;
    }
  }

  private Review load(UUID id) {
    return reviews.find(id).orElseThrow(() -> new DomainException(BookingError.REVIEW_NOT_FOUND));
  }

  /** Cos nomes de agora da instalación e do uso, por lote. */
  private List<ManagedReview> named(List<Review> list) {
    if (list.isEmpty()) {
      return List.of();
    }
    Map<UUID, String> facilityNames =
        facilities.summary(list.stream().map(Review::facilityId).distinct().toList()).stream()
            .collect(Collectors.toMap(FacilitySummary::id, FacilitySummary::name));
    Map<UUID, String> useNames =
        facilities.usesById(list.stream().map(Review::useId).collect(Collectors.toSet())).stream()
            .collect(Collectors.toMap(UseSummary::id, UseSummary::name));
    return list.stream()
        .map(
            r ->
                new ManagedReview(
                    r,
                    facilityNames.getOrDefault(r.facilityId(), ""),
                    useNames.getOrDefault(r.useId(), "")))
        .toList();
  }
}
