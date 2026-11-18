// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingCancelled;
import gal.furar.booking.BookingConfirmed;
import gal.furar.booking.NoStaffAccounts;
import gal.furar.booking.StaffAccounts;
import gal.furar.booking.application.port.in.MyBooking;
import gal.furar.booking.application.port.in.MyOrganizationsUseCase;
import gal.furar.booking.application.port.in.ReserveCommand;
import gal.furar.booking.application.port.in.SeriesRequestCommand;
import gal.furar.booking.application.port.in.SeriesRequestView;
import gal.furar.booking.application.port.in.SlotTakenException;
import gal.furar.booking.application.port.out.BookingCustomFields;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.application.port.out.IdempotencyKeys;
import gal.furar.booking.application.port.out.Organizations;
import gal.furar.booking.application.port.out.SeriesRequests;
import gal.furar.booking.domain.BookableUse;
import gal.furar.booking.domain.BookedSlot;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingCode;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.BookingRules;
import gal.furar.booking.domain.Holder;
import gal.furar.booking.domain.NoShowRule;
import gal.furar.booking.domain.Participants;
import gal.furar.booking.domain.RentalMode;
import gal.furar.booking.domain.SlotGrid;
import gal.furar.booking.domain.season.Organization;
import gal.furar.booking.domain.season.SeriesRequest;
import gal.furar.booking.domain.season.SeriesSchedule;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.UseSummary;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.EmailKey;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import gal.furar.shared.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A persoa responsable dunha entidade (ADR-0039, punto 10): ve as reservas da entidade, cancélaas,
 * reserva no seu nome e solicita tempadas. É responsable quen entra cun correo verificado que está
 * entre os responsables dunha entidade dada de alta do concello; compróbase en cada caso de uso.
 * Unha conta de traballo non actúa como entidade.
 *
 * <p>Reservar como entidade é o fluxo de {@link ReserveService} coas súas diferenzas: despois do
 * bloqueo da instalación, a entidade {@code for share} (que ninguén a arquive mentres tanto); o
 * titular é a entidade; o límite do uso cóntase para a entidade (as sesións non contan); sen
 * persoas nin usos por prazas; e o bloqueo por faltas que mira é o da persoa responsable.
 */
@Service
public class OrganizationBookingsService implements MyOrganizationsUseCase {

  private static final String ACTIVE = "ACTIVE";
  private static final int CODE_ATTEMPTS = 10;

  private final FacilityQueries facilities;
  private final BookingRepository bookings;
  private final Organizations organizations;
  private final SeriesRequests requests;
  private final IdempotencyKeys keys;
  private final BookingCustomFields customFields;
  private final BookingViews views;
  private final SlotGrids grids;
  private final NoShowPenalty penalty;
  private final WaitNotifier waitNotifier;
  private final SeasonSupport support;
  private final CurrentActor currentActor;
  private final StaffAccounts staffAccounts;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final RandomGenerator random = new SecureRandom();

  public OrganizationBookingsService(
      FacilityQueries facilities,
      BookingRepository bookings,
      Organizations organizations,
      SeriesRequests requests,
      IdempotencyKeys keys,
      BookingCustomFields customFields,
      BookingViews views,
      SlotGrids grids,
      NoShowPenalty penalty,
      WaitNotifier waitNotifier,
      SeasonSupport support,
      CurrentActor currentActor,
      ObjectProvider<StaffAccounts> staffAccounts,
      ApplicationEventPublisher events,
      Clock clock) {
    this.facilities = facilities;
    this.bookings = bookings;
    this.organizations = organizations;
    this.requests = requests;
    this.keys = keys;
    this.customFields = customFields;
    this.views = views;
    this.grids = grids;
    this.penalty = penalty;
    this.waitNotifier = waitNotifier;
    this.support = support;
    this.currentActor = currentActor;
    // Un só StaffAccounts (ADR-0005): sen ningún, NoStaffAccounts.
    this.staffAccounts = staffAccounts.getIfAvailable(NoStaffAccounts::new);
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public List<MyOrganization> mine() {
    Person person = person();
    if (staffAccounts.isStaffAccount(person.subject())) {
      return List.of();
    }
    return organizations.ofManager(person.emailKey()).stream()
        .map(o -> new MyOrganization(o.id(), o.name()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public PageResult<MyBooking> bookings(UUID organizationId, boolean upcoming, int page, int size) {
    managed(organizationId);
    Instant now = clock.instant();
    PageResult<Booking> result = bookings.ofOrganization(organizationId, upcoming, now, page, size);
    return new PageResult<>(
        views.views(result.items(), now), result.page(), result.size(), result.totalItems());
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public MyBooking booking(UUID organizationId, BookingId id) {
    managed(organizationId);
    return views.view(
        bookings
            .findOfOrganization(id, organizationId)
            .orElseThrow(() -> new DomainException(BookingError.NOT_FOUND)),
        clock.instant());
  }

  @Override
  @Transactional
  @PersonUseCase
  public MyBooking cancel(UUID organizationId, BookingId id, long expectedVersion) {
    Person person = managed(organizationId);
    Booking found =
        bookings
            .findOfOrganization(id, organizationId)
            .orElseThrow(() -> new DomainException(BookingError.NOT_FOUND));
    // Instalación → reserva (ADR-0022, punto 4).
    if (!facilities.lockFacility(found.slot().facilityId())) {
      throw new DomainException(BookingError.NOT_FOUND);
    }
    Booking before =
        bookings
            .findOfOrganization(id, organizationId)
            .orElseThrow(() -> new DomainException(BookingError.NOT_FOUND));
    if (before.version() == null || before.version() != expectedVersion) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    Instant now = clock.instant();
    // Unha sesión cancélase ata que empeza (o seu prazo é o inicio); unha extra, coas súas normas.
    Booking after = before.cancelByPerson(now, person.subject());
    bookings.cancel(before, after, person.subject());
    waitNotifier.released(
        before.slot().facilityId(), List.of(WaitNotifier.occupied(before)), List.of());
    events.publishEvent(BookingCancelled.byPerson(TenantContext.require().id(), id.value()));
    return views.view(bookings.findById(id).orElseThrow(), now);
  }

  @Override
  @Transactional
  @PersonUseCase
  public MyBooking reserve(UUID organizationId, ReserveCommand command) {
    Person person = person();
    if (staffAccounts.isStaffAccount(person.subject())) {
      throw new DomainException(BookingError.STAFF_ACCOUNT);
    }
    // 1. O bloqueo da instalación, antes de ler nada; despois a entidade, para que non se arquive.
    if (!facilities.lockFacility(command.facilityId())) {
      throw new DomainException(CommonError.NOT_FOUND);
    }
    Organization organization =
        organizations
            .findForShare(organizationId)
            .filter(o -> !o.isArchived() && o.isManager(person.emailKey()))
            .orElseThrow(() -> new DomainException(BookingError.ORGANIZATION_NOT_FOUND));
    Instant now = clock.instant();
    // 2. A clave: a mesma petición como entidade ten outra pegada ca como persoa.
    String fingerprint = fingerprint(command, organizationId);
    Optional<IdempotencyKeys.Remembered> remembered = keys.find(person.subject(), command.key());
    if (remembered.isPresent()) {
      if (!remembered.get().fingerprint().equals(fingerprint)) {
        throw new DomainException(BookingError.IDEMPOTENCY_KEY_REUSED);
      }
      return views.view(bookings.findById(remembered.get().bookingId()).orElseThrow(), now);
    }
    // Bloqueada por faltas a persoa responsable: tampouco reserva no nome da entidade.
    refuseIfBlocked(person.emailKey(), now);
    CustomFieldValues values = customFields.validateNew(command.customFields());
    FacilitySummary facility =
        facilities
            .findPublic(command.facilityId())
            .orElseThrow(() -> new DomainException(CommonError.NOT_FOUND));
    if (!ACTIVE.equals(facility.status()) || !facility.onlineBooking()) {
      throw new DomainException(BookingError.FACILITY_NOT_BOOKABLE);
    }
    UseSummary summary =
        facilities
            .use(command.useId())
            .filter(u -> u.facilityId().equals(command.facilityId()))
            .orElseThrow(() -> new DomainException(CommonError.NOT_FOUND));
    if (summary.archived()) {
      throw new DomainException(BookingError.USE_NOT_AVAILABLE);
    }
    BookableUse use = Uses.bookable(summary);
    if (use.rentalMode() == RentalMode.SEATS) {
      throw new DomainException(BookingError.ORGANIZATION_SEATS_USE);
    }
    if (!command.participants().isDefault()) {
      throw new DomainException(BookingError.PARTICIPANTS_NOT_ALLOWED);
    }
    BookingRules rules = use.rules();
    Instant start = command.start();
    rules.checkSlot(start, command.slots(), now);
    Instant end = start.plus(Duration.ofMinutes(use.slotMinutes()).multipliedBy(command.slots()));
    LocalDate date = start.atZone(SlotGrid.ZONE).toLocalDate();
    SlotGrid grid = grids.of(command.facilityId(), date, now);
    UUID part;
    try {
      part = grid.allocate(use, start, command.slots(), command.partId(), 1).orElse(null);
    } catch (DomainException e) {
      if (e.errorCode() == BookingError.SLOT_TAKEN) {
        throw new SlotTakenException(grid.alternatives(use, start, command.slots()));
      }
      throw e;
    }
    // O límite do uso, para a entidade coma se fose unha persoa; as sesións non contan.
    rules
        .capWindow(start)
        .ifPresent(
            w ->
                rules.checkCap(
                    bookings.countConfirmedOfOrganization(
                        use.id(), organizationId, w.from(), w.to())));
    BookedSlot slot =
        new BookedSlot(
            command.facilityId(), use.id(), part, use.rentalMode(), start, end, command.slots());
    Holder holder = Booking.organizationHolder(organization.name(), support.language());
    for (int attempt = 1; attempt <= CODE_ATTEMPTS; attempt++) {
      Booking booking =
          Booking.createForOrganization(
              BookingId.newId(),
              BookingCode.random(random),
              slot,
              Uses.unitPrice(summary),
              rules,
              holder,
              organizationId,
              person.subject(),
              now);
      if (bookings.insert(booking, use.capacity(), person.subject(), Participants.none())) {
        if (!values.isEmpty()) {
          customFields.replace(booking.id(), values);
        }
        keys.remember(person.subject(), command.key(), fingerprint, booking.id(), now);
        events.publishEvent(
            new BookingConfirmed(TenantContext.require().id(), booking.id().value()));
        return views.view(bookings.findById(booking.id()).orElseThrow(), now);
      }
    }
    throw new IllegalStateException("No free booking code after " + CODE_ATTEMPTS + " attempts");
  }

  @Override
  @Transactional
  @PersonUseCase
  public SeriesRequestView request(UUID organizationId, SeriesRequestCommand command) {
    Person person = person();
    if (staffAccounts.isStaffAccount(person.subject())) {
      throw new DomainException(BookingError.STAFF_ACCOUNT);
    }
    // Entidade → solicitude: a entidade bloqueada para contar as pendentes sen carreiras.
    Organization organization =
        organizations
            .findForUpdate(organizationId)
            .filter(o -> !o.isArchived() && o.isManager(person.emailKey()))
            .orElseThrow(() -> new DomainException(BookingError.ORGANIZATION_NOT_FOUND));
    Instant now = clock.instant();
    SeriesSchedule schedule = command.schedule();
    schedule.check(now.atZone(SeriesSchedule.ZONE).toLocalDate());
    UseSummary use = support.use(schedule);
    if (requests.countPending(organization.id()) >= Organization.MAX_PENDING_REQUESTS) {
      throw new DomainException(
          BookingError.SERIES_REQUEST_LIMIT, Map.of("max", Organization.MAX_PENDING_REQUESTS));
    }
    SeriesRequest created =
        SeriesRequest.create(
            UUID.randomUUID(),
            organization.id(),
            schedule,
            RentalMode.valueOf(use.rentalMode()),
            command.note(),
            person.subject(),
            person.email(),
            now);
    requests.insert(created);
    return support.requestViews(List.of(requests.find(created.id()).orElseThrow())).getFirst();
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public List<SeriesRequestView> requests(UUID organizationId) {
    managed(organizationId);
    return support.requestViews(requests.ofOrganization(organizationId));
  }

  @Override
  @Transactional
  @PersonUseCase
  public void withdraw(UUID organizationId, UUID requestId) {
    managed(organizationId);
    SeriesRequest found =
        requests
            .findForUpdate(requestId)
            .filter(r -> r.organizationId().equals(organizationId))
            .orElseThrow(() -> new DomainException(BookingError.SERIES_REQUEST_NOT_FOUND));
    found.requirePending();
    requests.delete(requestId);
  }

  /**
   * A persoa, se é responsable desa entidade dada de alta; se non, a entidade non existe para ela.
   */
  private Person managed(UUID organizationId) {
    Person person = person();
    if (staffAccounts.isStaffAccount(person.subject())) {
      throw new DomainException(BookingError.STAFF_ACCOUNT);
    }
    organizations
        .find(organizationId)
        .filter(o -> !o.isArchived() && o.isManager(person.emailKey()))
        .orElseThrow(() -> new DomainException(BookingError.ORGANIZATION_NOT_FOUND));
    return person;
  }

  private void refuseIfBlocked(String emailKey, Instant now) {
    penalty
        .blocked(emailKey, now)
        .ifPresent(
            block -> {
              throw new DomainException(
                  BookingError.BLOCKED_NO_SHOW,
                  Map.of(
                      "until", NoShowRule.lastBlockedDay(block.endsAt()).toString(),
                      "faults", block.faults(),
                      "windowDays", penalty.rule().windowDays()));
            });
  }

  /** A pegada da persoa máis a entidade: a mesma clave como persoa e como entidade non vale. */
  private static String fingerprint(ReserveCommand command, UUID organizationId) {
    String canonical = command.fingerprint() + "|o:" + organizationId;
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is mandatory in every JDK", e);
    }
  }

  /** Quen entra: a conta e o correo verificado (xa o comprobou {@link PersonUseCase}). */
  private record Person(String subject, String email, String emailKey) {}

  private Person person() {
    Actor actor =
        currentActor.current().orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED));
    String email = actor.profile().email();
    if (email == null) {
      throw new DomainException(AuthError.EMAIL_NOT_VERIFIED);
    }
    return new Person(actor.subject(), email, EmailKey.of(email));
  }
}
