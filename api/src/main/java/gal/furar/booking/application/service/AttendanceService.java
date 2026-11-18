// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingPermission;
import gal.furar.booking.NoStaffAccounts;
import gal.furar.booking.StaffAccounts;
import gal.furar.booking.application.port.in.AttendanceUseCase;
import gal.furar.booking.application.port.in.ManagedBooking;
import gal.furar.booking.application.port.in.NoShowStanding;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.domain.Attendance;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingCode;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingEventKind;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.Holder;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A asistencia dunha reserva (ADR-0032, puntos 3 a 9): validala ao ler o seu código, marcar a falta
 * e desfacer. Todo baixo o bloqueo da fila da reserva ({@code for update}): non toca a ocupación,
 * así que non precisa o da instalación, e dúas lecturas á vez na porta validan unha soa vez.
 *
 * <p>A marxe para validar soa é unha duración do despregue ({@code
 * furar.booking.validation-margin}, 45 minutos) sobre os instantes da franxa: non depende da zona
 * horaria. Fóra dela só se valida con {@code force} e un motivo, que queda no rexistro de
 * actividade tal como se escribiu: é a excepción decidida a «os motivos libres non van nos
 * detalles» (ADR-0020). As lecturas que non validan nada non se rexistran.
 */
@Service
public class AttendanceService implements AttendanceUseCase {

  static final String VALIDATE = "booking.validate";
  static final String NO_SHOW = "booking.no-show";
  static final String UNDO = "booking.attendance-undo";

  /** O motivo de validar fóra da marxe, como moito. */
  static final int REASON_MAX = 200;

  private final BookingRepository bookings;
  private final BookingViews views;
  private final NoShowPenalty penalty;
  private final StaffAccounts staff;
  private final CurrentActor currentActor;
  private final AuditLog audit;
  private final ApplicationEventPublisher events;
  private final Duration margin;

  /** Reloxo; as horas que se gardan van ao microsegundo, coma a base (en Linux dá nanosegundos). */
  private final Clock clock;

  public AttendanceService(
      BookingRepository bookings,
      BookingViews views,
      NoShowPenalty penalty,
      ObjectProvider<StaffAccounts> staff,
      CurrentActor currentActor,
      AuditLog audit,
      ApplicationEventPublisher events,
      @Value("${furar.booking.validation-margin:PT45M}") Duration margin,
      Clock clock) {
    this.bookings = bookings;
    this.views = views;
    this.penalty = penalty;
    // Un só StaffAccounts (ADR-0005): sen ningún, NoStaffAccounts.
    this.staff = staff.getIfAvailable(NoStaffAccounts::new);
    this.currentActor = currentActor;
    this.audit = audit;
    this.events = events;
    this.margin = margin;
    this.clock = clock;
  }

  @Override
  @Transactional
  @RequiresPermission(BookingPermission.Code.ATTENDANCE)
  public Validation validate(String code, boolean force, @Nullable String reason) {
    TenantRef tenant = TenantContext.require();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("forced", force);
    try {
      Validation result = read(code, force, reason, details);
      if (result.outcome() == Outcome.VALIDATED && details.containsKey("bookingId")) {
        audit.record(
            new AuditEvent(
                VALIDATE, AuditEvent.Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
      }
      return result;
    } catch (RuntimeException e) {
      // Un código que non existe non valida nada; un «Validar igualmente» rexeitado, si queda.
      if (force) {
        recordFailure(VALIDATE, tenant, details, e);
      }
      throw e;
    }
  }

  private Validation read(
      String code, boolean force, @Nullable String reason, Map<String, Object> details) {
    String typed = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
    if (!BookingCode.isValid(typed)) {
      throw new DomainException(BookingError.NOT_FOUND);
    }
    Booking before =
        bookings
            .findByCodeLocked(typed)
            .orElseThrow(() -> new DomainException(BookingError.NOT_FOUND));
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    if (before.status().isCancelled()) {
      if (force) {
        details.put("bookingId", before.id().value().toString());
        throw new DomainException(BookingError.NOT_VALIDATABLE);
      }
      return new Validation(Outcome.CANCELLED, views.managed(before, now), null, null);
    }
    if (before.attendance() == Attendance.USED) {
      return new Validation(
          Outcome.ALREADY_VALIDATED,
          views.managed(before, now),
          before.mark().at(),
          name(before.mark().by()));
    }
    boolean inWindow = before.inValidationWindow(now, margin);
    if (!inWindow && !force) {
      return new Validation(Outcome.OUT_OF_WINDOW, views.managed(before, now), null, null);
    }
    details.put("bookingId", before.id().value().toString());
    details.put("facilityId", before.slot().facilityId().toString());
    // Dentro da marxe valídase soa, tamén se chega un «force»: o motivo só fai falta fóra.
    details.put("forced", !inWindow);
    if (!inWindow) {
      String why = reason == null ? "" : reason.strip();
      if (why.isEmpty() || why.length() > REASON_MAX) {
        throw new DomainException(
            BookingError.VALIDATION_REASON_REQUIRED, Map.of("max", REASON_MAX));
      }
      details.put("reason", why);
    }
    String actor = subject();
    Booking after = before.validate(now, actor);
    bookings.markAttendance(before, after, BookingEventKind.VALIDATED, actor);
    // Validar unha reserva marcada como falta quítaa, e con ela o bloqueo que causase.
    if (before.attendance() == Attendance.NO_SHOW && penalty.lift(before.id(), now, actor)) {
      details.put("blockLifted", true);
    }
    return new Validation(
        Outcome.VALIDATED,
        views.managed(bookings.findById(before.id()).orElseThrow(), now),
        now,
        name(actor));
  }

  @Override
  @Transactional
  @RequiresPermission(BookingPermission.Code.ATTENDANCE)
  public ManagedBooking markNoShow(BookingId id, long expectedVersion) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("bookingId", id.value().toString());
    return audited(
        NO_SHOW,
        details,
        () -> {
          Booking before = current(id, expectedVersion);
          Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
          details.put("facilityId", before.slot().facilityId().toString());
          String actor = subject();
          Booking after = before.markNoShow(now, actor);
          if (before.attendance() == Attendance.NO_SHOW) {
            // Xa era unha falta: nin outra fila nin outro correo.
            details.put("blocked", false);
            return views.managed(before, now);
          }
          UUID eventId = bookings.markAttendance(before, after, BookingEventKind.NO_SHOW, actor);
          Optional<NoShowStanding> standing = fault(after, eventId, now);
          details.put("blocked", standing.map(s -> s.blockedUntil() != null).orElse(false));
          return views.managed(bookings.findById(id).orElseThrow(), now);
        });
  }

  /**
   * A falta conta para o correo da reserva e avísase por correo; sen correo, nin unha cousa nin a
   * outra.
   */
  private Optional<NoShowStanding> fault(Booking marked, UUID eventId, Instant now) {
    Holder holder = marked.holder();
    String emailKey = holder == null ? null : holder.emailKey();
    if (emailKey == null) {
      return Optional.empty();
    }
    Optional<NoShowStanding> standing = penalty.afterFault(emailKey, now).standing();
    events.publishEvent(penalty.event(marked.id(), eventId, standing));
    return standing;
  }

  @Override
  @Transactional
  @RequiresPermission(BookingPermission.Code.ATTENDANCE)
  public ManagedBooking undo(BookingId id, long expectedVersion) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("bookingId", id.value().toString());
    return audited(
        UNDO,
        details,
        () -> {
          Booking before = current(id, expectedVersion);
          Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
          details.put("facilityId", before.slot().facilityId().toString());
          String actor = subject();
          Booking after = before.undoAttendance(now, actor);
          details.put("from", before.attendance().name());
          bookings.markAttendance(before, after, BookingEventKind.ATTENDANCE_UNDONE, actor);
          boolean lifted =
              before.attendance() == Attendance.NO_SHOW && penalty.lift(id, now, actor);
          details.put("blockLifted", lifted);
          return views.managed(bookings.findById(id).orElseThrow(), now).withBlockLifted(lifted);
        });
  }

  private Booking current(BookingId id, long expectedVersion) {
    Booking booking =
        bookings.findLocked(id).orElseThrow(() -> new DomainException(BookingError.NOT_FOUND));
    if (!Objects.equals(booking.version(), expectedVersion)) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return booking;
  }

  /** O nome de quen do persoal marcou; nulo se foi a tarefa ou xa non se sabe. */
  private @Nullable String name(@Nullable String subject) {
    if (subject == null) {
      return null;
    }
    StaffAccounts.Member member = staff.members(List.of(subject)).get(subject);
    return member == null ? null : member.name();
  }

  private String subject() {
    return currentActor
        .current()
        .orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED))
        .subject();
  }

  /** Executa a escritura e rexístraa (ADR-0020): o éxito cos detalles; o fallo co código. */
  private ManagedBooking audited(
      String action, Map<String, Object> details, Supplier<ManagedBooking> work) {
    TenantRef tenant = TenantContext.require();
    try {
      ManagedBooking result = work.get();
      audit.record(
          new AuditEvent(action, AuditEvent.Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
      return result;
    } catch (RuntimeException e) {
      recordFailure(action, tenant, details, e);
      throw e;
    }
  }

  /** Se o rexistro do fallo non se pode escribir, mantense o erro orixinal. */
  private void recordFailure(
      String action, TenantRef tenant, Map<String, Object> details, RuntimeException e) {
    Map<String, Object> failure = new LinkedHashMap<>(details);
    // O motivo só queda cando a reserva se valida.
    failure.remove("reason");
    failure.put("code", e instanceof DomainException d ? d.errorCode().code() : "common.internal");
    try {
      audit.record(
          new AuditEvent(action, AuditEvent.Outcome.FAILURE, tenant.id(), tenant.slug(), failure));
    } catch (RuntimeException auditFailure) {
      e.addSuppressed(auditFailure);
    }
  }
}
