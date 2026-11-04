// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingCancelled;
import gal.furar.booking.BookingChanged;
import gal.furar.booking.application.port.in.ChangeBookingCommand;
import gal.furar.booking.application.port.in.ChangeMyBookingUseCase;
import gal.furar.booking.application.port.in.MyBooking;
import gal.furar.booking.application.port.in.SlotTakenException;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.domain.BookableUse;
import gal.furar.booking.domain.BookedSlot;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.BookingRules;
import gal.furar.booking.domain.RentalMode;
import gal.furar.booking.domain.SlotGrid;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.UseSummary;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambiar e cancelar unha reserva propia (ADR-0025, puntos 5, 8 e 12). Cada operación é unha
 * transacción que toma primeiro o bloqueo da instalación da reserva e volve ler a reserva baixo el.
 * Cambiar libera a ocupación vella, avalía o oco novo coma se a vella non existise (o límite por
 * persoa non a conta) e só entón escribe a nova sobre a mesma fila; calquera erro desfai todo e a
 * reserva queda como estaba (ocupación, prazas e versión incluídas).
 */
@Service
public class ChangeMyBookingService implements ChangeMyBookingUseCase {

  private static final String ACTIVE = "ACTIVE";

  private final FacilityQueries facilities;
  private final BookingRepository bookings;
  private final BookingViews views;
  private final SlotGrids grids;
  private final CurrentActor currentActor;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public ChangeMyBookingService(
      FacilityQueries facilities,
      BookingRepository bookings,
      BookingViews views,
      SlotGrids grids,
      CurrentActor currentActor,
      ApplicationEventPublisher events,
      Clock clock) {
    this.facilities = facilities;
    this.bookings = bookings;
    this.views = views;
    this.grids = grids;
    this.currentActor = currentActor;
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional
  @PersonUseCase
  public MyBooking change(ChangeBookingCommand command) {
    String subject = subject();
    Booking before = lockedOwn(command.id(), subject, command.expectedVersion());
    Instant now = clock.instant();
    // P1: fóra de prazo (ou xa cancelada) non se cambia por internet.
    before.checkChangeable(now);
    UUID facilityId = before.slot().facilityId();
    UseSummary summary =
        facilities
            .use(command.useId())
            .orElseThrow(() -> new DomainException(CommonError.NOT_FOUND));
    // P2: só dentro da mesma instalación, así basta un bloqueo.
    if (!summary.facilityId().equals(facilityId)) {
      throw new DomainException(BookingError.CHANGE_OTHER_FACILITY);
    }
    FacilitySummary facility =
        facilities
            .findPublic(facilityId)
            .orElseThrow(() -> new DomainException(BookingError.FACILITY_NOT_BOOKABLE));
    if (!ACTIVE.equals(facility.status()) || !facility.onlineBooking()) {
      throw new DomainException(BookingError.FACILITY_NOT_BOOKABLE);
    }
    if (summary.archived()) {
      throw new DomainException(BookingError.USE_NOT_AVAILABLE);
    }
    BookableUse use = Uses.bookable(summary);
    BookingRules rules = use.rules();
    Instant start = command.start();
    rules.checkSlot(start, command.slots(), now);
    Instant end = start.plus(Duration.ofMinutes(use.slotMinutes()).multipliedBy(command.slots()));

    // 1. Libera a ocupación vella: dende aquí, a reserva non conta para o oco nin para o límite.
    bookings.release(before);
    // 2. O oco novo, coma se a vella non existise.
    SlotGrid grid = grids.of(facilityId, start.atZone(SlotGrid.ZONE).toLocalDate(), now);
    if (use.rentalMode() == RentalMode.SEATS
        && bookings.holds(use.id(), subject, start, end, before.id())) {
      throw new DomainException(BookingError.SEAT_TAKEN_BY_YOU);
    }
    UUID part;
    try {
      part = grid.allocate(use, start, command.slots(), command.partId()).orElse(null);
    } catch (DomainException e) {
      if (e.errorCode() == BookingError.SLOT_TAKEN) {
        throw new SlotTakenException(grid.alternatives(use, start, command.slots()));
      }
      throw e;
    }
    rules
        .capWindow(start)
        .ifPresent(
            w ->
                rules.checkCap(
                    bookings.countConfirmed(use.id(), subject, w.from(), w.to(), before.id())));
    // 3. Prezo (P3), normas e prazo novos; só entón escribe sobre a mesma fila.
    Booking after =
        before.reschedule(
            new BookedSlot(
                facilityId, use.id(), part, use.rentalMode(), start, end, command.slots()),
            Uses.unitPrice(summary),
            rules,
            now);
    bookings.reschedule(before, after, use.capacity(), subject);
    Booking saved = bookings.findById(after.id()).orElseThrow();
    events.publishEvent(
        new BookingChanged(
            TenantContext.require().id(),
            after.id().value(),
            before.slot().start(),
            before.slot().end(),
            Objects.requireNonNull(saved.version(), "version")));
    return views.view(saved, now);
  }

  @Override
  @Transactional
  @PersonUseCase
  public MyBooking cancel(BookingId id, long expectedVersion) {
    String subject = subject();
    Booking before = lockedOwn(id, subject, expectedVersion);
    Instant now = clock.instant();
    Booking after = before.cancelByPerson(now, subject);
    bookings.cancel(before, after, subject);
    events.publishEvent(BookingCancelled.byPerson(TenantContext.require().id(), id.value()));
    return views.view(bookings.findById(id).orElseThrow(), now);
  }

  /**
   * A reserva da persoa, lida outra vez baixo o bloqueo da súa instalación (ADR-0022, punto 4) e
   * coa versión do {@code If-Match}.
   */
  private Booking lockedOwn(BookingId id, String subject, long expectedVersion) {
    Booking found =
        bookings
            .findOfHolder(id, subject)
            .orElseThrow(() -> new DomainException(BookingError.NOT_FOUND));
    if (!facilities.lockFacility(found.slot().facilityId())) {
      throw new DomainException(BookingError.NOT_FOUND);
    }
    Booking locked =
        bookings
            .findOfHolder(id, subject)
            .orElseThrow(() -> new DomainException(BookingError.NOT_FOUND));
    if (!Objects.equals(locked.version(), expectedVersion)) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return locked;
  }

  private String subject() {
    return currentActor
        .current()
        .orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED))
        .subject();
  }
}
