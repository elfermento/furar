// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.service;

import gal.furar.booking.ActivityPlan;
import gal.furar.event.EventEndedEarly;
import gal.furar.event.EventPermission;
import gal.furar.event.EventSessionCancelled;
import gal.furar.event.application.port.in.EventCommand;
import gal.furar.event.application.port.in.EventView;
import gal.furar.event.application.port.in.ManageActivityUseCase;
import gal.furar.event.application.port.in.SessionView;
import gal.furar.event.application.port.out.EventRepository;
import gal.furar.event.application.port.out.RegistrationRepository;
import gal.furar.event.application.port.out.SessionRepository;
import gal.furar.event.domain.Event;
import gal.furar.event.domain.EventError;
import gal.furar.event.domain.EventId;
import gal.furar.event.domain.EventSchedule;
import gal.furar.event.domain.EventSession;
import gal.furar.facility.FacilityQueries;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As sesións das actividades con horario na xestión (ADR-0041): a vista previa, a lista, cancelar
 * unha sesión e rematar antes. Primeiro o bloqueo da fila do evento e despois, en {@code booking},
 * o da instalación (ningún camiño de {@code booking} bloquea despois un evento: sen ciclos).
 */
@Service
public class EventActivityService implements ManageActivityUseCase {

  public static final String SESSION_CANCEL = "event.session-cancel";
  public static final String END = "event.end";

  private final EventRepository repository;
  private final RegistrationRepository registrations;
  private final SessionRepository sessions;
  private final ActivitySessions activity;
  private final EventViews views;
  private final FacilityQueries facilities;
  private final AuditLog audit;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public EventActivityService(
      EventRepository repository,
      RegistrationRepository registrations,
      SessionRepository sessions,
      ActivitySessions activity,
      EventViews views,
      FacilityQueries facilities,
      AuditLog audit,
      ApplicationEventPublisher events,
      Clock clock) {
    this.repository = repository;
    this.registrations = registrations;
    this.sessions = sessions;
    this.activity = activity;
    this.views = views;
    this.facilities = facilities;
    this.audit = audit;
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(EventPermission.Code.MANAGE)
  public ActivityPlan preview(@Nullable UUID facilityId, EventCommand.ScheduleCommand command) {
    Instant now = clock.instant();
    EventSchedule schedule =
        EventSchedule.of(
            command.useId(),
            command.partId(),
            command.weekdays(),
            command.startMinute(),
            command.endMinute(),
            command.firstDate(),
            command.lastDate());
    schedule.check(today(now));
    if ((facilityId == null) != (schedule.useId() == null)) {
      throw new DomainException(EventError.SCHEDULE_INVALID);
    }
    if (facilityId != null && facilities.find(facilityId).filter(f -> !f.withdrawn()).isEmpty()) {
      throw new DomainException(EventError.FACILITY_NOT_FOUND);
    }
    return activity.preview(facilityId, schedule, now);
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(EventPermission.Code.VIEW)
  public List<SessionView> sessions(EventId id) {
    scheduled(load(id));
    Map<UUID, SessionRepository.Tally> tallies = sessions.tallies(id);
    return sessions.sessionsOf(id).stream()
        .map(
            s -> {
              SessionRepository.Tally t =
                  tallies.getOrDefault(s.id(), SessionRepository.Tally.NONE);
              return new SessionView(s, t.present(), t.absent());
            })
        .toList();
  }

  @Override
  @Transactional
  @RequiresPermission(EventPermission.Code.MANAGE)
  public SessionView cancelSession(EventId id, UUID sessionId) {
    lock(id);
    Event event = scheduled(load(id));
    if (event.cancelled()) {
      throw new DomainException(EventError.CANCELLED);
    }
    Instant now = clock.instant();
    EventSession session =
        sessions
            .find(id, sessionId)
            .orElseThrow(() -> new DomainException(EventError.SESSION_NOT_FOUND));
    session.checkCancellable(now);
    activity.release(event, s -> s.id().equals(sessionId), now);
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("eventId", id.value().toString());
    details.put("sessionId", sessionId.toString());
    details.put("date", session.date().toString());
    record(SESSION_CANCEL, details);
    events.publishEvent(
        new EventSessionCancelled(TenantContext.require().id(), id.value(), sessionId));
    return new SessionView(sessions.find(id, sessionId).orElse(session.cancel(now)), 0, 0);
  }

  @Override
  @Transactional
  @RequiresPermission(EventPermission.Code.MANAGE)
  public EventView end(EventId id, LocalDate from) {
    lock(id);
    Event event = load(id);
    Instant now = clock.instant();
    LocalDate today = today(now);
    // As comprobacións, antes de soltar nada.
    Event checked = event.endFrom(from, today, event.details().endsAt(), now);
    if (checked == event) {
      return views.of(event);
    }
    List<EventSession> released = activity.release(event, s -> !s.date().isBefore(from), now);
    Instant end =
        sessions.sessionsOf(id).stream()
            .filter(s -> !s.cancelled())
            .map(EventSession::endsAt)
            .max(Instant::compareTo)
            .orElse(event.details().startsAt());
    Event saved = repository.update(event.endFrom(from, today, end, now));
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("eventId", id.value().toString());
    details.put("from", from.toString());
    details.put("cancelled", released.size());
    record(END, details);
    events.publishEvent(
        new EventEndedEarly(TenantContext.require().id(), id.value(), Ids.newV7(), from));
    return views.of(saved);
  }

  private static Event scheduled(Event event) {
    if (!event.details().scheduled()) {
      throw new DomainException(EventError.NOT_SCHEDULED);
    }
    return event;
  }

  private void lock(EventId id) {
    if (!registrations.lockEvent(id)) {
      throw new DomainException(EventError.NOT_FOUND);
    }
  }

  private Event load(EventId id) {
    return repository.findById(id).orElseThrow(() -> new DomainException(EventError.NOT_FOUND));
  }

  private static LocalDate today(Instant now) {
    return now.atZone(EventSchedule.ZONE).toLocalDate();
  }

  private void record(String action, Map<String, Object> details) {
    TenantRef tenant = TenantContext.require();
    audit.record(new AuditEvent(action, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
  }
}
