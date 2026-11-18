// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.persistence;

import gal.furar.booking.BookingCancelled;
import gal.furar.booking.application.port.out.BookingFilter;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.application.port.out.ConfirmedSpan;
import gal.furar.booking.application.port.out.StartCount;
import gal.furar.booking.domain.BookedSlot;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingEventKind;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.BookingStatus;
import gal.furar.booking.domain.Holder;
import gal.furar.booking.domain.Occupied;
import gal.furar.booking.domain.Participant;
import gal.furar.booking.domain.Participants;
import gal.furar.booking.domain.RentalMode;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Ids;
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

  /** Ningunha reserva ten este id: «sen excepción» na praza. */
  private static final UUID NONE = new UUID(0, 0);

  private final BookingSql sql;
  private final BookingSearchSql search;
  private final BookingParticipantSql people;

  private final BookingAttendanceSql attendance;

  BookingPersistenceAdapter(
      BookingSql sql,
      BookingSearchSql search,
      BookingParticipantSql people,
      BookingAttendanceSql attendance) {
    this.sql = sql;
    this.search = search;
    this.people = people;
    this.attendance = attendance;
  }

  @Override
  public Optional<Booking> findLocked(BookingId id) {
    return attendance.findLocked(tenant().id(), id.value());
  }

  @Override
  public Optional<Booking> findByCodeLocked(String code) {
    return attendance.findByCodeLocked(tenant().id(), code);
  }

  @Override
  public UUID markAttendance(
      Booking before, Booking after, BookingEventKind kind, String actorSubject) {
    UUID m = tenant().id();
    long expected = Objects.requireNonNull(before.version(), "version");
    if (!sql.updateBooking(m, after, expected)) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return sql.insertEvent(m, after.id().value(), kind, after.updatedAt(), actorSubject);
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
  public Optional<Booking> findOfOrganization(BookingId id, UUID organizationId) {
    return sql.findOfOrganization(tenant().id(), id.value(), organizationId);
  }

  @Override
  public PageResult<Booking> ofOrganization(
      UUID organizationId, boolean upcoming, Instant now, int page, int size) {
    UUID m = tenant().id();
    long offset = (long) page * size;
    return upcoming
        ? new PageResult<>(
            sql.upcomingOfOrganization(m, organizationId, now, size, offset),
            page,
            size,
            sql.countUpcomingOfOrganization(m, organizationId, now))
        : new PageResult<>(
            sql.historyOfOrganization(m, organizationId, now, size, offset),
            page,
            size,
            sql.countHistoryOfOrganization(m, organizationId, now));
  }

  @Override
  public int countConfirmedOfOrganization(
      UUID useId, UUID organizationId, Instant from, Instant to) {
    return sql.countConfirmedOfOrganization(tenant().id(), useId, organizationId, from, to);
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
      UUID useId, String emailKey, Instant from, Instant to, @Nullable BookingId except) {
    return sql.countConfirmed(
        tenant().id(), useId, emailKey, from, to, except == null ? null : except.value());
  }

  @Override
  public Set<String> bookedAmong(
      UUID useId, Collection<String> emailKeys, Instant from, Instant to) {
    return Set.copyOf(sql.bookedAmong(tenant().id(), useId, emailKeys, from, to));
  }

  @Override
  public List<ConfirmedSpan> confirmedSpans(String emailKey, Instant from, Instant to) {
    return sql.confirmedSpans(tenant().id(), emailKey, from, to);
  }

  @Override
  public Set<String> seated(
      UUID useId,
      Collection<String> personKeys,
      Instant from,
      Instant to,
      @Nullable BookingId except) {
    return Set.copyOf(
        people.seated(
            tenant().id(), useId, personKeys, from, to, except == null ? NONE : except.value()));
  }

  @Override
  public Map<UUID, List<Participant>> participants(Collection<BookingId> ids) {
    return people.of(tenant().id(), ids.stream().map(BookingId::value).toList()).stream()
        .collect(
            Collectors.groupingBy(
                BookingParticipantSql.Row::bookingId,
                Collectors.mapping(BookingParticipantSql.Row::participant, Collectors.toList())));
  }

  @Override
  public int link(String emailKey, String subject, Instant now) {
    UUID m = tenant().id();
    // Unha lectura sobre o índice parcial; só se hai algo, a escritura.
    if (sql.unlinked(m, emailKey).isEmpty()) {
      return 0;
    }
    List<UUID> linked = sql.link(m, emailKey, subject);
    linked.forEach(id -> sql.insertEvent(m, id, BookingEventKind.LINKED, now, subject));
    return linked.size();
  }

  @Override
  public boolean insert(
      Booking booking, @Nullable Integer capacity, String actorSubject, Participants participants) {
    UUID m = tenant().id();
    try {
      if (!sql.insertBooking(m, booking)) {
        return false;
      }
      sql.insertOccupancy(m, booking);
      takeSeats(m, booking, capacity);
      insertParticipants(m, booking, participants);
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
    free(m, booking);
    people.replace(m, booking.id().value());
  }

  /** A ocupación inactiva e, nas prazas, cada franxa devolve as da reserva. */
  private void free(UUID m, Booking booking) {
    sql.deactivateOccupancy(m, booking.id().value());
    if (booking.slot().rentalMode() == RentalMode.SEATS) {
      starts(booking.slot())
          .forEach(at -> sql.releaseSeats(m, booking.slot().useId(), at, booking.seats()));
    }
  }

  @Override
  public void reschedule(
      Booking before,
      Booking after,
      @Nullable Integer capacity,
      String actorSubject,
      Participants participants) {
    UUID m = tenant().id();
    try {
      update(m, before, after);
      sql.insertOccupancy(m, after);
      takeSeats(m, after, capacity);
      insertParticipants(m, after, participants);
      if (holderEmailChanged(before, after)) {
        sql.insertEvent(
            m,
            after.id().value(),
            BookingEventKind.HOLDER_CHANGED,
            after.updatedAt(),
            actorSubject);
      }
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
    free(m, before);
    people.deactivate(m, before.id().value());
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
    free(m, before);
    people.deactivate(m, before.id().value());
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
  public void correct(Booking before, Booking after, String actorSubject) {
    UUID m = tenant().id();
    try {
      update(m, before, after);
      rekeyHolder(m, before, after, actorSubject);
    } catch (DataIntegrityViolationException e) {
      throw refused(e);
    }
  }

  /**
   * Se o persoal corrixiu o correo: a praza do titular pasa a ser do correo novo (ou de ningún) e
   * queda a fila {@code HOLDER_CHANGED}, sen correos.
   */
  private void rekeyHolder(UUID m, Booking before, Booking after, String actorSubject) {
    if (!holderEmailChanged(before, after)) {
      return;
    }
    String key = after.requireHolder().emailKey();
    if (key == null) {
      people.unkeyHolder(m, after.id().value());
    } else {
      people.rekeyHolder(m, after.id().value(), "e:" + key);
    }
    sql.insertEvent(
        m, after.id().value(), BookingEventKind.HOLDER_CHANGED, after.updatedAt(), actorSubject);
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
    // Cancelala ou cambiala o persoal borra a marca de asistencia (ADR-0032, punto 2).
    String unmarkedBy = after.mark().by();
    if (before.mark().marked() && !after.mark().marked() && unmarkedBy != null) {
      sql.insertEvent(
          m, after.id().value(), BookingEventKind.ATTENDANCE_UNDONE, after.updatedAt(), unmarkedBy);
    }
    // Para quen é: só cambia nas que fixo o persoal (Booking#withHolder).
    Holder holder = after.holder();
    if (after.bookedBy() != null && holder != null && !holder.equals(before.holder())) {
      sql.updateHolder(m, after.id().value(), holder);
    }
  }

  private static boolean holderEmailChanged(Booking before, Booking after) {
    Holder was = before.holder();
    Holder now = after.holder();
    return was != null && now != null && !Objects.equals(was.emailKey(), now.emailKey());
  }

  private void takeSeats(UUID m, Booking booking, @Nullable Integer capacity) {
    BookedSlot slot = booking.slot();
    if (slot.rentalMode() == RentalMode.SEATS) {
      int total = Objects.requireNonNull(capacity, "capacity");
      starts(slot).forEach(at -> sql.takeSeats(m, slot.useId(), at, total, booking.seats()));
    }
  }

  /**
   * As persoas da reserva, na súa orde. A clave de cada unha: o titular, polo seu correo; a persoa
   * a cargo, pola súa ficha; sen correo e os acompañantes, a propia fila (non se comparan con
   * outras reservas).
   */
  private void insertParticipants(UUID m, Booking booking, Participants participants) {
    String emailKey = booking.requireHolder().emailKey();
    int position = 0;
    for (Participant p : participants.list()) {
      UUID id = Ids.newV7();
      people.insert(
          m,
          id,
          booking.id().value(),
          booking.slot().useId(),
          booking.slot().start(),
          booking.slot().end(),
          p,
          p.personKey(emailKey).orElse("w:" + id),
          position++);
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
    if (message.contains("booking_participant_one_seat")) {
      return new DomainException(BookingError.SEAT_TAKEN_BY_YOU);
    }
    // A ficha borrouse mentres se reservaba (a FK cara a account_dependent).
    if (message.contains("booking_participant_dependent_id_fkey")) {
      return new DomainException(BookingError.DEPENDENT_UNKNOWN);
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
