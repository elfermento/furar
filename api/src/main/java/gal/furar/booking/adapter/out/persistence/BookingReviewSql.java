// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.BookingStatistics.ReviewBucket;
import gal.furar.booking.StatsUnit;
import gal.furar.booking.application.port.out.BookingReviews.ReviewFilter;
import gal.furar.booking.application.port.out.StatsWindow;
import gal.furar.booking.domain.Review;
import gal.furar.booking.domain.ReviewOrder;
import gal.furar.shared.persistence.NativeSql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As valoracións en SQL, como o resto das reservas (ADR-0037): {@code on conflict do nothing} para
 * «unha por reserva» e os filtros opcionais como {@code (:all or …)}, así o nivel 0 é unha
 * conxunción co concello. Sempre dentro dunha transacción (RLS) e co concello como parámetro (A8b,
 * {@code BookingReviewSqlTenantIT}). Cada orde da lista pública vai no seu literal.
 */
@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class BookingReviewSql {

  /** Ningún valor real: o recheo das listas dun filtro que non filtra. */
  private static final UUID NONE = new UUID(0, 0);

  private static final String NEWEST =
      """
      select id, booking_id, facility_id, use_id, stars, comment, created_at, hidden_at
      from booking_review
      where facility_id = :facility
        and municipality_id = :tenant
      order by created_at desc, id desc
      limit :limit offset :offset
      """;

  private static final String OLDEST =
      """
      select id, booking_id, facility_id, use_id, stars, comment, created_at, hidden_at
      from booking_review
      where facility_id = :facility
        and municipality_id = :tenant
      order by created_at, id
      limit :limit offset :offset
      """;

  private static final String HIGHEST =
      """
      select id, booking_id, facility_id, use_id, stars, comment, created_at, hidden_at
      from booking_review
      where facility_id = :facility
        and municipality_id = :tenant
      order by stars desc, created_at desc, id desc
      limit :limit offset :offset
      """;

  private static final String LOWEST =
      """
      select id, booking_id, facility_id, use_id, stars, comment, created_at, hidden_at
      from booking_review
      where facility_id = :facility
        and municipality_id = :tenant
      order by stars, created_at desc, id desc
      limit :limit offset :offset
      """;

  private final JdbcClient jdbc;

  BookingReviewSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  boolean insert(UUID municipality, Review review) {
    return jdbc.sql(
            """
            insert into booking_review
              (id, municipality_id, booking_id, facility_id, use_id, stars, comment, created_at)
            values (:id, :tenant, :booking, :facility, :use, :stars, :comment, :at)
            on conflict do nothing
            returning id
            """)
        .param("id", review.id())
        .param("tenant", municipality)
        .param("booking", review.bookingId())
        .param("facility", review.facilityId())
        .param("use", review.useId())
        .param("stars", review.stars())
        .param("comment", review.comment())
        .param("at", BookingSql.utc(review.createdAt()))
        .query(UUID.class)
        .optional()
        .isPresent();
  }

  Map<UUID, Integer> starsOf(UUID municipality, Collection<UUID> bookingIds) {
    Map<UUID, Integer> stars = new LinkedHashMap<>();
    jdbc.sql(
            """
            select booking_id, stars
            from booking_review
            where booking_id in (:bookings)
              and municipality_id = :tenant
            """)
        .param("bookings", List.copyOf(bookingIds))
        .param("tenant", municipality)
        .query(
            (ResultSet rs) -> {
              stars.put(rs.getObject("booking_id", UUID.class), rs.getInt("stars"));
            });
    return stars;
  }

  Optional<Review> find(UUID municipality, UUID id) {
    return jdbc.sql(
            """
            select id, booking_id, facility_id, use_id, stars, comment, created_at, hidden_at
            from booking_review
            where id = :id
              and municipality_id = :tenant
            """)
        .param("id", id)
        .param("tenant", municipality)
        .query(BookingReviewSql::row)
        .optional();
  }

  int hide(UUID municipality, UUID id, @Nullable Instant hiddenAt, @Nullable String hiddenBy) {
    return jdbc.sql(
            """
            update booking_review
               set hidden_at = :hiddenAt, hidden_by = :hiddenBy
            where id = :id and comment is not null
              and municipality_id = :tenant
            """)
        .param("hiddenAt", hiddenAt == null ? null : BookingSql.utc(hiddenAt))
        .param("hiddenBy", hiddenBy)
        .param("id", id)
        .param("tenant", municipality)
        .update();
  }

  /** Cantas e a suma das notas; usa {@code booking_review_facility_idx}. */
  long[] tally(UUID municipality, UUID facilityId) {
    return jdbc.sql(
            """
            select count(*) as reviews, coalesce(sum(stars), 0) as stars_sum
            from booking_review
            where facility_id = :facility
              and municipality_id = :tenant
            """)
        .param("facility", facilityId)
        .param("tenant", municipality)
        .query((rs, n) -> new long[] {rs.getLong("reviews"), rs.getLong("stars_sum")})
        .single();
  }

  List<Review> ofFacility(
      UUID municipality, UUID facilityId, ReviewOrder order, int limit, long offset) {
    JdbcClient.StatementSpec spec =
        switch (order) {
          case NEWEST -> jdbc.sql(NEWEST);
          case OLDEST -> jdbc.sql(OLDEST);
          case HIGHEST -> jdbc.sql(HIGHEST);
          case LOWEST -> jdbc.sql(LOWEST);
        };
    return spec.param("facility", facilityId)
        .param("tenant", municipality)
        .param("limit", limit)
        .param("offset", offset)
        .query(BookingReviewSql::row)
        .list();
  }

  List<Review> search(UUID municipality, ReviewFilter filter, int limit, long offset) {
    return filtered(
            jdbc.sql(
                """
                select id, booking_id, facility_id, use_id, stars, comment, created_at, hidden_at
                from booking_review
                where created_at >= :since and created_at < :until
                  and (:allFacilities or facility_id = :facility)
                  and (:allUses or use_id in (:uses))
                  and (:allStars or stars in (:stars))
                  and municipality_id = :tenant
                order by created_at desc, id desc
                limit :limit offset :offset
                """),
            municipality,
            filter)
        .param("limit", limit)
        .param("offset", offset)
        .query(BookingReviewSql::row)
        .list();
  }

  /** Por nota, sen o filtro de nota: cantas e cantas con comentario. */
  List<long[]> countsByStars(UUID municipality, ReviewFilter filter) {
    return filtered(
            jdbc.sql(
                """
                select stars, count(*) as reviews, count(comment) as commented
                from booking_review
                where created_at >= :since and created_at < :until
                  and (:allFacilities or facility_id = :facility)
                  and (:allUses or use_id in (:uses))
                  and municipality_id = :tenant
                group by stars
                order by stars
                """),
            municipality,
            new ReviewFilter(filter.window(), Set.of()))
        .query(
            (rs, n) ->
                new long[] {rs.getInt("stars"), rs.getLong("reviews"), rs.getLong("commented")})
        .list();
  }

  List<ReviewBucket> perBucket(UUID municipality, StatsWindow window, StatsUnit unit) {
    return filtered(
            jdbc.sql(
                """
                select date_trunc(:unit, created_at at time zone 'Europe/Madrid')::date
                         as bucket_start,
                       count(*) as reviews,
                       sum(stars) as stars_sum
                from booking_review
                where created_at >= :since and created_at < :until
                  and (:allFacilities or facility_id = :facility)
                  and (:allUses or use_id in (:uses))
                  and municipality_id = :tenant
                group by 1
                order by 1
                """),
            municipality,
            new ReviewFilter(window, Set.of()))
        .param("unit", unit.name().toLowerCase(Locale.ROOT))
        .query(
            (rs, n) ->
                new ReviewBucket(
                    rs.getObject("bucket_start", LocalDate.class),
                    rs.getLong("reviews"),
                    rs.getLong("stars_sum")))
        .list();
  }

  private static JdbcClient.StatementSpec filtered(
      JdbcClient.StatementSpec spec, UUID municipality, ReviewFilter filter) {
    StatsWindow window = filter.window();
    return spec.param("since", BookingSql.utc(window.since()))
        .param("until", BookingSql.utc(window.until()))
        .param("allFacilities", window.facilityId() == null)
        .param("facility", window.facilityId() == null ? NONE : window.facilityId())
        .param("allUses", window.useIds().isEmpty())
        .param("uses", window.useIds().isEmpty() ? List.of(NONE) : List.copyOf(window.useIds()))
        .param("allStars", filter.stars().isEmpty())
        .param("stars", filter.stars().isEmpty() ? List.of(0) : List.copyOf(filter.stars()))
        .param("tenant", municipality);
  }

  static Review row(ResultSet rs, int n) throws SQLException {
    OffsetDateTime hidden = rs.getObject("hidden_at", OffsetDateTime.class);
    return new Review(
        rs.getObject("id", UUID.class),
        rs.getObject("booking_id", UUID.class),
        rs.getObject("facility_id", UUID.class),
        rs.getObject("use_id", UUID.class),
        rs.getInt("stars"),
        rs.getString("comment"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        hidden == null ? null : hidden.toInstant());
  }
}
