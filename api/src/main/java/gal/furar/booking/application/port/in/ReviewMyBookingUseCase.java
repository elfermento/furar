// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.Review;
import org.jspecify.annotations.Nullable;

/**
 * Valorar unha reserva propia (ADR-0037, puntos 2 e 3): quen a fixo, unha vez, despois de ir. Sen
 * cambios nin borrado.
 */
public interface ReviewMyBookingUseCase {

  /**
   * @param comment opcional; baleiro conta como sen comentario
   * @throws gal.furar.shared.domain.DomainException {@code booking.not-found} (non existe ou é
   *     doutra persoa), {@code booking.review-not-allowed}, {@code booking.review-exists} ou {@code
   *     booking.review-invalid}
   */
  Review review(BookingId id, int stars, @Nullable String comment);
}
