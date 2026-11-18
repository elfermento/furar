// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.ReviewOrder;
import gal.furar.shared.domain.PageResult;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * As valoracións dunha instalación á vista de calquera, sen sesión (ADR-0037, punto 5): a nota
 * media, cantas e a lista, sen ningún dato de quen valorou. O comentario oculto polo xestor non
 * sae, pero a súa nota conta.
 */
public interface FacilityReviewsUseCase {

  /**
   * @throws gal.furar.shared.domain.DomainException {@code common.not-found} se a instalación non
   *     se ve
   */
  Summary summary(UUID facilityId);

  /**
   * @throws gal.furar.shared.domain.DomainException {@code common.not-found} se a instalación non
   *     se ve
   */
  PageResult<PublicReview> list(UUID facilityId, ReviewOrder order, int page, int size);

  /**
   * @param reviews cantas valoracións
   * @param average a nota media; nula sen valoracións
   */
  record Summary(long reviews, @Nullable Double average) {}

  /**
   * @param comment o comentario, se o hai e non está oculto
   */
  record PublicReview(UUID id, int stars, @Nullable String comment, Instant createdAt) {}
}
