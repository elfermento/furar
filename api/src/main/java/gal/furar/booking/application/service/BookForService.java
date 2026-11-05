// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingChanged;
import gal.furar.booking.BookingConfirmed;
import gal.furar.booking.BookingPermission;
import gal.furar.booking.application.port.in.BookForUseCase;
import gal.furar.booking.application.port.in.ChangeBookingByStaffCommand;
import gal.furar.booking.application.port.in.ManagedBooking;
import gal.furar.booking.application.port.in.ReserveForCommand;
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
import gal.furar.booking.domain.PaymentStatus;
import gal.furar.booking.domain.SlotGrid;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.UseSummary;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O persoal reserva no nome doutra persoa e cambia reservas desde a xestión (especificación, bloque
 * 5, decidido o 2026-11-04). Reservar segue a orde de {@link ReserveService} (ADR-0025, punto 5): o
 * bloqueo da instalación, a clave de reintento, os campos propios e, baixo o bloqueo, instalación,
 * uso, normas, horario e ocupación; só entón escribe. Sen conta non hai a quen contar o límite por
 * persoa nin «unha persoa, unha praza». Cambiar segue a {@link ChangeMyBookingService}, sen o prazo
 * da persoa (como cancelar desde a xestión): libera a ocupación vella, avalía o oco novo coma se a
 * vella non existise e escribe na mesma fila; se o oco é o mesmo, só corrixe os datos. Cada
 * escritura deixa fila no rexistro de actividade (ADR-0020), co éxito ou co código do erro, sen
 * datos persoais.
 */
@Service
public class BookForService implements BookForUseCase {

  static final String BOOK_FOR = "booking.book-for";
  static final String CHANGE = "booking.change";

  private static final String ACTIVE = "ACTIVE";

  /** Intentos de código: con 32⁴ códigos, chocar dúas veces seguidas xa é case imposible. */
  private static final int CODE_ATTEMPTS = 10;

  private final FacilityQueries facilities;
  private final BookingRepository bookings;
  private final IdempotencyKeys keys;
  private final BookingCustomFields customFields;
  private final BookingViews views;
  private final SlotGrids grids;
  private final CurrentActor currentActor;
  private final AuditLog audit;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final RandomGenerator random = new SecureRandom();

  public BookForService(
      FacilityQueries facilities,
      BookingRepository bookings,
      IdempotencyKeys keys,
      BookingCustomFields customFields,
      BookingViews views,
      SlotGrids grids,
      CurrentActor currentActor,
      AuditLog audit,
      ApplicationEventPublisher events,
      Clock clock) {
    this.facilities = facilities;
    this.bookings = bookings;
    this.keys = keys;
    this.customFields = customFields;
    this.views = views;
    this.grids = grids;
    this.currentActor = currentActor;
    this.audit = audit;
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional
  @RequiresPermission(BookingPermission.Code.BOOK_FOR)
  public ManagedBooking reserveFor(ReserveForCommand command) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("facilityId", command.facilityId().toString());
    details.put("useId", command.useId().toString());
    return audited(BOOK_FOR, details, () -> reserve(command, details));
  }

  private ManagedBooking reserve(ReserveForCommand command, Map<String, Object> details) {
    String actor = subject();
    // 1. O bloqueo da instalación, antes de ler nada (ADR-0022, punto 4).
    if (!facilities.lockFacility(command.facilityId())) {
      throw new DomainException(CommonError.NOT_FOUND);
    }
    Instant now = clock.instant();
    // 2. Baixo o bloqueo, a clave: se xa se reservou con ela, esa reserva e nada máis.
    String fingerprint = command.fingerprint();
    Optional<IdempotencyKeys.Remembered> remembered = keys.find(actor, command.key());
    if (remembered.isPresent()) {
      if (!remembered.get().fingerprint().equals(fingerprint)) {
        throw new DomainException(BookingError.IDEMPOTENCY_KEY_REUSED);
      }
      details.put("bookingId", remembered.get().bookingId().value().toString());
      return views.managed(bookings.findById(remembered.get().bookingId()).orElseThrow(), now);
    }
    // 3. Os campos propios, como os escribe o persoal (todos).
    CustomFieldValues values =
        customFields.validateByStaff(command.customFields(), CustomFieldValues.empty());
    // 4. Baixo o bloqueo: instalación, uso, normas, horario e ocupación.
    UseSummary summary = bookableUse(command.facilityId(), command.useId());
    BookableUse use = Uses.bookable(summary);
    BookingRules rules = use.rules();
    Instant start = command.start();
    rules.checkSlot(start, command.slots(), now);
    Instant end = end(use, start, command.slots());
    SlotGrid grid = grids.of(command.facilityId(), start.atZone(SlotGrid.ZONE).toLocalDate(), now);
    UUID part = allocate(grid, use, start, command.slots(), command.partId());
    // 5. Só agora escribe: reserva, ocupación, prazas, historia, cobro, campos propios e a clave.
    BookedSlot slot =
        new BookedSlot(
            command.facilityId(), use.id(), part, use.rentalMode(), start, end, command.slots());
    for (int attempt = 1; attempt <= CODE_ATTEMPTS; attempt++) {
      Booking booking =
          Booking.createFor(
              BookingId.newId(),
              BookingCode.random(random),
              slot,
              Uses.unitPrice(summary),
              rules,
              command.holder(),
              actor,
              now);
      if (bookings.insert(booking, use.capacity(), actor)) {
        details.put("bookingId", booking.id().value().toString());
        Booking saved = bookings.findById(booking.id()).orElseThrow();
        if (command.paid() && saved.paymentStatus() == PaymentStatus.PENDING) {
          bookings.changePayment(saved, saved.changePayment(PaymentStatus.PAID, now), actor);
          details.put("payment", PaymentStatus.PAID.name());
        }
        if (!values.isEmpty()) {
          customFields.replace(booking.id(), values);
        }
        keys.remember(actor, command.key(), fingerprint, booking.id(), now);
        events.publishEvent(
            new BookingConfirmed(TenantContext.require().id(), booking.id().value()));
        return views.managed(bookings.findById(booking.id()).orElseThrow(), now);
      }
    }
    throw new IllegalStateException("No free booking code after " + CODE_ATTEMPTS + " attempts");
  }

  @Override
  @Transactional
  @RequiresPermission(BookingPermission.Code.BOOK_FOR)
  public ManagedBooking change(ChangeBookingByStaffCommand command) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("bookingId", command.id().value().toString());
    return audited(CHANGE, details, () -> changeLocked(command, details));
  }

  private ManagedBooking changeLocked(
      ChangeBookingByStaffCommand command, Map<String, Object> details) {
    String actor = subject();
    Booking found = load(command.id());
    if (!facilities.lockFacility(found.slot().facilityId())) {
      throw new DomainException(BookingError.NOT_FOUND);
    }
    Booking before = load(command.id());
    if (!Objects.equals(before.version(), command.expectedVersion())) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    Instant now = clock.instant();
    Booking current = before;
    // Para quen é: só nas que fixo o persoal (as dunha conta son os datos da conta).
    ChangeBookingByStaffCommand.Contact contact = command.holder();
    Holder holder = before.holder();
    if (contact != null && (holder == null || holder.hasAccount())) {
      // Os datos dunha conta son os da conta; unha anonimizada xa non ten persoa.
      throw new DomainException(BookingError.HOLDER_NOT_EDITABLE);
    }
    if (contact != null && holder != null) {
      Holder changed =
          Holder.walkIn(contact.name(), contact.email(), contact.phone(), holder.language());
      if (!changed.equals(holder)) {
        current = current.withHolder(changed, now);
        details.put("holder", true);
      }
    }
    // Os campos propios, sobre os gardados.
    CustomFieldValues values =
        command.customFields() == null
            ? null
            : customFields.validateByStaff(
                command.customFields(), customFields.stored(before.id()));
    boolean moved = !sameSlot(before.slot(), command);
    Booking saved;
    if (moved) {
      current = rescheduled(before, current, command, now, actor);
      saved = bookings.findById(before.id()).orElseThrow();
      details.put("moved", true);
    } else if (current != before) {
      bookings.correct(before, current);
      saved = bookings.findById(before.id()).orElseThrow();
    } else {
      saved = before;
    }
    // O cobro, despois do oco (o prezo novo xa está).
    PaymentStatus target = paymentTarget(saved, command.paid());
    if (target != null) {
      bookings.changePayment(saved, saved.changePayment(target, now), actor);
      details.put("payment", target.name());
      saved = bookings.findById(before.id()).orElseThrow();
    }
    if (values != null) {
      customFields.replace(before.id(), values);
    }
    if (moved) {
      events.publishEvent(
          new BookingChanged(
              TenantContext.require().id(),
              before.id().value(),
              before.slot().start(),
              before.slot().end(),
              Objects.requireNonNull(saved.version(), "version"),
              !command.notifyPerson()));
      details.put("notify", command.notifyPerson());
    }
    return views.managed(saved, now);
  }

  /** Libera a ocupación vella, avalía o oco novo sen ela e garda o cambio na mesma fila. */
  private Booking rescheduled(
      Booking before,
      Booking current,
      ChangeBookingByStaffCommand command,
      Instant now,
      String actor) {
    // A instalación non cambia (P2): así basta un bloqueo.
    UUID facilityId = before.slot().facilityId();
    UseSummary summary =
        facilities
            .use(command.useId())
            .orElseThrow(() -> new DomainException(CommonError.NOT_FOUND));
    if (!summary.facilityId().equals(facilityId)) {
      throw new DomainException(BookingError.CHANGE_OTHER_FACILITY);
    }
    summary = bookableUse(facilityId, command.useId());
    BookableUse use = Uses.bookable(summary);
    BookingRules rules = use.rules();
    Instant start = command.start();
    rules.checkSlot(start, command.slots(), now);
    Instant end = end(use, start, command.slots());
    bookings.release(before);
    SlotGrid grid = grids.of(facilityId, start.atZone(SlotGrid.ZONE).toLocalDate(), now);
    UUID part = allocate(grid, use, start, command.slots(), command.partId());
    Booking after =
        current.rescheduleByMunicipality(
            new BookedSlot(
                facilityId, use.id(), part, use.rentalMode(), start, end, command.slots()),
            Uses.unitPrice(summary),
            rules,
            now);
    bookings.reschedule(before, after, use.capacity(), actor);
    return after;
  }

  /** O uso, se a instalación e o uso se poden reservar agora. */
  private UseSummary bookableUse(UUID facilityId, UUID useId) {
    FacilitySummary facility =
        facilities
            .findPublic(facilityId)
            .orElseThrow(() -> new DomainException(BookingError.FACILITY_NOT_BOOKABLE));
    if (!ACTIVE.equals(facility.status()) || !facility.onlineBooking()) {
      throw new DomainException(BookingError.FACILITY_NOT_BOOKABLE);
    }
    UseSummary summary =
        facilities
            .use(useId)
            .filter(u -> u.facilityId().equals(facilityId))
            .orElseThrow(() -> new DomainException(CommonError.NOT_FOUND));
    if (summary.archived()) {
      throw new DomainException(BookingError.USE_NOT_AVAILABLE);
    }
    return summary;
  }

  private static Instant end(BookableUse use, Instant start, int slots) {
    return start.plus(Duration.ofMinutes(use.slotMinutes()).multipliedBy(slots));
  }

  private static @Nullable UUID allocate(
      SlotGrid grid, BookableUse use, Instant start, int slots, @Nullable UUID partId) {
    try {
      return grid.allocate(use, start, slots, partId).orElse(null);
    } catch (DomainException e) {
      if (e.errorCode() == BookingError.SLOT_TAKEN) {
        throw new SlotTakenException(grid.alternatives(use, start, slots));
      }
      throw e;
    }
  }

  /** O mesmo oco: o mesmo uso, inicio e franxas, e a mesma parte (ou «tanto me ten»). */
  private static boolean sameSlot(BookedSlot slot, ChangeBookingByStaffCommand command) {
    return slot.useId().equals(command.useId())
        && slot.start().equals(command.start())
        && slot.slots() == command.slots()
        && (command.partId() == null || command.partId().equals(slot.partId()));
  }

  /** O cobro que hai que marcar para que quede como se pide; nulo se xa está ou non conta. */
  private static @Nullable PaymentStatus paymentTarget(Booking booking, @Nullable Boolean paid) {
    if (paid == null || booking.status().isCancelled()) {
      return null;
    }
    PaymentStatus now = booking.paymentStatus();
    if (paid && now == PaymentStatus.PENDING) {
      return PaymentStatus.PAID;
    }
    if (!paid && now == PaymentStatus.PAID) {
      return PaymentStatus.PENDING;
    }
    return null;
  }

  private Booking load(BookingId id) {
    return bookings.findById(id).orElseThrow(() -> new DomainException(BookingError.NOT_FOUND));
  }

  private String subject() {
    return currentActor
        .current()
        .orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED))
        .subject();
  }

  /**
   * Executa a escritura e rexístraa (ADR-0020): o éxito cos detalles; o fallo co código. Se o
   * rexistro do fallo non se pode escribir, mantense o erro orixinal. Sen datos persoais.
   */
  private ManagedBooking audited(
      String action, Map<String, Object> details, Supplier<ManagedBooking> work) {
    TenantRef tenant = TenantContext.require();
    try {
      ManagedBooking result = work.get();
      audit.record(new AuditEvent(action, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
      return result;
    } catch (RuntimeException e) {
      Map<String, Object> failure = new LinkedHashMap<>(details);
      failure.put(
          "code", e instanceof DomainException d ? d.errorCode().code() : "common.internal");
      try {
        audit.record(new AuditEvent(action, Outcome.FAILURE, tenant.id(), tenant.slug(), failure));
      } catch (RuntimeException auditFailure) {
        e.addSuppressed(auditFailure);
      }
      throw e;
    }
  }
}
