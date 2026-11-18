// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

/**
 * Se unha persoa reservou como veciña nalgún concello (ADR-0027, revisión do 2026-10-30). A única
 * lectura das reservas fóra do concello actual: só di si ou non.
 */
public interface ResidentBookings {

  /**
   * @param subject {@code sub} da persoa
   * @return se ten algunha reserva, en calquera concello e en calquera estado
   */
  boolean existAnywhere(String subject);
}
