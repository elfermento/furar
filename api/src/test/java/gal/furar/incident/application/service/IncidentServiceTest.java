// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gal.furar.booking.BookingQueries;
import gal.furar.booking.BookingSummary;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.PhotoSanitizer;
import gal.furar.facility.SanitizedPhoto;
import gal.furar.incident.IncidentReported;
import gal.furar.incident.IncidentResolved;
import gal.furar.incident.application.port.in.IncidentView;
import gal.furar.incident.application.port.in.ReportIncidentCommand;
import gal.furar.incident.application.port.in.ReviewIncidentCommand;
import gal.furar.incident.application.port.out.IncidentRepository;
import gal.furar.incident.domain.Incident;
import gal.furar.incident.domain.IncidentError;
import gal.furar.incident.domain.IncidentId;
import gal.furar.incident.domain.IncidentOrigin;
import gal.furar.incident.domain.IncidentPhoto;
import gal.furar.incident.domain.IncidentStatus;
import gal.furar.incident.domain.IncidentType;
import gal.furar.notification.IncidentMailer;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** Casos de uso das incidencias cun repositorio en memoria e os outros módulos simulados. */
class IncidentServiceTest {

  static final Instant NOW = Instant.parse("2026-11-11T10:42:00Z");
  static final UUID MUNICIPALITY = UUID.randomUUID();
  static final UUID FACILITY = UUID.randomUUID();

  final InMemoryIncidents repository = new InMemoryIncidents();
  final FacilityQueries facilities = mock(FacilityQueries.class);
  final BookingQueries bookings = mock(BookingQueries.class);
  final PhotoSanitizer photos = mock(PhotoSanitizer.class);
  final IncidentMailer mails = mock(IncidentMailer.class);
  final List<AuditEvent> audited = new ArrayList<>();
  final AuditLog audit = audited::add;
  final List<Object> published = new ArrayList<>();
  final ApplicationEventPublisher events = published::add;
  Actor actor =
      new Actor(
          "sub-1",
          Set.of(),
          true,
          new Actor.Profile("Persoa Proba", "persoa@example.org", "es-ES"));
  final CurrentActor currentActor = () -> Optional.of(actor);
  IncidentService service;
  TenantContext.Scope scope;

  @BeforeEach
  void setUp() {
    service =
        new IncidentService(
            repository,
            facilities,
            bookings,
            photos,
            currentActor,
            audit,
            events,
            mails,
            Clock.fixed(NOW, ZoneOffset.UTC));
    FacilitySummary facility =
        new FacilitySummary(FACILITY, "Pista de pádel 2", "ACTIVE", true, true, null);
    when(facilities.findPublic(FACILITY)).thenReturn(Optional.of(facility));
    when(facilities.find(FACILITY)).thenReturn(Optional.of(facility));
    when(facilities.all()).thenReturn(List.of(facility));
    when(photos.sanitize(any()))
        .thenReturn(new SanitizedPhoto(new byte[] {1}, new byte[] {2}, "image/jpeg", 40, 30));
    scope = TenantContext.open(new TenantRef(MUNICIPALITY, "naron"));
  }

  @AfterEach
  void tearDown() {
    scope.close();
  }

  ReportIncidentCommand command(UUID booking, int photoCount) {
    List<byte[]> files = new ArrayList<>();
    for (int i = 0; i < photoCount; i++) {
      files.add(new byte[] {(byte) i});
    }
    return new ReportIncidentCommand(
        FACILITY, booking, IncidentType.BREAKDOWN, "A rede está rota", files);
  }

  @Test
  void aResidentReportsWithPhotosAndTheManagementIsTold() {
    IncidentId id = service.report(command(null, 2));

    Incident saved = repository.findById(id).orElseThrow();
    assertThat(saved.status()).isEqualTo(IncidentStatus.NEW);
    assertThat(saved.origin()).isEqualTo(IncidentOrigin.RESIDENT);
    assertThat(saved.reporter().subject()).isEqualTo("sub-1");
    assertThat(saved.reporter().language()).isEqualTo(Language.ES);
    assertThat(saved.photos()).extracting(IncidentPhoto::position).containsExactly(0, 1);
    assertThat(repository.contents).hasSize(2);
    assertThat(published).containsExactly(new IncidentReported(MUNICIPALITY, id.value()));
    assertThat(audited)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.action()).isEqualTo(IncidentService.CREATE);
              assertThat(e.details())
                  .containsEntry("type", "BREAKDOWN")
                  .containsEntry("origin", "RESIDENT")
                  .containsEntry("photos", 2)
                  .doesNotContainKey("description");
            });
  }

  @Test
  void aFacilityThatIsNotPublicCannotBeReported() {
    when(facilities.findPublic(FACILITY)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.report(command(null, 0)))
        .extracting(e -> ((DomainException) e).errorCode())
        .isEqualTo(IncidentError.FACILITY_NOT_FOUND);
  }

  @Test
  void moreThanThreePhotosAreRefused() {
    assertThatThrownBy(() -> service.report(command(null, 4)))
        .extracting(e -> ((DomainException) e).errorCode())
        .isEqualTo(IncidentError.TOO_MANY_PHOTOS);
    assertThat(repository.all()).isEmpty();
  }

  @Test
  void theBookingMustBeOwnPastAndOfTheFacility() {
    UUID booking = UUID.randomUUID();
    when(bookings.find(booking))
        .thenReturn(
            Optional.of(
                new BookingSummary(
                    booking,
                    "FUR-AAAA",
                    FACILITY,
                    UUID.randomUUID(),
                    null,
                    NOW.minusSeconds(7200),
                    NOW.minusSeconds(3600),
                    BookingSummary.Status.CONFIRMED)));
    // Sen aviso (anonimizada ou sen correo): non é súa.
    when(bookings.notice(booking)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.report(command(booking, 0)))
        .extracting(e -> ((DomainException) e).errorCode())
        .isEqualTo(IncidentError.BOOKING_INVALID);

    UUID unknown = UUID.randomUUID();
    when(bookings.find(unknown)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.report(command(unknown, 0)))
        .extracting(e -> ((DomainException) e).errorCode())
        .isEqualTo(IncidentError.BOOKING_INVALID);
  }

  @Test
  void threeReportsADayAtMost() {
    for (int i = 0; i < IncidentService.MAX_REPORTS_PER_DAY; i++) {
      service.report(command(null, 0));
    }
    assertThatThrownBy(() -> service.report(command(null, 0)))
        .extracting(e -> ((DomainException) e).errorCode())
        .isEqualTo(IncidentError.TOO_MANY_REPORTS);
  }

  @Test
  void resolvingAResidentReportAuditsAndTellsThePerson() {
    IncidentId id = service.report(command(null, 0));
    published.clear();
    audited.clear();

    IncidentView progress =
        service.review(
            new ReviewIncidentCommand(id, 0, IncidentStatus.IN_PROGRESS, "Avisado", null));
    assertThat(progress.incident().version()).isEqualTo(1);
    assertThat(audited)
        .singleElement()
        .extracting(AuditEvent::action)
        .isEqualTo(IncidentService.STATUS);
    assertThat(published).isEmpty();

    IncidentView resolved =
        service.review(
            new ReviewIncidentCommand(id, 1, IncidentStatus.RESOLVED, "Avisado", "Cambiada"));
    assertThat(resolved.incident().resolutionMessage()).isEqualTo("Cambiada");
    assertThat(audited)
        .last()
        .satisfies(
            e -> {
              assertThat(e.action()).isEqualTo(IncidentService.RESOLVE);
              assertThat(e.details()).containsEntry("message", true).doesNotContainKey("note");
            });
    assertThat(published).containsExactly(new IncidentResolved(MUNICIPALITY, id.value()));

    // Só a nota: sen rexistro nin correo.
    audited.clear();
    published.clear();
    service.review(new ReviewIncidentCommand(id, 2, IncidentStatus.RESOLVED, "Feito", "Cambiada"));
    assertThat(audited).isEmpty();
    assertThat(published).isEmpty();
  }

  @Test
  void aStaleVersionIsRefused() {
    IncidentId id = service.report(command(null, 0));
    assertThatThrownBy(
            () ->
                service.review(
                    new ReviewIncidentCommand(id, 7, IncidentStatus.IN_PROGRESS, null, null)))
        .extracting(e -> ((DomainException) e).errorCode())
        .isEqualTo(CommonError.STALE_VERSION);
  }

  @Test
  void theStaffRecordsWithoutMailToThemselvesWhenResolved() {
    actor = new Actor("staff-1", Set.of(), true, new Actor.Profile("Recepción", null, "gl"));
    IncidentView view = service.record(command(null, 1));
    assertThat(view.facilityName()).isEqualTo("Pista de pádel 2");
    assertThat(view.incident().origin()).isEqualTo(IncidentOrigin.STAFF);
    published.clear();
    service.review(
        new ReviewIncidentCommand(
            view.incident().id(), 0, IncidentStatus.RESOLVED, null, "Para ninguén"));
    assertThat(published).isEmpty();
  }

  @Test
  void theListCarriesTheFacilityNameAndTheCountOnlyNewOnes() {
    IncidentId first = service.report(command(null, 0));
    service.report(command(null, 0));
    service.review(new ReviewIncidentCommand(first, 0, IncidentStatus.IN_PROGRESS, null, null));
    assertThat(service.list())
        .hasSize(2)
        .allSatisfy(v -> assertThat(v.facilityName()).isEqualTo("Pista de pádel 2"));
    assertThat(service.countNew()).isEqualTo(1);
    verify(facilities).all();
  }

  @Test
  void theManagementDeletesAPhotoOrTheWholeIncidentWithItsMails() {
    IncidentId id = service.report(command(null, 2));
    UUID photo = repository.findById(id).orElseThrow().photos().getFirst().id();
    audited.clear();

    IncidentView left = service.deletePhoto(id, photo);
    assertThat(left.incident().photos()).hasSize(1);
    assertThat(left.incident().version()).isEqualTo(1);
    assertThat(audited)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.action()).isEqualTo(IncidentService.PHOTO_DELETE);
              assertThat(e.details()).containsEntry("photoId", photo.toString());
            });
    assertThatThrownBy(() -> service.deletePhoto(id, photo))
        .extracting(e -> ((DomainException) e).errorCode())
        .isEqualTo(IncidentError.PHOTO_NOT_FOUND);

    service.delete(id);
    assertThat(repository.findById(id)).isEmpty();
    verify(mails).forget(List.of(id.value()));
    assertThat(audited)
        .last()
        .satisfies(
            e -> {
              assertThat(e.action()).isEqualTo(IncidentService.DELETE);
              assertThat(e.details()).containsEntry("photos", 1).doesNotContainKey("description");
            });
    assertThatThrownBy(() -> service.delete(id))
        .extracting(e -> ((DomainException) e).errorCode())
        .isEqualTo(IncidentError.NOT_FOUND);
  }

  /** Repositorio en memoria do concello aberto. */
  static class InMemoryIncidents implements IncidentRepository {

    final Map<IncidentId, Incident> items = new LinkedHashMap<>();
    final List<PhotoContent> contents = new ArrayList<>();

    List<Incident> all() {
      return List.copyOf(items.values());
    }

    @Override
    public Incident insert(Incident incident, List<PhotoContent> photos) {
      Incident saved = incident.withVersion(0);
      items.put(saved.id(), saved);
      contents.addAll(photos);
      return saved;
    }

    @Override
    public Incident update(Incident incident) {
      Incident current = items.get(incident.id());
      if (current == null || !current.version().equals(incident.version())) {
        throw new DomainException(CommonError.STALE_VERSION);
      }
      Incident saved = incident.withVersion(incident.version() + 1);
      items.put(saved.id(), saved);
      return saved;
    }

    @Override
    public Optional<Incident> findById(IncidentId id) {
      return Optional.ofNullable(items.get(id));
    }

    @Override
    public List<Incident> recent(int limit) {
      return all().reversed().stream().limit(limit).toList();
    }

    @Override
    public long countByStatus(IncidentStatus status) {
      return items.values().stream().filter(i -> i.status() == status).count();
    }

    @Override
    public long countReportedSince(String subject, Instant since) {
      return items.values().stream()
          .filter(i -> subject.equals(i.reporter().subject()))
          .filter(i -> !i.createdAt().isBefore(since))
          .count();
    }

    @Override
    public Optional<PhotoData> photo(IncidentId id, UUID photoId, boolean large) {
      return Optional.empty();
    }

    @Override
    public boolean deletePhoto(IncidentId id, UUID photoId) {
      Incident current = items.get(id);
      if (current == null || current.photos().stream().noneMatch(p -> p.id().equals(photoId))) {
        return false;
      }
      items.put(
          id,
          new Incident(
              current.id(),
              current.facilityId(),
              current.bookingId(),
              current.type(),
              current.description(),
              current.status(),
              current.origin(),
              current.reporter(),
              current.internalNote(),
              current.resolutionMessage(),
              current.photos().stream().filter(p -> !p.id().equals(photoId)).toList(),
              current.createdAt(),
              current.updatedAt(),
              current.resolvedAt(),
              current.photosRemovedAt(),
              current.anonymizedAt(),
              current.version() + 1));
      return true;
    }

    @Override
    public boolean delete(IncidentId id) {
      return items.remove(id) != null;
    }
  }
}
