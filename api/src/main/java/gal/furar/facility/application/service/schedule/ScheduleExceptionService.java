// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service.schedule;

import gal.furar.facility.BookingDecision;
import gal.furar.facility.BookingImpact;
import gal.furar.facility.FacilityChange;
import gal.furar.facility.FacilityPermission;
import gal.furar.facility.NoBookingImpact;
import gal.furar.facility.application.port.in.schedule.CreateExceptionCommand;
import gal.furar.facility.application.port.in.schedule.DeleteExceptionCommand;
import gal.furar.facility.application.port.in.schedule.ManageExceptionsUseCase;
import gal.furar.facility.application.port.in.schedule.UpdateExceptionCommand;
import gal.furar.facility.application.port.out.FacilityLock;
import gal.furar.facility.application.port.out.schedule.PublicHolidayRepository;
import gal.furar.facility.application.port.out.schedule.ScheduleExceptionRepository;
import gal.furar.facility.application.port.out.schedule.SeasonRepository;
import gal.furar.facility.application.service.AffectedBookingsGate;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.OpeningHours;
import gal.furar.facility.domain.schedule.OpeningHours.Slot;
import gal.furar.facility.domain.schedule.PublicHoliday;
import gal.furar.facility.domain.schedule.ScheduleError;
import gal.furar.facility.domain.schedule.ScheduleException;
import gal.furar.facility.domain.schedule.ScheduleExceptionId;
import gal.furar.facility.domain.schedule.TimeRange;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso das excepcións do horario (D1-15b). Cada escritura toma primeiro o bloqueo da
 * instalación (ADR-0022, punto 4): por iso a data repetida compróbase aquí, coa excepción que xa a
 * usa, antes de que o diga a base. Un cambio que quita horas a algún día (unha excepción que pecha
 * ou acurta, movela a outra data, borrar unha que abría máis ca a semana) pregunta a {@link
 * BookingImpact} polas reservas afectadas nese día ou días e abre o modal se hai (P6). Cada
 * escritura queda no rexistro de actividade (ADR-0020).
 */
@Service
public class ScheduleExceptionService implements ManageExceptionsUseCase {

  private static final ZoneId ZONE = OpeningHours.ZONE;
  static final String CREATE = "facility.exception-create";
  static final String UPDATE = "facility.exception-update";
  static final String DELETE = "facility.exception-delete";

  private final SeasonRepository seasons;
  private final PublicHolidayRepository holidays;
  private final ScheduleExceptionRepository exceptions;
  private final FacilityLock lock;
  private final AffectedBookingsGate gate;
  private final AuditLog audit;
  private final Clock clock;

  /** Un só {@link BookingImpact} (ADR-0005): sen ningún, {@link NoBookingImpact}. */
  @Autowired
  public ScheduleExceptionService(
      SeasonRepository seasons,
      PublicHolidayRepository holidays,
      ScheduleExceptionRepository exceptions,
      FacilityLock lock,
      ObjectProvider<BookingImpact> bookingImpact,
      AuditLog audit,
      Clock clock) {
    this(
        seasons,
        holidays,
        exceptions,
        lock,
        bookingImpact.getIfAvailable(NoBookingImpact::new),
        audit,
        clock);
  }

  public ScheduleExceptionService(
      SeasonRepository seasons,
      PublicHolidayRepository holidays,
      ScheduleExceptionRepository exceptions,
      FacilityLock lock,
      BookingImpact impact,
      AuditLog audit,
      Clock clock) {
    this.seasons = seasons;
    this.holidays = holidays;
    this.exceptions = exceptions;
    this.lock = lock;
    this.gate = new AffectedBookingsGate(impact);
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(FacilityPermission.Code.VIEW)
  public ScheduleException exception(FacilityId facilityId, ScheduleExceptionId exceptionId) {
    return exceptions
        .findById(facilityId, exceptionId)
        .orElseThrow(() -> new DomainException(ScheduleError.EXCEPTION_NOT_FOUND));
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public ScheduleException create(CreateExceptionCommand command) {
    return audited(
        CREATE,
        command.facilityId(),
        null,
        () -> {
          lockFacility(command.facilityId());
          ScheduleException exception =
              ScheduleException.create(
                  ScheduleExceptionId.newId(),
                  command.facilityId(),
                  command.spec(),
                  clock.instant());
          refuseTakenDate(exception);
          List<Slot> lost =
              OpeningHours.lost(
                  exception.date(),
                  base(exception.facilityId(), exception.date()),
                  exception.hours(),
                  ZONE);
          return guarded(
              exception.facilityId(),
              lost,
              command.decisions(),
              () -> exceptions.insert(exception));
        });
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public ScheduleException update(UpdateExceptionCommand command) {
    return audited(
        UPDATE,
        command.facilityId(),
        command.exceptionId(),
        () -> {
          lockFacility(command.facilityId());
          ScheduleException current =
              current(command.facilityId(), command.exceptionId(), command.expectedVersion());
          ScheduleException changed = current.update(command.spec(), clock.instant());
          refuseTakenDate(changed);
          List<Slot> lost = new ArrayList<>();
          if (changed.date().equals(current.date())) {
            lost.addAll(OpeningHours.lost(current.date(), current.hours(), changed.hours(), ZONE));
          } else {
            // A data vella volve ao horario da tempada; a nova colle o da excepción. Só eses dous
            // días, nunca os do medio.
            lost.addAll(
                OpeningHours.lost(
                    current.date(),
                    current.hours(),
                    base(current.facilityId(), current.date()),
                    ZONE));
            lost.addAll(
                OpeningHours.lost(
                    changed.date(),
                    base(changed.facilityId(), changed.date()),
                    changed.hours(),
                    ZONE));
          }
          return guarded(
              changed.facilityId(), lost, command.decisions(), () -> exceptions.update(changed));
        });
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public void delete(DeleteExceptionCommand command) {
    audited(
        DELETE,
        command.facilityId(),
        command.exceptionId(),
        () -> {
          lockFacility(command.facilityId());
          ScheduleException current =
              current(command.facilityId(), command.exceptionId(), command.expectedVersion());
          List<Slot> lost =
              OpeningHours.lost(
                  current.date(),
                  current.hours(),
                  base(current.facilityId(), current.date()),
                  ZONE);
          return guarded(
              current.facilityId(),
              lost,
              command.decisions(),
              () -> {
                exceptions.delete(current);
                return current;
              });
        });
  }

  /**
   * Se o cambio quita horas, pregunta polas reservas que pisan eses tramos exactos (un só cambio
   * para o modal), garda e aplica as decisións.
   */
  private ScheduleException guarded(
      FacilityId facilityId,
      List<Slot> lost,
      List<BookingDecision> decisions,
      Supplier<ScheduleException> write) {
    FacilityChange.@Nullable ScheduleChanged change = ScheduleChanges.of(facilityId, lost);
    if (change == null) {
      return write.get();
    }
    List<BookingDecision> accepted = gate.check(change, decisions);
    ScheduleException saved = write.get();
    gate.apply(change, accepted);
    return saved;
  }

  /** Os tramos dese día sen excepción: festivo e despois semana da tempada que o cobre. */
  private List<TimeRange> base(FacilityId facilityId, LocalDate date) {
    Set<LocalDate> holidayDates =
        holidays.findBetween(date, date).stream()
            .map(PublicHoliday::date)
            .collect(Collectors.toUnmodifiableSet());
    return new OpeningHours(seasons.findByFacility(facilityId), List.of(), holidayDates)
        .withoutException(date);
  }

  /** Xa baixo o bloqueo: ningunha outra escritura do horario pode colarse antes do gardado. */
  private void refuseTakenDate(ScheduleException exception) {
    for (ScheduleException other :
        exceptions.findBetween(exception.facilityId(), exception.date(), exception.date())) {
      if (!other.id().equals(exception.id())) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("exceptionId", other.id().value().toString());
        params.put("date", other.date().toString());
        throw new DomainException(ScheduleError.EXCEPTION_DATE_TAKEN, params);
      }
    }
  }

  private void lockFacility(FacilityId id) {
    if (!lock.lock(id)) {
      throw new DomainException(FacilityError.NOT_FOUND);
    }
  }

  private ScheduleException current(
      FacilityId facilityId, ScheduleExceptionId id, long expectedVersion) {
    ScheduleException current =
        exceptions
            .findById(facilityId, id)
            .orElseThrow(() -> new DomainException(ScheduleError.EXCEPTION_NOT_FOUND));
    if (current.version() == null || current.version() != expectedVersion) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return current;
  }

  /**
   * Executa a escritura e rexístraa (ADR-0020): o éxito coa instalación e a excepción; o fallo co
   * código. Se o rexistro do fallo non se pode escribir, mantense o erro orixinal.
   */
  private ScheduleException audited(
      String action,
      FacilityId facilityId,
      @Nullable ScheduleExceptionId exceptionId,
      Supplier<ScheduleException> work) {
    TenantRef tenant = TenantContext.require();
    try {
      ScheduleException exception = work.get();
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("facilityId", facilityId.value().toString());
      details.put("exceptionId", exception.id().value().toString());
      details.put("date", exception.date().toString());
      audit.record(new AuditEvent(action, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
      return exception;
    } catch (RuntimeException e) {
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("facilityId", facilityId.value().toString());
      if (exceptionId != null) {
        details.put("exceptionId", exceptionId.value().toString());
      }
      details.put(
          "code", e instanceof DomainException d ? d.errorCode().code() : "common.internal");
      try {
        audit.record(new AuditEvent(action, Outcome.FAILURE, tenant.id(), tenant.slug(), details));
      } catch (RuntimeException auditFailure) {
        e.addSuppressed(auditFailure);
      }
      throw e;
    }
  }
}
