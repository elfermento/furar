// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingCancelled;
import gal.furar.booking.BookingPermission;
import gal.furar.booking.application.port.in.ManageBookingUseCase;
import gal.furar.booking.application.port.in.ManagedBooking;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.PaymentStatus;
import gal.furar.facility.FacilityQueries;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O que fai o persoal cunha reserva (ADR-0025, punto 12; P12, P13): o cobro no mostrador e a
 * cancelación polo concello. Cada escritura comproba a versión do {@code If-Match}, deixa fila en
 * {@code booking_event} e no rexistro de actividade (ADR-0020), co éxito ou co código do erro.
 * Cancelar toma antes o bloqueo da instalación (ADR-0025, punto 5), porque libera a ocupación.
 */
@Service
public class ManageBookingService implements ManageBookingUseCase {

  static final String PAYMENT = "booking.payment";
  static final String CANCEL = "booking.cancel";

  private final FacilityQueries facilities;
  private final BookingRepository bookings;
  private final BookingViews views;
  private final CurrentActor currentActor;
  private final AuditLog audit;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public ManageBookingService(
      FacilityQueries facilities,
      BookingRepository bookings,
      BookingViews views,
      CurrentActor currentActor,
      AuditLog audit,
      ApplicationEventPublisher events,
      Clock clock) {
    this.facilities = facilities;
    this.bookings = bookings;
    this.views = views;
    this.currentActor = currentActor;
    this.audit = audit;
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional
  @RequiresPermission(BookingPermission.Code.PAYMENT)
  public ManagedBooking changePayment(BookingId id, long expectedVersion, PaymentStatus target) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("bookingId", id.value().toString());
    details.put("to", target.name());
    return audited(
        PAYMENT,
        details,
        () -> {
          Booking before = current(load(id), expectedVersion);
          details.put("from", before.paymentStatus().name());
          Instant now = clock.instant();
          Booking after = before.changePayment(target, now);
          bookings.changePayment(before, after, subject());
          return views.managed(bookings.findById(id).orElseThrow(), now);
        });
  }

  @Override
  @Transactional
  @RequiresPermission(BookingPermission.Code.CANCEL)
  public ManagedBooking cancel(
      BookingId id, long expectedVersion, Set<BookingCancelled.Channel> channels) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("bookingId", id.value().toString());
    details.put("channels", channels.stream().map(Enum::name).sorted().toList());
    return audited(
        CANCEL,
        details,
        () -> {
          Booking found = load(id);
          if (!facilities.lockFacility(found.slot().facilityId())) {
            throw new DomainException(BookingError.NOT_FOUND);
          }
          Booking before = current(load(id), expectedVersion);
          Instant now = clock.instant();
          String actor = subject();
          Booking after = before.cancelByMunicipality(now, actor);
          bookings.cancel(before, after, actor, channels);
          events.publishEvent(
              new BookingCancelled(
                  TenantContext.require().id(),
                  id.value(),
                  BookingCancelled.By.MUNICIPALITY,
                  channels));
          details.put("payment", after.paymentStatus().name());
          return views.managed(bookings.findById(id).orElseThrow(), now);
        });
  }

  private Booking load(BookingId id) {
    return bookings.findById(id).orElseThrow(() -> new DomainException(BookingError.NOT_FOUND));
  }

  private static Booking current(Booking booking, long expectedVersion) {
    if (!Objects.equals(booking.version(), expectedVersion)) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return booking;
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
