// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.BookingId;
import gal.furar.shared.domain.DomainException;

/**
 * Cambiar e cancelar unha reserva propia (plan do dominio 2, §2.2; ADR-0025, puntos 5, 8 e 12):
 * cada un nunha transacción baixo o bloqueo da instalación. A reserva doutra persoa é {@code
 * booking.not-found}.
 */
public interface ChangeMyBookingUseCase {

  /**
   * Cambia a reserva ao oco novo: mesma reserva (id e código), prezo, normas e prazo novos. As
   * normas avalíanse coma se a reserva vella non existise; se algo falla, queda exactamente como
   * estaba.
   *
   * @throws SlotTakenException se o oco novo xa non está libre, coas alternativas
   * @throws DomainException {@code booking.not-found}, {@code common.stale-version}, {@code
   *     booking.not-cancellable}, {@code booking.cancel-deadline-passed}, {@code
   *     booking.change-other-facility}, {@code booking.change-price-paid} e os de reservar
   */
  MyBooking change(ChangeBookingCommand command);

  /**
   * A persoa cancela a súa reserva dentro do prazo (P1); se estaba pagada, queda «a devolver».
   *
   * @throws DomainException {@code booking.not-found}, {@code common.stale-version}, {@code
   *     booking.not-cancellable} ou {@code booking.cancel-deadline-passed}
   */
  MyBooking cancel(BookingId id, long expectedVersion);
}
