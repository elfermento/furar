// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.shared.domain.DomainException;

/** Reservar (plan do dominio 2, §2.2; ADR-0025, puntos 5, 9, 10 e 11). */
public interface ReserveUseCase {

  /**
   * Reserva para a persoa da sesión, baixo o bloqueo da instalación. Se a persoa xa reservou coa
   * mesma clave e a mesma petición, devolve esa reserva (tal como está agora) sen crear outra.
   *
   * @throws SlotTakenException se o oco xa non está libre, coas alternativas
   * @throws DomainException {@code booking.facility-not-bookable}, {@code
   *     booking.use-not-available}, {@code booking.slot-invalid}, {@code booking.part-taken},
   *     {@code booking.seat-taken-by-you}, {@code booking.too-soon}, {@code booking.too-far},
   *     {@code booking.too-long}, {@code booking.cap-reached}, {@code
   *     booking.idempotency-key-reused} (a mesma clave cunha petición distinta) ou {@code
   *     common.not-found}
   */
  MyBooking reserve(ReserveCommand command);
}
