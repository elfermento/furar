// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.BookingCancelled;
import gal.furar.booking.application.port.out.BookingFilter;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.application.port.out.StartCount;
import gal.furar.booking.domain.BookedSlot;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingEventKind;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.BookingStatus;
import gal.furar.booking.domain.Occupied;
import gal.furar.booking.domain.RentalMode;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Reservas no concello actual: falla sen concello ou sen transacción (ADR-0006). */
@Component
class BookingPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements BookingRepository {

  /** Sen fin: «todas as que non remataron». */
  private static final Instant FOREVER = Instant.parse("9999-12-31T00:00:00Z");

  private final BookingSql sql;
  private final BookingSearchSql search;

  BookingPersistenceAdapter(BookingSql sql, BookingSearchSql search) {
    this.sql = sql;
    this.search = search;
  }

  @Override
  public Optional<Booking> findById(BookingId id) {
    return sql.find(tenant().id(), id.value());
  }

  @Override
  public Optional<Booking> findOfHolder(BookingId id, String subject) {
    return sql.findOfHolder(tenant().id(), id.value(), subject);
  }

  @Override
  public PageResult<Booking> ofHolder(
      String subject, boolean upcoming, Instant now, int page, int size) {
    UUID m = tenant().id();
    long offset = (long) page * size;
    return upcoming
        ? new PageResult<>(
            sql.upcoming(m, subject, now, size, offset),
            page,
            size,
            sql.countUpcoming(m, subject, now))
        : new PageResult<>(
            sql.history(m, subject, now, size, offset),
            page,
            size,
            sql.countHistory(m, subject, now));
  }

  @Override
  public PageResult<Booking> search(BookingFilter filter, int page, int size) {
    UUID m = tenant().id();
    long offset = (long) page * size;
    List<Booking> items =
        filter.recentFirst()
            ? search.recent(m, filter, size, offset)
            : search.byStart(m, filter, size, offset);
    return new PageResult<>(items, page, size, search.count(m, filter));
  }

  @Override
  public List<Booking> confirmedStarting(Instant from, Instant to, @Nullable UUID facilityId) {
    return search.confirmedStarting(tenant().id(), from, to, facilityId);
  }

  @Override
  public List<StartCount> countConfirmedStarting(
      Instant from, Instant to, @Nullable UUID facilityId, boolean byHour) {
    UUID m = tenant().id();
    return byHour
        ? search.perHour(m, from, to, facilityId)
        : search.perDay(m, from, to, facilityId);
  }

  @Override
  public Map<UUID, List<Occupied>> activeOccupancy(
      Collection<UUID> facilityIds, Instant from, Instant to) {
    return sql.activeOccupancy(tenant().id(), facilityIds, from, to).stream()
        .collect(
            Collectors.groupingBy(
                BookingSql.FacilityOccupied::facilityId,
                Collectors.mapping(BookingSql.FacilityOccupied::occupied, Collectors.toList())));
  }

  @Override
  public int countConfirmed(
      UUID useId, String subject, Instant from, Instant to, @Nullable BookingId except) {
    return sql.countConfirmed(
        tenant().id(), useId, subject, from, to, except == null ? null : except.value());
  }

  @Override
  public boolean holds(
      UUID useId, String subject, Instant from, Instant to, @Nullable BookingId except) {
    return sql.holds(
        tenant().id(), useId, subject, from, to, except == null ? null : except.value());
  }

  @Override
  public boolean insert(Booking booking, @Nullable Integer capacity, String actorSubject) {
    UUID m = tenant().id();
    try {
      if (!sql.insertBooking(m, booking)) {
        return false;
      }
      sql.insertOccupancy(m, booking);
      takeSeats(m, booking.slot(), capacity);
      sql.insertEvent(
          m, booking.id().value(), BookingEventKind.CREATED, booking.createdAt(), actorSubject);
      return true;
    } catch (DataIntegrityViolationException e) {
      throw refused(e);
    }
  }

  @Override
  public void release(Booking booking) {
    UUID m = tenant().id();
    sql.deactivateOccupancy(m, booking.id().value());
    if (booking.slot().rentalMode() == RentalMode.SEATS) {
      starts(booking.slot()).forEach(at -> sql.releaseSeat(m, booking.slot().useId(), at));
    }
  }

  @Override
  public void reschedule(
      Booking before, Booking after, @Nullable Integer capacity, String actorSubject) {
    UUID m = tenant().id();
    try {
      update(m, before, after);
      sql.insertOccupancy(m, after);
      takeSeats(m, after.slot(), capacity);
      sql.insertChangeEvent(
          m, after.id().value(), after.updatedAt(), actorSubject, before.slot(), after.slot());
    } catch (DataIntegrityViolationException e) {
      throw refused(e);
    }
  }

  @Override
  public void cancel(Booking before, Booking after, String actorSubject) {
    UUID m = tenant().id();
    update(m, before, after);
    release(before);
    BookingEventKind kind =
        after.status() == BookingStatus.CANCELLED_BY_PERSON
            ? BookingEventKind.CANCELLED_BY_PERSON
            : BookingEventKind.CANCELLED_BY_MUNICIPALITY;
    sql.insertEvent(m, after.id().value(), kind, after.updatedAt(), actorSubject);
  }

  @Override
  public void cancel(
      Booking before, Booking after, String actorSubject, Set<BookingCancelled.Channel> channels) {
    if (after.status() != BookingStatus.CANCELLED_BY_MUNICIPALITY) {
      throw new IllegalArgumentException("Only the municipality says how it warns");
    }
    UUID m = tenant().id();
    update(m, before, after);
    release(before);
    sql.insertCancelEvent(
        m,
        after.id().value(),
        after.updatedAt(),
        actorSubject,
        channels.stream().map(Enum::name).toList());
  }

  @Override
  public List<Booking> confirmedNotEnded(
      UUID facilityId,
      @Nullable UUID useId,
      Instant now,
      @Nullable Instant from,
      @Nullable Instant to) {
    return sql.confirmedNotEnded(
        tenant().id(),
        facilityId,
        useId,
        now,
        from == null ? now : from,
        to == null ? FOREVER : to);
  }

  @Override
  public void resizeSeats(UUID useId, int capacity, Instant from) {
    sql.resizeSeats(tenant().id(), useId, capacity, from);
  }

  @Override
  public boolean anyOf(UUID facilityId) {
    return sql.anyOf(tenant().id(), facilityId);
  }

  @Override
  public void keep(Booking booking, Instant at, String actorSubject) {
    sql.insertEvent(tenant().id(), booking.id().value(), BookingEventKind.KEPT, at, actorSubject);
  }

  @Override
  public void changePayment(Booking before, Booking after, String actorSubject) {
    UUID m = tenant().id();
    update(m, before, after);
    sql.insertEvent(
        m,
        after.id().value(),
        BookingEventKind.ofPayment(after.paymentStatus()),
        after.updatedAt(),
        actorSubject);
  }

  private void update(UUID m, Booking before, Booking after) {
    long expected = Objects.requireNonNull(before.version(), "version");
    if (!sql.updateBooking(m, after, expected)) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
  }

  private void takeSeats(UUID m, BookedSlot slot, @Nullable Integer capacity) {
    if (slot.rentalMode() == RentalMode.SEATS) {
      int seats = Objects.requireNonNull(capacity, "capacity");
      starts(slot).forEach(at -> sql.takeSeat(m, slot.useId(), at, seats));
    }
  }

  /** O inicio de cada franxa do oco. */
  private static List<Instant> starts(BookedSlot slot) {
    Duration step = Duration.between(slot.start(), slot.end()).dividedBy(slot.slots());
    List<Instant> starts = new ArrayList<>();
    for (Instant at = slot.start(); at.isBefore(slot.end()); at = at.plus(step)) {
      starts.add(at);
    }
    return starts;
  }

  /**
   * As garantías da base (ADR-0012) como erros de negocio. Baixo o bloqueo da instalación non
   * deberían saltar: son a rede se o código fallase.
   */
  private static RuntimeException refused(DataIntegrityViolationException e) {
    String message = String.valueOf(e.getMostSpecificCause().getMessage());
    if (message.contains("booking_one_seat_per_person")) {
      return new DomainException(BookingError.SEAT_TAKEN_BY_YOU);
    }
    if (message.contains("booking_occupancy_part")) {
      return new DomainException(BookingError.PART_TAKEN);
    }
    if (message.contains("booking_occupancy_") || message.contains("use_slot_taken_ck")) {
      return new DomainException(BookingError.SLOT_TAKEN);
    }
    return e;
  }
}
