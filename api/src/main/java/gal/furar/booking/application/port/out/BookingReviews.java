// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import gal.furar.booking.BookingStatistics.ReviewBucket;
import gal.furar.booking.StatsUnit;
import gal.furar.booking.domain.Review;
import gal.furar.booking.domain.ReviewOrder;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** As valoracións do concello actual (ADR-0037). Sempre dentro dunha transacción (ADR-0006). */
public interface BookingReviews {

  /**
   * Garda a valoración, sen abortar a transacción se a reserva xa ten a súa.
   *
   * @return falso se a reserva xa estaba valorada
   */
  boolean insert(Review review);

  /** A nota de cada reserva valorada desas. */
  Map<UUID, Integer> starsOf(Collection<UUID> bookingIds);

  Optional<Review> find(UUID id);

  /**
   * Oculta ({@code hiddenAt} con valor) ou volve mostrar o comentario.
   *
   * @return falso se non existe ou non ten comentario
   */
  boolean hide(UUID id, @Nullable Instant hiddenAt, @Nullable String hiddenBy);

  /** Cantas valoracións ten a instalación e a suma das súas notas (tamén as ocultas). */
  Tally tally(UUID facilityId);

  /** As valoracións dunha instalación na orde pedida, por páxinas. */
  List<Review> ofFacility(UUID facilityId, ReviewOrder order, int limit, long offset);

  /** As valoracións do filtro do xestor, das máis novas ás máis vellas. */
  List<Review> search(ReviewFilter filter, int limit, long offset);

  /** Os recontos do filtro (sen o filtro de nota, para que se vexa cantas ten cada nota). */
  Counts counts(ReviewFilter filter);

  /** Por período: cantas valoracións e a suma das notas, polo día da valoración. */
  List<ReviewBucket> perBucket(StatsWindow window, StatsUnit unit);

  /**
   * O filtro da táboa do xestor: as valoracións feitas en {@code [since, until)}.
   *
   * @param window período e filtros de instalación e uso, coma os indicadores
   * @param stars só estas notas; baleiro, todas
   */
  record ReviewFilter(StatsWindow window, Set<Integer> stars) {

    public ReviewFilter {
      stars = Set.copyOf(stars);
    }
  }

  /**
   * @param reviews cantas
   * @param starsSum a suma das notas
   */
  record Tally(long reviews, long starsSum) {}

  /**
   * @param total as do filtro coa nota
   * @param withComment desas, as que teñen comentario
   * @param byStars cantas ten cada nota (1 a 5) sen o filtro de nota
   */
  record Counts(long total, long withComment, Map<Integer, Long> byStars) {

    public Counts {
      byStars = Map.copyOf(byStars);
    }
  }
}
