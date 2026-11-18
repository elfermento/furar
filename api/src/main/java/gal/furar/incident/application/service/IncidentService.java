// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.application.service;

import gal.furar.booking.BookingNotice;
import gal.furar.booking.BookingQueries;
import gal.furar.booking.BookingSummary;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.PhotoSanitizer;
import gal.furar.facility.SanitizedPhoto;
import gal.furar.facility.UseSummary;
import gal.furar.incident.IncidentPermission;
import gal.furar.incident.IncidentReported;
import gal.furar.incident.IncidentResolved;
import gal.furar.incident.application.port.in.DeleteIncidentUseCase;
import gal.furar.incident.application.port.in.GetIncidentUseCase;
import gal.furar.incident.application.port.in.IncidentView;
import gal.furar.incident.application.port.in.IncidentView.LinkedBooking;
import gal.furar.incident.application.port.in.ListIncidentsUseCase;
import gal.furar.incident.application.port.in.PhotoBytes;
import gal.furar.incident.application.port.in.RecordIncidentUseCase;
import gal.furar.incident.application.port.in.ReportIncidentCommand;
import gal.furar.incident.application.port.in.ReportIncidentUseCase;
import gal.furar.incident.application.port.in.ReviewIncidentCommand;
import gal.furar.incident.application.port.in.ReviewIncidentUseCase;
import gal.furar.incident.application.port.out.IncidentRepository;
import gal.furar.incident.application.port.out.IncidentRepository.PhotoContent;
import gal.furar.incident.domain.Incident;
import gal.furar.incident.domain.IncidentError;
import gal.furar.incident.domain.IncidentId;
import gal.furar.incident.domain.IncidentOrigin;
import gal.furar.incident.domain.IncidentPhoto;
import gal.furar.incident.domain.IncidentStatus;
import gal.furar.incident.domain.Reporter;
import gal.furar.notification.IncidentMailer;
import gal.furar.shared.audit.AuditChanges;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.domain.Language;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso das incidencias (ADR-0036). Cada método público declara o seu permiso (A15): a
 * veciñanza avisa con {@link PersonUseCase}; o persoal ve, cambia e anota con {@link
 * IncidentPermission}. Crear, cambiar o estado e resolver quedan no rexistro de actividade co
 * identificador, a instalación, o tipo e os estados: nunca o texto, a nota nin a mensaxe.
 */
@Service
public class IncidentService
    implements ReportIncidentUseCase,
        RecordIncidentUseCase,
        ListIncidentsUseCase,
        GetIncidentUseCase,
        ReviewIncidentUseCase,
        DeleteIncidentUseCase {

  /** Rexistro de actividade: alta (veciñanza ou persoal). */
  public static final String CREATE = "incident.create";

  /** Rexistro de actividade: cambio de estado que non resolve. */
  public static final String STATUS = "incident.status";

  /** Rexistro de actividade: pasa a Resolta. */
  public static final String RESOLVE = "incident.resolve";

  /** Rexistro de actividade: a xestión elimina unha incidencia. */
  public static final String DELETE = "incident.delete";

  /** Rexistro de actividade: a xestión quita unha foto dunha incidencia. */
  public static final String PHOTO_DELETE = "incident.photo-delete";

  /** Avisos como moito por conta e día nun concello: fronte ao abuso, non á veciñanza. */
  static final int MAX_REPORTS_PER_DAY = 3;

  private final IncidentRepository repository;
  private final FacilityQueries facilities;
  private final BookingQueries bookings;
  private final PhotoSanitizer photos;
  private final CurrentActor currentActor;
  private final AuditLog audit;
  private final ApplicationEventPublisher events;
  private final IncidentMailer mails;
  private final Clock clock;

  public IncidentService(
      IncidentRepository repository,
      FacilityQueries facilities,
      BookingQueries bookings,
      PhotoSanitizer photos,
      CurrentActor currentActor,
      AuditLog audit,
      ApplicationEventPublisher events,
      IncidentMailer mails,
      Clock clock) {
    this.repository = repository;
    this.facilities = facilities;
    this.bookings = bookings;
    this.photos = photos;
    this.currentActor = currentActor;
    this.audit = audit;
    this.events = events;
    this.mails = mails;
    this.clock = clock;
  }

  @Override
  @Transactional
  @PersonUseCase
  public IncidentId report(ReportIncidentCommand command) {
    Actor actor = actor();
    Instant now = clock.instant();
    if (facilities.findPublic(command.facilityId()).isEmpty()) {
      throw new DomainException(IncidentError.FACILITY_NOT_FOUND);
    }
    if (command.bookingId() != null) {
      checkBooking(command.bookingId(), command.facilityId(), actor, now);
    }
    if (repository.countReportedSince(actor.subject(), now.minus(Duration.ofDays(1)))
        >= MAX_REPORTS_PER_DAY) {
      throw new DomainException(IncidentError.TOO_MANY_REPORTS, Map.of("max", MAX_REPORTS_PER_DAY));
    }
    Incident saved = save(command, command.bookingId(), IncidentOrigin.RESIDENT, actor, now);
    return saved.id();
  }

  @Override
  @Transactional
  @RequiresPermission(IncidentPermission.Code.MANAGE)
  public IncidentView record(ReportIncidentCommand command) {
    Actor actor = actor();
    FacilitySummary facility =
        facilities
            .find(command.facilityId())
            .orElseThrow(() -> new DomainException(IncidentError.FACILITY_NOT_FOUND));
    Incident saved = save(command, null, IncidentOrigin.STAFF, actor, clock.instant());
    return new IncidentView(saved, facility.name(), null);
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(IncidentPermission.Code.VIEW)
  public List<IncidentView> list() {
    Map<UUID, String> names =
        facilities.all().stream()
            .collect(Collectors.toMap(FacilitySummary::id, FacilitySummary::name, (a, b) -> a));
    return repository.recent(LIMIT).stream()
        .map(i -> new IncidentView(i, names.getOrDefault(i.facilityId(), ""), null))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(IncidentPermission.Code.VIEW)
  public long countNew() {
    return repository.countByStatus(IncidentStatus.NEW);
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(IncidentPermission.Code.VIEW)
  public IncidentView get(IncidentId id) {
    return view(load(id));
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(IncidentPermission.Code.VIEW)
  public PhotoBytes photo(IncidentId id, UUID photoId, boolean large) {
    return repository
        .photo(id, photoId, large)
        .map(p -> new PhotoBytes(p.bytes(), p.contentType()))
        .orElseThrow(() -> new DomainException(IncidentError.PHOTO_NOT_FOUND));
  }

  @Override
  @Transactional
  @RequiresPermission(IncidentPermission.Code.MANAGE)
  public IncidentView review(ReviewIncidentCommand command) {
    Incident current = load(command.id());
    if (current.version() == null || current.version() != command.expectedVersion()) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    Incident changed =
        current.review(
            command.status(), command.internalNote(), command.resolutionMessage(), clock.instant());
    Incident saved = repository.update(changed);
    if (saved.status() != current.status()) {
      TenantRef tenant = TenantContext.require();
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("incidentId", saved.id().value().toString());
      details.put("facilityId", saved.facilityId().toString());
      details.put("from", current.status().name());
      details.put("to", saved.status().name());
      boolean resolves = saved.resolvedSince(current);
      if (resolves) {
        details.put("message", saved.resolutionMessage() != null);
      }
      audit.record(
          new AuditEvent(
              resolves ? RESOLVE : STATUS, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
      if (resolves && saved.canNotifyReporter()) {
        events.publishEvent(new IncidentResolved(tenant.id(), saved.id().value()));
      }
    }
    return view(saved);
  }

  @Override
  @Transactional
  @RequiresPermission(IncidentPermission.Code.DELETE)
  public void delete(IncidentId id) {
    Incident incident = load(id);
    Map<String, Object> details = removal(incident);
    new AuditChanges()
        .value("incident.type", incident.type().name(), null)
        .value("incident.status", incident.status().name(), null)
        .value("incident.reportedAt", madridDay(incident.createdAt()), null)
        .value("incident.photos", incident.photos().size(), null)
        .into(details);
    repository.delete(id);
    // Os envíos levan correos: van coa incidencia.
    mails.forget(List.of(id.value()));
    TenantRef tenant = TenantContext.require();
    audit.record(new AuditEvent(DELETE, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
  }

  @Override
  @Transactional
  @RequiresPermission(IncidentPermission.Code.DELETE)
  public IncidentView deletePhoto(IncidentId id, UUID photoId) {
    Incident incident = load(id);
    if (!repository.deletePhoto(id, photoId)) {
      throw new DomainException(IncidentError.PHOTO_NOT_FOUND);
    }
    Map<String, Object> details = removal(incident);
    details.put("photoId", photoId.toString());
    new AuditChanges()
        .value("incident.photos", incident.photos().size(), incident.photos().size() - 1)
        .into(details);
    TenantRef tenant = TenantContext.require();
    audit.record(
        new AuditEvent(PHOTO_DELETE, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
    return view(load(id));
  }

  /**
   * O que queda no rexistro ao eliminar (ADR-0036): a incidencia, a instalación (co seu nome), o
   * tipo, o estado, o día do aviso e as fotos; nunca o texto, a nota, a mensaxe nin quen avisou.
   * Quen e cando os pon o rexistro.
   */
  private Map<String, Object> removal(Incident incident) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("incidentId", incident.id().value().toString());
    details.put("facilityId", incident.facilityId().toString());
    facilities
        .find(incident.facilityId())
        .ifPresent(facility -> details.put("label", facility.name()));
    details.put("type", incident.type().name());
    details.put("status", incident.status().name());
    details.put("reportedAt", incident.createdAt().toString());
    details.put("photos", incident.photos().size());
    return details;
  }

  private static String madridDay(Instant instant) {
    return instant.atZone(ZoneId.of("Europe/Madrid")).toLocalDate().toString();
  }

  private Incident save(
      ReportIncidentCommand command,
      @Nullable UUID bookingId,
      IncidentOrigin origin,
      Actor actor,
      Instant now) {
    if (command.photos().size() > Incident.MAX_PHOTOS) {
      throw new DomainException(IncidentError.TOO_MANY_PHOTOS, Map.of("max", Incident.MAX_PHOTOS));
    }
    List<PhotoContent> contents = new ArrayList<>();
    for (byte[] upload : command.photos()) {
      SanitizedPhoto photo = photos.sanitize(upload);
      contents.add(
          new PhotoContent(
              new IncidentPhoto(Ids.newV7(), contents.size(), photo.width(), photo.height()),
              photo.large(),
              photo.thumb(),
              photo.contentType()));
    }
    Incident incident =
        Incident.report(
            IncidentId.newId(),
            command.facilityId(),
            bookingId,
            command.type(),
            command.description(),
            origin,
            reporter(actor),
            contents.stream().map(PhotoContent::photo).toList(),
            now);
    Incident saved = repository.insert(incident, contents);
    TenantRef tenant = TenantContext.require();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("incidentId", saved.id().value().toString());
    details.put("facilityId", saved.facilityId().toString());
    details.put("type", saved.type().name());
    details.put("origin", origin.name());
    details.put("photos", saved.photos().size());
    audit.record(new AuditEvent(CREATE, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
    events.publishEvent(new IncidentReported(tenant.id(), saved.id().value()));
    return saved;
  }

  /**
   * A reserva ten que ser desta instalación, da mesma persoa (o correo da reserva é o da conta),
   * sen cancelar e xa empezada: «Avisar dun problema» só está no historial.
   */
  private void checkBooking(UUID bookingId, UUID facilityId, Actor actor, Instant now) {
    BookingSummary booking =
        bookings
            .find(bookingId)
            .filter(b -> b.facilityId().equals(facilityId))
            .filter(b -> b.status() == BookingSummary.Status.CONFIRMED)
            .filter(b -> b.start().isBefore(now))
            .orElseThrow(() -> new DomainException(IncidentError.BOOKING_INVALID));
    String email = actor.profile().email();
    boolean own =
        email != null
            && bookings
                .notice(booking.id())
                .map(BookingNotice::holderEmail)
                .filter(holder -> holder.equalsIgnoreCase(email.strip()))
                .isPresent();
    if (!own) {
      throw new DomainException(IncidentError.BOOKING_INVALID);
    }
  }

  private IncidentView view(Incident incident) {
    String facilityName =
        facilities.find(incident.facilityId()).map(FacilitySummary::name).orElse("");
    LinkedBooking booking = null;
    if (incident.bookingId() != null) {
      booking =
          bookings
              .find(incident.bookingId())
              .map(
                  b ->
                      new LinkedBooking(
                          b.id(),
                          b.code(),
                          b.start(),
                          b.end(),
                          facilities.use(b.useId()).map(UseSummary::name).orElse("")))
              .orElse(null);
    }
    return new IncidentView(incident, facilityName, booking);
  }

  private Incident load(IncidentId id) {
    return repository.findById(id).orElseThrow(() -> new DomainException(IncidentError.NOT_FOUND));
  }

  private Actor actor() {
    return currentActor.current().orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED));
  }

  /** Quen avisa, tal como vén no token; sen idioma coñecido, galego. */
  static Reporter reporter(Actor actor) {
    Actor.Profile profile = actor.profile();
    String locale = profile.locale() == null ? "" : profile.locale();
    Language language =
        Language.fromCode(locale.toLowerCase(Locale.ROOT).split("[-_]")[0]).orElse(Language.GL);
    return new Reporter(actor.subject(), profile.name(), profile.email(), language);
  }
}
