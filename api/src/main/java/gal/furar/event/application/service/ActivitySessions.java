// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.service;

import gal.furar.booking.ActivityBookings;
import gal.furar.booking.ActivityOccupation;
import gal.furar.booking.ActivityPlan;
import gal.furar.booking.ActivitySlot;
import gal.furar.event.application.port.out.SessionRepository;
import gal.furar.event.domain.Event;
import gal.furar.event.domain.EventError;
import gal.furar.event.domain.EventId;
import gal.furar.event.domain.EventSchedule;
import gal.furar.event.domain.EventSession;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Ids;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * As sesións dunha actividade con horario (ADR-0041). Nunha instalación, ocupar e soltar as horas
 * vai pola API pública de {@code booking} ({@link ActivityBookings}): o mesmo planificador e as
 * mesmas regras ca as tempadas, sen pisar nunca unha reserva. Noutro sitio, unha sesión por data.
 * Dentro da transacción de quen chama, que xa ten o bloqueo da fila do evento (despois, {@code
 * booking} toma o da instalación).
 */
@Component
class ActivitySessions {

  private final ActivityBookings bookings;
  private final SessionRepository sessions;

  ActivitySessions(ActivityBookings bookings, SessionRepository sessions) {
    this.bookings = bookings;
    this.sessions = sessions;
  }

  /** As sesións que crearía ese horario, sen escribir. */
  ActivityPlan preview(@Nullable UUID facilityId, EventSchedule schedule, Instant now) {
    if (facilityId == null) {
      return new ActivityPlan(
          schedule.sessionsOutside(now).stream()
              .map(s -> new ActivityPlan.Session(s.date(), s.start(), s.end()))
              .toList(),
          List.of(),
          List.of(),
          List.of(),
          List.of());
    }
    return bookings.plan(slot(facilityId, schedule));
  }

  /**
   * Ocupa a instalación (se a hai) e devolve as sesións, sen gardalas.
   *
   * @throws DomainException {@code event.sessions-changed} se os días con choque xa non son {@code
   *     skip}, ou {@code event.no-sessions} se non queda ningún día
   */
  List<EventSession> occupy(
      EventId id,
      String name,
      @Nullable UUID facilityId,
      EventSchedule schedule,
      Set<LocalDate> skip,
      Instant now) {
    List<EventSession> created = new ArrayList<>();
    if (facilityId == null) {
      for (EventSchedule.Session s : schedule.sessionsOutside(now)) {
        created.add(session(id, s.date(), s.start(), s.end(), null));
      }
    } else {
      ActivityOccupation occupation =
          bookings.occupy(id.value(), name, slot(facilityId, schedule), skip);
      if (occupation.changed()) {
        throw new DomainException(EventError.SESSIONS_CHANGED);
      }
      for (ActivityOccupation.Occupied o : occupation.sessions()) {
        created.add(session(id, o.date(), o.start(), o.end(), o.bookingId()));
      }
    }
    if (created.isEmpty()) {
      throw new DomainException(EventError.NO_SESSIONS);
    }
    return created;
  }

  /**
   * Cancela as sesións que cumpren {@code which} e aínda se poden cancelar (sen cancelar, sen
   * empezar e sen lista pasada): solta as súas horas na instalación e márcaas canceladas.
   *
   * @return as canceladas
   */
  List<EventSession> release(Event event, Predicate<EventSession> which, Instant now) {
    List<EventSession> chosen =
        sessions.sessionsOf(event.id()).stream()
            .filter(s -> !s.cancelled() && !s.started(now) && s.attendanceTakenAt() == null)
            .filter(which)
            .toList();
    if (chosen.isEmpty()) {
      return List.of();
    }
    UUID facility = event.details().place().facilityId();
    List<UUID> owned =
        chosen.stream().map(EventSession::bookingId).filter(Objects::nonNull).toList();
    if (facility != null && !owned.isEmpty()) {
      bookings.release(event.id().value(), facility, owned);
    }
    sessions.cancel(chosen.stream().map(EventSession::id).toList(), now);
    return chosen;
  }

  /** O nome novo da actividade nas reservas que ocupan a instalación. */
  void rename(Event event) {
    bookings.rename(event.id().value(), event.details().name().gl());
  }

  private static EventSession session(
      EventId id, LocalDate date, Instant start, Instant end, @Nullable UUID booking) {
    return new EventSession(Ids.newV7(), id, date, start, end, booking, null, null);
  }

  private static ActivitySlot slot(UUID facilityId, EventSchedule s) {
    return new ActivitySlot(
        facilityId,
        Objects.requireNonNull(s.useId(), "useId"),
        s.partId(),
        s.isoWeekdays(),
        s.startMinute(),
        s.endMinute(),
        s.firstDate(),
        s.lastDate());
  }
}
