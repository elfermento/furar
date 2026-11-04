// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.BookingId;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.PageResult;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * As reservas do concello para o persoal (plan do dominio 2, §2.2; maquetas 26 e 27): lista con
 * filtros, portada, detalle e calendario. Permiso {@code booking.view}.
 */
public interface ManageBookingsUseCase {

  /**
   * @throws DomainException {@code common.invalid-parameter} se {@code from} é posterior a {@code
   *     to}
   */
  PageResult<ManagedBooking> search(BookingSearch search);

  /** As últimas reservadas e as pestanas de «Cobros», cada unha coas {@code size} primeiras. */
  BookingsOverview overview(int size);

  /**
   * @throws DomainException {@code booking.not-found} (tamén se é doutro concello)
   */
  ManagedBooking get(BookingId id);

  /** O período da vista que contén {@code date}, dunha instalación ou de todas ({@code null}). */
  BookingCalendar calendar(CalendarView view, LocalDate date, @Nullable UUID facilityId);
}
