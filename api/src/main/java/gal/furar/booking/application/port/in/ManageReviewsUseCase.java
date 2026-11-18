// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.Review;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * As valoracións do concello para a xestión (ADR-0037, punto 6): a táboa dos indicadores, co
 * comentario aínda que estea oculto, e ocultar ou volver mostrar un comentario. Nunca quen valorou.
 */
public interface ManageReviewsUseCase {

  /**
   * As valoracións feitas entre {@code from} e {@code to} (Europe/Madrid, incluídos), das máis
   * novas ás máis vellas.
   *
   * @throws gal.furar.shared.domain.DomainException {@code booking.review-invalid} cun período ou
   *     unha nota imposibles
   */
  Page list(Query query, int page, int size);

  /**
   * @throws gal.furar.shared.domain.DomainException {@code booking.review-not-found}
   */
  ManagedReview get(UUID id);

  /**
   * Oculta ({@code hidden}) ou volve mostrar o comentario. Repetir non cambia nada.
   *
   * @throws gal.furar.shared.domain.DomainException {@code booking.review-not-found} se non existe
   *     ou non ten comentario
   */
  ManagedReview hide(UUID id, boolean hidden);

  /**
   * @param facilityId só esta instalación; nula, todas
   * @param useIds só estes usos; baleiro, todos
   * @param stars só estas notas; baleiro, todas
   */
  record Query(
      LocalDate from,
      LocalDate to,
      @Nullable UUID facilityId,
      Set<UUID> useIds,
      Set<Integer> stars) {

    public Query {
      useIds = Set.copyOf(useIds);
      stars = Set.copyOf(stars);
    }
  }

  /** Unha valoración cos nomes de agora da instalación e do uso. */
  record ManagedReview(Review review, String facilityName, String useName) {}

  /**
   * @param total as valoracións do filtro
   * @param withComment desas, as que teñen comentario
   * @param byStars cantas ten cada nota, sen o filtro de nota
   */
  record Page(
      List<ManagedReview> items,
      int page,
      int size,
      long total,
      long withComment,
      Map<Integer, Long> byStars) {

    public Page {
      items = List.copyOf(items);
      byStars = Map.copyOf(byStars);
    }
  }
}
