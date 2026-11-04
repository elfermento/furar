// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingConfirmed;
import gal.furar.booking.NoStaffAccounts;
import gal.furar.booking.StaffAccounts;
import gal.furar.booking.application.port.in.MyBooking;
import gal.furar.booking.application.port.in.ReserveCommand;
import gal.furar.booking.application.port.in.ReserveUseCase;
import gal.furar.booking.application.port.in.SlotTakenException;
import gal.furar.booking.application.port.out.BookingCustomFields;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.application.port.out.IdempotencyKeys;
import gal.furar.booking.domain.BookableUse;
import gal.furar.booking.domain.BookedSlot;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingCode;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.BookingRules;
import gal.furar.booking.domain.Holder;
import gal.furar.booking.domain.RentalMode;
import gal.furar.booking.domain.SlotGrid;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.UseSummary;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import gal.furar.shared.tenancy.TenantContext;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reservar (ADR-0025, punto 5): unha transacción que toma primeiro o bloqueo da instalación e,
 * baixo el, comproba a instalación, o uso, as normas, o horario xa cortado polos bloqueos, a
 * ocupación, a praza da persoa e o límite por persoa; só entón escribe. Dúas peticións ao mesmo oco
 * ou da mesma persoa fanse unha despois da outra; a base segue garantindo a ocupación e o cupo.
 *
 * <p>Idempotente (ADR-0025, punto 19): baixo o mesmo bloqueo mira se a persoa xa reservou con esta
 * {@code Idempotency-Key}. Coa mesma pegada, devolve esa reserva tal como está agora, sen comprobar
 * nin escribir nada; con outra, 422 {@code booking.idempotency-key-reused}. Se non, a clave gárdase
 * coa reserva nova, na mesma transacción. Un intento que falla non garda a clave: o reintento
 * vólvese avaliar enteiro.
 *
 * <p>Os campos propios da reserva (destino {@code BOOKING}, ADR-0011) valídanse antes de escribir,
 * como titular (os de «só o persoal» non os ve nin os escribe), e gárdanse coa reserva na mesma
 * transacción.
 *
 * <p>Unha conta de traballo (pertenza activa ao persoal de calquera concello) non reserva como
 * veciña: 403 {@code booking.staff-account} antes de mirar nada máis (ADR-0027, revisión do
 * 2026-10-30). Pregúntase a {@link StaffAccounts}, que implementa {@code staff}.
 */
@Service
public class ReserveService implements ReserveUseCase {

  private static final String ACTIVE = "ACTIVE";

  /** Intentos de código: con 32⁴ códigos, chocar dúas veces seguidas xa é case imposible. */
  private static final int CODE_ATTEMPTS = 10;

  private static final int MAX_NAME = 200;

  private final FacilityQueries facilities;
  private final BookingRepository bookings;
  private final IdempotencyKeys keys;
  private final BookingCustomFields customFields;
  private final BookingViews views;
  private final SlotGrids grids;
  private final CurrentActor currentActor;
  private final StaffAccounts staffAccounts;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final RandomGenerator random = new SecureRandom();

  public ReserveService(
      FacilityQueries facilities,
      BookingRepository bookings,
      IdempotencyKeys keys,
      BookingCustomFields customFields,
      BookingViews views,
      SlotGrids grids,
      CurrentActor currentActor,
      ObjectProvider<StaffAccounts> staffAccounts,
      ApplicationEventPublisher events,
      Clock clock) {
    this.facilities = facilities;
    this.bookings = bookings;
    this.keys = keys;
    this.customFields = customFields;
    this.views = views;
    this.grids = grids;
    this.currentActor = currentActor;
    // Un só StaffAccounts (ADR-0005): sen ningún, NoStaffAccounts.
    this.staffAccounts = staffAccounts.getIfAvailable(NoStaffAccounts::new);
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional
  @PersonUseCase
  public MyBooking reserve(ReserveCommand command) {
    Holder holder =
        holder(
            currentActor
                .current()
                .orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED)));
    // 0. A conta de traballo non reserva como veciña, en ningún concello.
    if (staffAccounts.isStaffAccount(holder.subject())) {
      throw new DomainException(BookingError.STAFF_ACCOUNT);
    }
    // 1. O bloqueo da instalación, antes de ler nada (ADR-0022, punto 4).
    if (!facilities.lockFacility(command.facilityId())) {
      throw new DomainException(CommonError.NOT_FOUND);
    }
    Instant now = clock.instant();
    // 2. Baixo o bloqueo, a clave: se a persoa xa reservou con ela, esa reserva e nada máis.
    String fingerprint = command.fingerprint();
    Optional<IdempotencyKeys.Remembered> remembered = keys.find(holder.subject(), command.key());
    if (remembered.isPresent()) {
      if (!remembered.get().fingerprint().equals(fingerprint)) {
        throw new DomainException(BookingError.IDEMPOTENCY_KEY_REUSED);
      }
      return views.view(bookings.findById(remembered.get().bookingId()).orElseThrow(), now);
    }
    // 3. Os campos propios da reserva, como os escribe quen reserva.
    CustomFieldValues values = customFields.validateNew(command.customFields());
    // 4. Baixo o bloqueo: instalación, uso, normas, horario, ocupación e límite.
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
    BookingRules rules = use.rules();
    Instant start = command.start();
    rules.checkSlot(start, command.slots(), now);
    Instant end = start.plus(Duration.ofMinutes(use.slotMinutes()).multipliedBy(command.slots()));
    LocalDate date = start.atZone(SlotGrid.ZONE).toLocalDate();
    SlotGrid grid = grids.of(command.facilityId(), date, now);
    if (use.rentalMode() == RentalMode.SEATS
        && bookings.holds(use.id(), holder.subject(), start, end, null)) {
      throw new DomainException(BookingError.SEAT_TAKEN_BY_YOU);
    }
    UUID part;
    try {
      part = grid.allocate(use, start, command.slots(), command.partId()).orElse(null);
    } catch (DomainException e) {
      if (e.errorCode() == BookingError.SLOT_TAKEN) {
        throw new SlotTakenException(grid.alternatives(use, start, command.slots()));
      }
      throw e;
    }
    rules
        .capWindow(start)
        .ifPresent(
            w ->
                rules.checkCap(
                    bookings.countConfirmed(use.id(), holder.subject(), w.from(), w.to(), null)));
    // 5. Só agora escribe: reserva, ocupación, prazas, historia, campos propios e a clave.
    BookedSlot slot =
        new BookedSlot(
            command.facilityId(), use.id(), part, use.rentalMode(), start, end, command.slots());
    for (int attempt = 1; attempt <= CODE_ATTEMPTS; attempt++) {
      Booking booking =
          Booking.create(
              BookingId.newId(),
              BookingCode.random(random),
              slot,
              Uses.unitPrice(summary),
              rules,
              holder,
              now);
      if (bookings.insert(booking, use.capacity(), holder.subject())) {
        if (!values.isEmpty()) {
          customFields.replace(booking.id(), values);
        }
        keys.remember(holder.subject(), command.key(), fingerprint, booking.id(), now);
        events.publishEvent(
            new BookingConfirmed(TenantContext.require().id(), booking.id().value()));
        return views.view(bookings.findById(booking.id()).orElseThrow(), now);
      }
    }
    throw new IllegalStateException("No free booking code after " + CODE_ATTEMPTS + " attempts");
  }

  /**
   * Quen reserva, copiado do token (ADR-0025, punto 3). O correo verificado xa o comprobou {@link
   * PersonUseCase}; sen nome, o correo; sen idioma coñecido, galego.
   */
  private static Holder holder(Actor actor) {
    Actor.Profile profile = actor.profile();
    String email = profile.email();
    if (email == null) {
      throw new DomainException(AuthError.EMAIL_NOT_VERIFIED);
    }
    String name = profile.name() == null ? email : profile.name();
    if (name.length() > MAX_NAME) {
      name = name.substring(0, MAX_NAME);
    }
    String locale = profile.locale() == null ? "" : profile.locale();
    Language language =
        Language.fromCode(locale.toLowerCase(Locale.ROOT).split("[-_]")[0]).orElse(Language.GL);
    return new Holder(actor.subject(), name, email, language);
  }
}
