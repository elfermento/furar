// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingNotice;
import gal.furar.booking.BookingQueries;
import gal.furar.booking.BookingSummary;
import gal.furar.booking.application.port.in.MyBooking;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.application.port.out.CalendarFile;
import gal.furar.booking.application.port.out.QrEncoder;
import gal.furar.booking.application.port.out.ResidentBookings;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.Holder;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa a API pública síncrona ({@link BookingQueries}) para outros módulos. Sempre no
 * concello actual (o repositorio falla sen el); sen permisos propios porque non é un caso de uso.
 */
@Service
class BookingQueryService implements BookingQueries {

  /** Píxeles por módulo do QR do correo: ~200 px, nítido a 150 px nunha pantalla densa. */
  private static final int QR_MODULE_PIXELS = 8;

  private static final int QR_QUIET_MODULES = 2;

  private final BookingRepository repository;
  private final BookingViews views;
  private final CalendarFile calendar;
  private final QrEncoder qr;
  private final ResidentBookings residents;
  private final Clock clock;

  BookingQueryService(
      BookingRepository repository,
      BookingViews views,
      CalendarFile calendar,
      QrEncoder qr,
      ResidentBookings residents,
      Clock clock) {
    this.repository = repository;
    this.views = views;
    this.calendar = calendar;
    this.qr = qr;
    this.residents = residents;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<BookingSummary> find(UUID id) {
    return repository.findById(new BookingId(id)).map(BookingQueryService::summary);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<BookingNotice> notice(UUID id) {
    // Unha anonimizada xa non ten a quen avisar; unha do persoal sen correo, tampouco.
    return repository
        .findById(new BookingId(id))
        .filter(b -> !b.isAnonymised() && b.requireHolder().email() != null)
        .map(this::notice);
  }

  @Override
  public boolean hasResidentBookings(String subject) {
    return residents.existAnywhere(subject);
  }

  private BookingNotice notice(Booking booking) {
    MyBooking view = views.withoutCover(booking, clock.instant());
    Holder holder = booking.requireHolder();
    return new BookingNotice(
        booking.id().value(),
        booking.code().value(),
        Objects.requireNonNull(booking.version(), "version"),
        booking.slot().facilityId(),
        view.facilityName(),
        view.facilityAddress(),
        view.useName(),
        view.partName(),
        booking.slot().start(),
        booking.slot().end(),
        BookingSummary.Status.valueOf(booking.status().name()),
        BookingNotice.Payment.valueOf(booking.paymentStatus().name()),
        BookingNotice.PriceKind.valueOf(booking.price().kind().name()),
        booking.price().amountCents(),
        booking.cancelUntil(),
        holder.name(),
        Objects.requireNonNull(holder.email(), "email"),
        holder.language().code(),
        holder.hasAccount(),
        qr.png(booking.code().value(), QR_MODULE_PIXELS, QR_QUIET_MODULES),
        calendar.of(
            booking, view.facilityName(), view.facilityAddress(), view.useName(), view.partName()));
  }

  static BookingSummary summary(Booking booking) {
    return new BookingSummary(
        booking.id().value(),
        booking.code().value(),
        booking.slot().facilityId(),
        booking.slot().useId(),
        booking.slot().partId(),
        booking.slot().start(),
        booking.slot().end(),
        BookingSummary.Status.valueOf(booking.status().name()));
  }
}
