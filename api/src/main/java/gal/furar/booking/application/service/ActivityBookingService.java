// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.ActivityBookings;
import gal.furar.booking.ActivityOccupation;
import gal.furar.booking.ActivityPlan;
import gal.furar.booking.ActivitySlot;
import gal.furar.booking.application.port.out.SeriesStore;
import gal.furar.booking.domain.BookableUse;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingCode;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.Holder;
import gal.furar.booking.domain.season.SeriesConflict;
import gal.furar.booking.domain.season.SeriesPlan;
import gal.furar.booking.domain.season.SeriesSchedule;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.UseSummary;
import gal.furar.shared.domain.DomainException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa {@link ActivityBookings} (ADR-0041) co planificador e a escritura en lote das tempadas
 * ({@link SeriesWriter}, ADR-0039 punto 8): unha actividade ocupa a instalación coma unha tempada,
 * sen entidade. Non é un caso de uso: os permisos xa os comprobou {@code event}.
 */
@Service
class ActivityBookingService implements ActivityBookings {

  private final FacilityQueries facilities;
  private final SeriesWriter writer;
  private final SeriesStore store;
  private final SeasonSupport support;
  private final WaitNotifier waitNotifier;
  private final Clock clock;
  private final RandomGenerator random = new SecureRandom();

  ActivityBookingService(
      FacilityQueries facilities,
      SeriesWriter writer,
      SeriesStore store,
      SeasonSupport support,
      WaitNotifier waitNotifier,
      Clock clock) {
    this.facilities = facilities;
    this.writer = writer;
    this.store = store;
    this.support = support;
    this.waitNotifier = waitNotifier;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public ActivityPlan plan(ActivitySlot slot) {
    Instant now = clock.instant();
    SeriesSchedule schedule = schedule(slot);
    schedule.check(today(now));
    return view(writer.plan(schedule, support.use(schedule, BookingError.ACTIVITY_SEATS_USE), now));
  }

  @Override
  @Transactional
  public ActivityOccupation occupy(
      UUID activityId, String name, ActivitySlot slot, Set<LocalDate> skip) {
    SeriesSchedule schedule = schedule(slot);
    if (!facilities.lockFacility(schedule.facilityId())) {
      throw new DomainException(BookingError.FACILITY_NOT_BOOKABLE);
    }
    Instant now = clock.instant();
    schedule.check(today(now));
    UseSummary use = support.use(schedule, BookingError.ACTIVITY_SEATS_USE);
    SeriesPlan plan = writer.plan(schedule, use, now);
    ActivityPlan view = view(plan);
    if (!plan.conflictDates().equals(new TreeSet<>(skip))) {
      return new ActivityOccupation(view, true, List.of());
    }
    String actor = support.subject();
    Holder holder = Booking.organizationHolder(name, support.language());
    BookableUse bookable = support.bookable(use);
    List<Booking> sessions = new ArrayList<>();
    List<ActivityOccupation.Occupied> occupied = new ArrayList<>();
    for (SeriesPlan.Session s : plan.sessions()) {
      Booking booking =
          Booking.createActivitySession(
              BookingId.newId(),
              BookingCode.random(random),
              SeriesWriter.slot(schedule, bookable, s),
              holder,
              activityId,
              actor,
              now);
      sessions.add(booking);
      occupied.add(
          new ActivityOccupation.Occupied(s.date(), s.start(), s.end(), booking.id().value()));
    }
    // O código pode cambiar ao escribir; o id, non.
    writer.write(sessions, actor);
    return new ActivityOccupation(view, false, occupied);
  }

  @Override
  @Transactional
  public Set<UUID> release(UUID activityId, UUID facilityId, Collection<UUID> bookingIds) {
    if (bookingIds.isEmpty() || !facilities.lockFacility(facilityId)) {
      return Set.of();
    }
    Instant now = clock.instant();
    List<Booking> found = store.activitySessions(activityId, bookingIds, now);
    if (found.isEmpty()) {
      return Set.of();
    }
    String actor = support.subject();
    List<Booking> cancelled = found.stream().map(b -> b.cancelByMunicipality(now, actor)).toList();
    store.cancelSessions(found, cancelled, actor);
    waitNotifier.released(
        facilityId, found.stream().map(WaitNotifier::occupied).toList(), List.of());
    Set<UUID> ids = new LinkedHashSet<>();
    found.forEach(b -> ids.add(b.id().value()));
    return ids;
  }

  @Override
  @Transactional
  public void rename(UUID activityId, String name) {
    store.renameActivity(activityId, name);
  }

  private static SeriesSchedule schedule(ActivitySlot slot) {
    return SeriesSchedule.of(
        slot.facilityId(),
        slot.useId(),
        slot.partId(),
        slot.weekdays(),
        slot.startMinute(),
        slot.endMinute(),
        slot.firstDate(),
        slot.lastDate());
  }

  private static ActivityPlan view(SeriesPlan plan) {
    return new ActivityPlan(
        plan.sessions().stream()
            .map(s -> new ActivityPlan.Session(s.date(), s.start(), s.end()))
            .toList(),
        plan.holidays(),
        plan.useOff(),
        plan.closed(),
        plan.conflicts().stream().map(ActivityBookingService::conflict).toList());
  }

  private static ActivityPlan.Conflict conflict(SeriesConflict c) {
    return new ActivityPlan.Conflict(
        c.date(),
        c.start(),
        c.end(),
        ActivityPlan.ConflictKind.valueOf(c.kind().name()),
        c.bookingId(),
        c.name(),
        c.label());
  }

  private static LocalDate today(Instant now) {
    return now.atZone(SeriesSchedule.ZONE).toLocalDate();
  }
}
