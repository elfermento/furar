// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.service;

import gal.furar.booking.ActivityBookings;
import gal.furar.booking.ActivityOccupation;
import gal.furar.booking.ActivityPlan;
import gal.furar.booking.ActivitySlot;
import gal.furar.event.domain.EventSchedule;
import gal.furar.event.domain.WaitlistClock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** O que comparten as probas das actividades: o reloxo da lista e unhas reservas de mentira. */
final class ActivityFixtures {

  /** Unha hora de exclusividade e silencio das 22:00 ás 8:00 en Europe/Madrid. */
  static final WaitlistClock CLOCK =
      new WaitlistClock(
          Duration.ofHours(1), LocalTime.of(22, 0), LocalTime.of(8, 0), EventSchedule.ZONE);

  private ActivityFixtures() {}

  /**
   * As reservas dunha instalación sen choques salvo os días de {@code busy}; garda o que se ocupou
   * e o que se soltou.
   */
  static final class FakeBookings implements ActivityBookings {

    final Set<LocalDate> busy = new TreeSet<>();
    final Set<UUID> occupied = new LinkedHashSet<>();
    final Set<UUID> released = new LinkedHashSet<>();
    final List<String> renamed = new ArrayList<>();

    @Override
    public ActivityPlan plan(ActivitySlot slot) {
      EventSchedule schedule =
          EventSchedule.of(
              slot.useId(),
              slot.partId(),
              slot.weekdays(),
              slot.startMinute(),
              slot.endMinute(),
              slot.firstDate(),
              slot.lastDate());
      List<ActivityPlan.Session> sessions = new ArrayList<>();
      List<ActivityPlan.Conflict> conflicts = new ArrayList<>();
      for (EventSchedule.Session s : schedule.sessionsOutside(Instant.EPOCH)) {
        if (busy.contains(s.date())) {
          conflicts.add(
              new ActivityPlan.Conflict(
                  s.date(),
                  s.start(),
                  s.end(),
                  ActivityPlan.ConflictKind.BOOKING,
                  UUID.randomUUID(),
                  "Reserva",
                  null));
        } else {
          sessions.add(new ActivityPlan.Session(s.date(), s.start(), s.end()));
        }
      }
      return new ActivityPlan(sessions, List.of(), List.of(), List.of(), conflicts);
    }

    @Override
    public ActivityOccupation occupy(
        UUID activityId, String name, ActivitySlot slot, Set<LocalDate> skip) {
      ActivityPlan plan = plan(slot);
      if (!plan.conflictDates().equals(new TreeSet<>(skip))) {
        return new ActivityOccupation(plan, true, List.of());
      }
      List<ActivityOccupation.Occupied> out = new ArrayList<>();
      for (ActivityPlan.Session s : plan.sessions()) {
        UUID booking = UUID.randomUUID();
        occupied.add(booking);
        out.add(new ActivityOccupation.Occupied(s.date(), s.start(), s.end(), booking));
      }
      return new ActivityOccupation(plan, false, out);
    }

    @Override
    public Set<UUID> release(UUID activityId, UUID facilityId, Collection<UUID> bookingIds) {
      released.addAll(bookingIds);
      return Set.copyOf(bookingIds);
    }

    @Override
    public void rename(UUID activityId, String name) {
      renamed.add(name);
    }
  }
}
