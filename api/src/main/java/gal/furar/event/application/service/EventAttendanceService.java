// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.service;

import gal.furar.event.EventPermission;
import gal.furar.event.application.port.in.EventAttendanceUseCase;
import gal.furar.event.application.port.out.EventRepository;
import gal.furar.event.application.port.out.RegistrationRepository;
import gal.furar.event.application.port.out.SessionRepository;
import gal.furar.event.domain.Event;
import gal.furar.event.domain.EventError;
import gal.furar.event.domain.EventId;
import gal.furar.event.domain.EventSchedule;
import gal.furar.event.domain.EventSession;
import gal.furar.event.domain.Registration;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pasar lista nas sesións das actividades con horario (ADR-0041). Desde a marxe da validación das
 * reservas antes do inicio ({@code furar.booking.validation-margin}, ADR-0032) e despois, para
 * corrixir, sempre. Nunha sesión sen lista todas as persoas con praza veñen presentes; a sesión
 * conta como «lista pasada» só ao gardar. Unha entrada no rexistro de actividade por cada vez que
 * se garda unha sesión, só cos números.
 */
@Service
public class EventAttendanceService implements EventAttendanceUseCase {

  public static final String ATTENDANCE = "event.attendance";

  private final EventRepository events;
  private final RegistrationRepository registrations;
  private final SessionRepository sessions;
  private final FacilityQueries facilities;
  private final AuditLog audit;
  private final CurrentActor currentActor;
  private final Clock clock;
  private final Duration margin;

  public EventAttendanceService(
      EventRepository events,
      RegistrationRepository registrations,
      SessionRepository sessions,
      FacilityQueries facilities,
      AuditLog audit,
      CurrentActor currentActor,
      Clock clock,
      @Value("${furar.booking.validation-margin:PT45M}") Duration margin) {
    this.events = events;
    this.registrations = registrations;
    this.sessions = sessions;
    this.facilities = facilities;
    this.audit = audit;
    this.currentActor = currentActor;
    this.clock = clock;
    this.margin = margin;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(EventPermission.Code.ATTENDANCE)
  public SessionAttendance attendance(EventId id, UUID sessionId) {
    load(id);
    EventSession session = session(id, sessionId);
    return view(id, session, sessions.marks(sessionId));
  }

  @Override
  @Transactional
  @RequiresPermission(EventPermission.Code.ATTENDANCE)
  public SessionAttendance mark(EventId id, UUID sessionId, Map<UUID, @Nullable Boolean> marks) {
    load(id);
    EventSession session = session(id, sessionId);
    Instant now = clock.instant();
    session.checkAttendance(now, margin);
    Set<UUID> confirmed =
        confirmed(id).stream().map(r -> r.id().value()).collect(Collectors.toSet());
    if (!confirmed.containsAll(marks.keySet())) {
      throw new DomainException(EventError.ATTENDANCE_INVALID);
    }
    Map<UUID, @Nullable Boolean> saved = new HashMap<>(marks);
    boolean first = session.attendanceTakenAt() == null;
    if (first) {
      // A primeira vez, quen non vén na petición estaba presente (a lista empeza así).
      confirmed.forEach(r -> saved.putIfAbsent(r, Boolean.TRUE));
    }
    sessions.mark(sessionId, saved, now, staffName());
    Map<UUID, Boolean> after = sessions.marks(sessionId);
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("eventId", id.value().toString());
    details.put("sessionId", sessionId.toString());
    details.put("present", after.values().stream().filter(Boolean::booleanValue).count());
    details.put("absent", after.values().stream().filter(p -> !p).count());
    details.put("correction", !first);
    TenantRef tenant = TenantContext.require();
    audit.record(new AuditEvent(ATTENDANCE, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
    return view(id, session(id, sessionId), after);
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(EventPermission.Code.ATTENDANCE)
  public List<AttendeeSummary> summary(EventId id) {
    load(id);
    Map<UUID, SessionRepository.Summary> summary = sessions.summary(id);
    int taken =
        (int)
            sessions.sessionsOf(id).stream()
                .filter(s -> !s.cancelled() && s.attendanceTakenAt() != null)
                .count();
    return confirmed(id).stream()
        .map(
            r -> {
              SessionRepository.Summary s = summary.get(r.id().value());
              return s == null
                  ? new AttendeeSummary(r, taken, 0, 0)
                  : new AttendeeSummary(r, s.sessions(), s.present(), s.absent());
            })
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(EventPermission.Code.ATTENDANCE)
  public List<TodaySession> today() {
    List<SessionRepository.Today> rows =
        sessions.on(clock.instant().atZone(EventSchedule.ZONE).toLocalDate());
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<UUID, RegistrationRepository.Counts> counts =
        registrations.counts(rows.stream().map(SessionRepository.Today::eventId).toList());
    List<UUID> places =
        rows.stream()
            .map(SessionRepository.Today::facilityId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<UUID, FacilitySummary> byId =
        places.isEmpty()
            ? Map.of()
            : facilities.summary(places).stream()
                .collect(Collectors.toMap(FacilitySummary::id, Function.identity()));
    Map<EventId, Map<UUID, SessionRepository.Tally>> tallies = new HashMap<>();
    rows.stream()
        .map(SessionRepository.Today::eventId)
        .distinct()
        .forEach(id -> tallies.put(id, sessions.tallies(id)));
    return rows.stream()
        .map(
            t -> {
              SessionRepository.Tally tally =
                  tallies
                      .getOrDefault(t.eventId(), Map.of())
                      .getOrDefault(t.session().id(), SessionRepository.Tally.NONE);
              return new TodaySession(
                  t.eventId(),
                  t.nameGl(),
                  t.nameEs(),
                  t.facilityId() == null ? null : byId.get(t.facilityId()),
                  t.place(),
                  t.session(),
                  counts
                      .getOrDefault(t.eventId().value(), RegistrationRepository.Counts.NONE)
                      .confirmed(),
                  tally.present(),
                  tally.absent());
            })
        .toList();
  }

  /** O nome de quen garda a lista ou, sen el, o seu correo. */
  private @Nullable String staffName() {
    return currentActor
        .current()
        .map(
            a -> {
              String name = a.profile().name();
              return name == null || name.isBlank() ? a.profile().email() : name;
            })
        .map(n -> n.length() > 200 ? n.substring(0, 200) : n)
        .orElse(null);
  }

  private SessionAttendance view(EventId id, EventSession session, Map<UUID, Boolean> marks) {
    boolean taken = session.attendanceTakenAt() != null;
    SessionRepository.Saved saved = taken ? sessions.saved(session.id()).orElse(null) : null;
    List<AttendeeMark> attendees =
        confirmed(id).stream()
            .map(r -> new AttendeeMark(r, taken ? marks.get(r.id().value()) : Boolean.TRUE))
            .toList();
    return new SessionAttendance(
        session,
        taken,
        saved == null ? session.attendanceTakenAt() : saved.at(),
        saved == null ? null : saved.byName(),
        attendees);
  }

  /** As persoas con praza, pola orde en que chegaron. */
  private List<Registration> confirmed(EventId id) {
    return registrations.listForEvent(id).stream().filter(Registration::confirmed).toList();
  }

  private EventSession session(EventId id, UUID sessionId) {
    return sessions
        .find(id, sessionId)
        .orElseThrow(() -> new DomainException(EventError.SESSION_NOT_FOUND));
  }

  private Event load(EventId id) {
    Event event = events.findById(id).orElseThrow(() -> new DomainException(EventError.NOT_FOUND));
    if (!event.details().scheduled()) {
      throw new DomainException(EventError.NOT_SCHEDULED);
    }
    return event;
  }
}
