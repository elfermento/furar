// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.service;

import gal.furar.event.EventCancelled;
import gal.furar.event.EventCreated;
import gal.furar.event.EventMoved;
import gal.furar.event.EventPermission;
import gal.furar.event.application.port.in.EventCommand;
import gal.furar.event.application.port.in.EventView;
import gal.furar.event.application.port.in.ExportEventsUseCase;
import gal.furar.event.application.port.in.ImportEventsUseCase;
import gal.furar.event.application.port.in.ManageEventsUseCase;
import gal.furar.event.application.port.in.PublicEventsUseCase;
import gal.furar.event.application.port.in.RestoreEventCommand;
import gal.furar.event.application.port.out.EventRepository;
import gal.furar.event.application.port.out.RegistrationRepository;
import gal.furar.event.application.port.out.SessionRepository;
import gal.furar.event.domain.BirthYears;
import gal.furar.event.domain.Event;
import gal.furar.event.domain.EventDetails;
import gal.furar.event.domain.EventError;
import gal.furar.event.domain.EventId;
import gal.furar.event.domain.EventKind;
import gal.furar.event.domain.EventPlace;
import gal.furar.event.domain.EventSchedule;
import gal.furar.event.domain.EventSession;
import gal.furar.event.domain.EventText;
import gal.furar.event.domain.PricePeriod;
import gal.furar.facility.FacilityQueries;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.portability.PortabilityPermission;
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Os eventos do concello (ADR-0038) e as actividades con horario (ADR-0041): a xestión e o que ve a
 * veciñanza. Cada método público declara o seu permiso (A15). Cambiar data, hora ou lugar e
 * cancelar avisan por correo a quen estea apuntado, despois do commit ({@link EventMoved}, {@link
 * EventCancelled}). Unha actividade crea as súas sesións ao crearse (nunha instalación, ocupándoa
 * coma unha tempada) e cancelala solta as que aínda non empezaron.
 */
@Service
public class EventService
    implements ManageEventsUseCase, PublicEventsUseCase, ExportEventsUseCase, ImportEventsUseCase {

  public static final String CREATE = "event.create";
  public static final String UPDATE = "event.update";
  public static final String CANCEL = "event.cancel";
  public static final String DELETE = "event.delete";

  private final EventRepository repository;
  private final RegistrationRepository registrations;
  private final EventViews views;
  private final ActivitySessions activity;
  private final SessionRepository sessions;
  private final Waitlist waitlist;
  private final FacilityQueries facilities;
  private final AuditLog audit;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public EventService(
      EventRepository repository,
      RegistrationRepository registrations,
      EventViews views,
      ActivitySessions activity,
      SessionRepository sessions,
      Waitlist waitlist,
      FacilityQueries facilities,
      AuditLog audit,
      ApplicationEventPublisher events,
      Clock clock) {
    this.repository = repository;
    this.registrations = registrations;
    this.views = views;
    this.activity = activity;
    this.sessions = sessions;
    this.waitlist = waitlist;
    this.facilities = facilities;
    this.audit = audit;
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(EventPermission.Code.VIEW)
  public List<EventView> list() {
    List<Event> all =
        repository.findAll().stream()
            .sorted(
                Comparator.comparing((Event e) -> e.details().startsAt())
                    .reversed()
                    .thenComparing(e -> e.id().value()))
            .toList();
    return views.of(all);
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(EventPermission.Code.VIEW)
  public EventView get(EventId id) {
    return views.of(load(id));
  }

  @Override
  @Transactional
  @RequiresPermission(EventPermission.Code.MANAGE)
  public EventView create(EventCommand command) {
    Instant now = clock.instant();
    EventId id = EventId.newId();
    EventDetails details = details(command, now);
    List<EventSession> created = List.of();
    if (details.scheduled()) {
      EventSchedule schedule = Objects.requireNonNull(details.schedule());
      EventCommand.ScheduleCommand asked = Objects.requireNonNull(command.schedule());
      created =
          activity.occupy(
              id,
              details.name().gl(),
              details.place().facilityId(),
              schedule,
              asked.skip() == null ? Set.of() : asked.skip(),
              now);
      details = withSessions(details, created);
    } else if (!details.startsAt().isAfter(now)) {
      throw new DomainException(EventError.STARTS_IN_PAST);
    }
    Event saved = repository.insert(Event.create(id, details, now));
    sessions.insertAll(created);
    TenantRef tenant = TenantContext.require();
    record(
        CREATE,
        saved,
        details.scheduled()
            ? Map.of("kind", EventKind.SCHEDULED.name(), "sessions", created.size())
            : Map.of());
    events.publishEvent(new EventCreated(tenant.id(), saved.id().value()));
    return views.of(saved);
  }

  @Override
  @Transactional
  @RequiresPermission(EventPermission.Code.MANAGE)
  public EventView update(EventId id, long expectedVersion, EventCommand command) {
    lock(id);
    Event current = load(id);
    checkVersion(current, expectedVersion);
    Instant now = clock.instant();
    EventDetails changed =
        current.details().scheduled()
            ? restored(command, current.details().startsAt(), current.details().endsAt())
            : details(command, now);
    if (!current.details().scheduled()) {
      checkFacility(changed);
    }
    Integer capacity = changed.capacity();
    int confirmed =
        registrations
            .counts(List.of(id))
            .getOrDefault(id.value(), RegistrationRepository.Counts.NONE)
            .confirmed();
    if (capacity != null && confirmed > capacity) {
      throw new DomainException(EventError.CAPACITY_BELOW_REGISTERED);
    }
    if (!changed.scheduled() && !changed.startsAt().isAfter(now)) {
      throw new DomainException(EventError.STARTS_IN_PAST);
    }
    Event saved = repository.update(current.update(changed, now));
    boolean moved = !saved.details().scheduled() && changed.movedFrom(current.details());
    record(UPDATE, saved, Map.of("moved", moved));
    if (moved) {
      events.publishEvent(
          new EventMoved(TenantContext.require().id(), saved.id().value(), Ids.newV7()));
    }
    if (saved.details().scheduled()
        && saved.details().place().facilityId() != null
        && !saved.details().name().gl().equals(current.details().name().gl())) {
      activity.rename(saved);
    }
    // Máis prazas, prazas sen límite ou sen aprobación: a lista de espera recibe o aviso agora.
    waitlist.fill(saved, now);
    return views.of(saved);
  }

  @Override
  @Transactional
  @RequiresPermission(EventPermission.Code.MANAGE)
  public EventView cancel(EventId id, long expectedVersion) {
    lock(id);
    Event current = load(id);
    checkVersion(current, expectedVersion);
    Instant now = clock.instant();
    Event saved = repository.update(current.cancel(now));
    int released = 0;
    if (saved.details().scheduled()) {
      released = activity.release(saved, s -> true, now).size();
    }
    record(CANCEL, saved, saved.details().scheduled() ? Map.of("sessions", released) : Map.of());
    events.publishEvent(new EventCancelled(TenantContext.require().id(), saved.id().value()));
    return views.of(saved);
  }

  @Override
  @Transactional
  @RequiresPermission(EventPermission.Code.MANAGE)
  public void delete(EventId id) {
    lock(id);
    Event current = load(id);
    if (registrations.countForEvent(id) > 0) {
      throw new DomainException(EventError.HAS_REGISTRATIONS);
    }
    if (current.details().scheduled()) {
      activity.release(current, s -> true, clock.instant());
    }
    repository.delete(id);
    record(DELETE, current, Map.of());
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public List<EventView> upcoming() {
    return views.of(repository.findVisibleFrom(clock.instant()));
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public EventView published(EventId id) {
    Event event = load(id);
    if (!event.visible()) {
      throw new DomainException(EventError.NOT_FOUND);
    }
    return views.of(event);
  }

  /** Portabilidade (ADR-0018): os eventos, sen as persoas apuntadas. */
  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(PortabilityPermission.Code.EXPORT)
  public List<EventView> exportAll() {
    return views.of(repository.findAll());
  }

  /** Portabilidade (ADR-0018): na transacción da importación do concello. */
  @Override
  @Transactional
  @RequiresPermission(PortabilityPermission.Code.IMPORT)
  public void importAll(List<RestoreEventCommand> rows) {
    for (RestoreEventCommand row : rows) {
      EventCommand c = row.details();
      Instant startsAt = Objects.requireNonNull(c.startsAt(), "startsAt");
      Instant endsAt = row.endsAt() == null ? startsAt : row.endsAt();
      repository.insert(
          Event.restore(
              new EventId(row.id()),
              restored(c, startsAt, endsAt),
              row.cancelledAt(),
              row.endedFrom(),
              row.createdAt(),
              row.updatedAt()));
    }
  }

  /**
   * Os datos do formulario; a instalación ten que ser do concello e non estar de baixa. Nunha
   * actividade, o horario desde hoxe e as datas provisionais (as de verdade saen das sesións).
   */
  private EventDetails details(EventCommand c, Instant now) {
    EventDetails details;
    if (c.kindOrDefault() == EventKind.SCHEDULED) {
      EventSchedule schedule = schedule(c);
      schedule.check(now.atZone(EventSchedule.ZONE).toLocalDate());
      details =
          restored(c, schedule.start(schedule.firstDate()), schedule.end(schedule.lastDate()));
    } else {
      if (c.startsAt() == null) {
        throw new DomainException(EventError.STARTS_IN_PAST);
      }
      details = restored(c, c.startsAt(), c.startsAt());
    }
    checkFacility(details);
    return details;
  }

  private void checkFacility(EventDetails details) {
    UUID facility = details.place().facilityId();
    if (facility != null && facilities.find(facility).filter(f -> !f.withdrawn()).isEmpty()) {
      throw new DomainException(EventError.FACILITY_NOT_FOUND);
    }
  }

  /** O inicio da primeira sesión e o fin da última. */
  private static EventDetails withSessions(EventDetails details, List<EventSession> created) {
    Instant start = created.getFirst().startsAt();
    Instant end = created.getLast().endsAt();
    return new EventDetails(
        details.name(),
        details.description(),
        details.kind(),
        start,
        end,
        details.place(),
        details.schedule(),
        details.capacity(),
        details.priceCents(),
        details.pricePeriod(),
        details.registrationDeadline(),
        details.published(),
        details.approval(),
        details.birthYears());
  }

  private static @Nullable EventSchedule scheduleOrNull(EventCommand c) {
    return c.kindOrDefault() == EventKind.SCHEDULED ? schedule(c) : null;
  }

  private static EventSchedule schedule(EventCommand c) {
    EventCommand.ScheduleCommand s = c.schedule();
    if (s == null) {
      throw new DomainException(EventError.SCHEDULE_INVALID);
    }
    return EventSchedule.of(
        s.useId(),
        s.partId(),
        s.weekdays(),
        s.startMinute(),
        s.endMinute(),
        s.firstDate(),
        s.lastDate());
  }

  private static EventDetails restored(EventCommand c, Instant startsAt, Instant endsAt) {
    PricePeriod period = c.pricePeriod();
    return new EventDetails(
        EventText.name(c.nameGl(), c.nameEs()),
        EventText.description(c.descriptionGl(), c.descriptionEs()),
        c.kindOrDefault(),
        startsAt,
        endsAt,
        new EventPlace(c.facilityId(), c.place()),
        scheduleOrNull(c),
        c.capacity(),
        c.priceCents(),
        period == null ? PricePeriod.ONCE : period,
        c.registrationDeadline(),
        c.published(),
        c.approval(),
        new BirthYears(c.birthYearFrom(), c.birthYearTo()));
  }

  private void lock(EventId id) {
    if (!registrations.lockEvent(id)) {
      throw new DomainException(EventError.NOT_FOUND);
    }
  }

  private Event load(EventId id) {
    return repository.findById(id).orElseThrow(() -> new DomainException(EventError.NOT_FOUND));
  }

  private static void checkVersion(Event current, long expected) {
    if (current.version() == null || current.version() != expected) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
  }

  /** No rexistro de actividade: o evento, sen persoas. */
  private void record(String action, Event event, Map<String, Object> extra) {
    TenantRef tenant = TenantContext.require();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("eventId", event.id().value().toString());
    details.put("name", event.details().name().gl());
    details.put("published", event.details().published());
    details.putAll(extra);
    audit.record(new AuditEvent(action, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
  }
}
