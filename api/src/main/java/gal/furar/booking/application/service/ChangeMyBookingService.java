// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingCancelled;
import gal.furar.booking.BookingChanged;
import gal.furar.booking.application.port.in.ChangeBookingCommand;
import gal.furar.booking.application.port.in.ChangeMyBookingUseCase;
import gal.furar.booking.application.port.in.MyBooking;
import gal.furar.booking.application.port.in.ParticipantSelection;
import gal.furar.booking.application.port.in.SlotTakenException;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.domain.BookableUse;
import gal.furar.booking.domain.BookedSlot;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.BookingRules;
import gal.furar.booking.domain.Participants;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
  private final Parties parties;
  private final WaitNotifier waitNotifier;
  private final OpenMatchHooks openMatches;
  private final CurrentActor currentActor;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public ChangeMyBookingService(
      FacilityQueries facilities,
      BookingRepository bookings,
      BookingViews views,
      SlotGrids grids,
      Parties parties,
      WaitNotifier waitNotifier,
      OpenMatchHooks openMatches,
      CurrentActor currentActor,
      ApplicationEventPublisher events,
      Clock clock) {
    this.facilities = facilities;
    this.bookings = bookings;
    this.views = views;
    this.grids = grids;
    this.parties = parties;
    this.waitNotifier = waitNotifier;
    this.openMatches = openMatches;
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
    // Fóra de prazo (ou xa cancelada) non se cambia por internet.
    before.checkChangeable(now);
    UUID facilityId = before.slot().facilityId();
    UseSummary summary =
        facilities
            .use(command.useId())
            .orElseThrow(() -> new DomainException(CommonError.NOT_FOUND));
    // Só dentro da mesma instalación, así basta un bloqueo.
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

    // Para quen é despois do cambio: sen dicilo, as mesmas persoas (se o uso segue sendo por
    // prazas). Os acompañantes que escribiu o persoal quedan: a persoa non os quita.
    Participants party = party(before, use.rentalMode(), command.participants(), subject);
    String emailKey = before.requireHolder().emailKey();

    // 1. Libera a ocupación vella: dende aquí, a reserva non conta para o oco nin para o límite.
    bookings.release(before);
    // 2. O oco novo, coma se a vella non existise.
    SlotGrid grid = grids.of(facilityId, start.atZone(SlotGrid.ZONE).toLocalDate(), now);
    parties.checkSeats(use.id(), party, emailKey, start, end, before.id());
    UUID part;
    try {
      part =
          grid.allocate(use, start, command.slots(), command.partId(), party.seats()).orElse(null);
    } catch (DomainException e) {
      if (e.errorCode() == BookingError.SLOT_TAKEN) {
        throw new SlotTakenException(grid.alternatives(use, start, command.slots()));
      }
      throw e;
    }
    if (emailKey != null) {
      rules
          .capWindow(start)
          .ifPresent(
              w ->
                  rules.checkCap(
                      bookings.countConfirmed(use.id(), emailKey, w.from(), w.to(), before.id())));
    }
    // 3. Prezo, normas e prazo novos; só entón escribe sobre a mesma fila.
    Booking after =
        before.reschedule(
            new BookedSlot(
                facilityId, use.id(), part, use.rentalMode(), start, end, command.slots()),
            party.seats(),
            Uses.unitPrice(summary),
            rules,
            now);
    // A partida aberta vai coa reserva: só se a hora nova aínda deixa abrila (ADR-0040, p. 6).
    openMatches.checkPersonMove(after, now);
    bookings.reschedule(before, after, use.capacity(), subject, party);
    Booking saved = bookings.findById(after.id()).orElseThrow();
    openMatches.moved(before, saved, false, now);
    // A espera polo oco novo está atendida; o vello pode quedar libre para quen o esperaba.
    waitNotifier.served(emailKey, after.slot());
    waitNotifier.released(
        facilityId, List.of(WaitNotifier.occupied(before)), List.of(WaitNotifier.occupied(after)));
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
    openMatches.bookingCancelled(before, now);
    waitNotifier.released(
        before.slot().facilityId(), List.of(WaitNotifier.occupied(before)), List.of());
    events.publishEvent(BookingCancelled.byPerson(TenantContext.require().id(), id.value()));
    return views.view(bookings.findById(id).orElseThrow(), now);
  }

  private Participants party(
      Booking before, RentalMode mode, @Nullable ParticipantSelection selection, String subject) {
    boolean wasBySeats = before.slot().rentalMode() == RentalMode.SEATS;
    if (selection == null) {
      return wasBySeats && mode == RentalMode.SEATS
          ? parties.current(mode, before.id())
          : parties.resolve(mode, ParticipantSelection.SELF, subject);
    }
    ParticipantSelection kept =
        wasBySeats && mode == RentalMode.SEATS
            ? new ParticipantSelection(
                selection.self(), selection.dependentIds(), parties.companions(before.id()))
            : selection;
    return parties.resolve(mode, kept, subject);
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
