// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.BookingCancelled;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.PaymentStatus;
import gal.furar.shared.domain.DomainException;
import java.util.Set;

/**
 * O que fai o persoal cunha reserva (ADR-0025, punto 12; P12, P13): marcar o cobro (e desfacelo) e
 * cancelala en nome do concello. Cada un deixa fila en {@code booking_event} e no rexistro de
 * actividade (ADR-0020), e leva a versión do {@code If-Match}.
 */
public interface ManageBookingUseCase {

  /**
   * Pendente → pagada, a devolver → devolta, e desfacer. Permiso {@code booking.payment}.
   *
   * @throws DomainException {@code booking.not-found}, {@code common.stale-version} ou {@code
   *     booking.payment-transition-invalid}
   */
  ManagedBooking changePayment(BookingId id, long expectedVersion, PaymentStatus target);

  /**
   * O concello cancela a reserva, sen prazo, e di por onde avisa (P12); se estaba pagada, queda «a
   * devolver». Publica {@link BookingCancelled} co concello e as canles. Permiso {@code
   * booking.cancel}.
   *
   * @throws DomainException {@code booking.not-found}, {@code common.stale-version}, {@code
   *     booking.not-cancellable} ou {@code common.invalid-parameter} (chamar sen teléfono)
   */
  ManagedBooking cancel(BookingId id, long expectedVersion, Set<BookingCancelled.Channel> channels);
}
