// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.BookingId;
import gal.furar.shared.domain.PageResult;

/**
 * As reservas da persoa da sesión no concello (plan do dominio 2, §2.2): só as súas, polo {@code
 * sub}; a doutra persoa é {@code booking.not-found}.
 */
public interface MyBookingsUseCase {

  /** As próximas (da máis próxima) ou o historial (da máis recente). */
  PageResult<MyBooking> list(boolean upcoming, int page, int size);

  MyBooking get(BookingId id);

  /** O {@code .ics} da reserva (ADR-0025, punto 14). */
  String calendar(BookingId id);
}
