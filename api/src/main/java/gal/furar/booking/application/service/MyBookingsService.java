// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.in.MyBooking;
import gal.furar.booking.application.port.in.MyBookingsUseCase;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.application.port.out.CalendarFile;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingId;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As reservas da persoa da sesión (plan do dominio 2, §2.2): sempre filtradas polo seu {@code sub}
 * no concello do URL. A doutra persoa responde igual ca unha que non existe.
 */
@Service
public class MyBookingsService implements MyBookingsUseCase {

  private final BookingRepository bookings;
  private final BookingViews views;
  private final CalendarFile calendar;
  private final CurrentActor currentActor;
  private final Clock clock;

  public MyBookingsService(
      BookingRepository bookings,
      BookingViews views,
      CalendarFile calendar,
      CurrentActor currentActor,
      Clock clock) {
    this.bookings = bookings;
    this.views = views;
    this.calendar = calendar;
    this.currentActor = currentActor;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public PageResult<MyBooking> list(boolean upcoming, int page, int size) {
    Instant now = clock.instant();
    PageResult<Booking> result = bookings.ofHolder(subject(), upcoming, now, page, size);
    return new PageResult<>(
        views.views(result.items(), now), result.page(), result.size(), result.totalItems());
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public MyBooking get(BookingId id) {
    return views.view(load(id), clock.instant());
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public String calendar(BookingId id) {
    MyBooking view = views.view(load(id), clock.instant());
    return calendar.of(
        view.booking(),
        view.facilityName(),
        view.facilityAddress(),
        view.useName(),
        view.partName());
  }

  private Booking load(BookingId id) {
    return bookings
        .findOfHolder(id, subject())
        .orElseThrow(() -> new DomainException(BookingError.NOT_FOUND));
  }

  private String subject() {
    return currentActor
        .current()
        .orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED))
        .subject();
  }
}
