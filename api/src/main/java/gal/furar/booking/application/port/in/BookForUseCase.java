// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.shared.domain.DomainException;

/**
 * Reservar no nome doutra persoa e cambiar unha reserva desde a xestión (especificación, bloque 5,
 * decidido o 2026-11-04; bloque 7: «Reservar no nome de alguén», xestión e Recepción). Permiso
 * {@code booking.book-for}.
 */
public interface BookForUseCase {

  /**
   * Reserva no nome dunha persoa sen conta, baixo o bloqueo da instalación e coas mesmas
   * comprobacións ca a reserva dunha persoa (horario, ocupación e normas do uso); o límite por
   * persoa e «unha persoa, unha praza» non, porque sen conta non se sabe quen é. Se xa pagou, queda
   * pagada. Coa mesma clave e a mesma petición, devolve a reserva xa feita.
   *
   * @throws SlotTakenException se o oco xa non está libre, coas alternativas
   * @throws DomainException {@code booking.facility-not-bookable}, {@code
   *     booking.use-not-available}, {@code booking.slot-invalid}, {@code booking.part-taken},
   *     {@code booking.too-soon}, {@code booking.too-far}, {@code booking.too-long}, {@code
   *     booking.idempotency-key-reused}, {@code validation.failed} ou {@code common.not-found}
   */
  ManagedBooking reserveFor(ReserveForCommand command);

  /**
   * Cambia a reserva a outro oco da mesma instalación (sen o prazo da persoa) e, se se dá, para
   * quen é, o pago e os campos propios. Se se marca, avísase á persoa por correo.
   *
   * @throws SlotTakenException se o oco novo xa non está libre, coas alternativas
   * @throws DomainException {@code booking.not-found}, {@code common.stale-version}, {@code
   *     booking.not-cancellable}, {@code booking.change-other-facility}, {@code
   *     booking.change-price-paid}, {@code booking.holder-not-editable}, {@code
   *     booking.payment-transition-invalid}, as normas do oco ou {@code validation.failed}
   */
  ManagedBooking change(ChangeBookingByStaffCommand command);
}
