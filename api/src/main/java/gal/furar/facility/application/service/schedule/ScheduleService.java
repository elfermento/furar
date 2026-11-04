// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service.schedule;

import gal.furar.facility.BookingDecision;
import gal.furar.facility.BookingImpact;
import gal.furar.facility.FacilityChange;
import gal.furar.facility.FacilityPermission;
import gal.furar.facility.NoBookingImpact;
import gal.furar.facility.application.port.in.schedule.CreateSeasonCommand;
import gal.furar.facility.application.port.in.schedule.DeleteSeasonCommand;
import gal.furar.facility.application.port.in.schedule.ManageSeasonsUseCase;
import gal.furar.facility.application.port.in.schedule.ReadScheduleUseCase;
import gal.furar.facility.application.port.in.schedule.ScheduleView;
import gal.furar.facility.application.port.in.schedule.UpdateSeasonCommand;
import gal.furar.facility.application.port.out.FacilityLock;
import gal.furar.facility.application.port.out.FacilityRepository;
import gal.furar.facility.application.port.out.schedule.PublicHolidayRepository;
import gal.furar.facility.application.port.out.schedule.ScheduleExceptionRepository;
import gal.furar.facility.application.port.out.schedule.SeasonRepository;
import gal.furar.facility.application.service.AffectedBookingsGate;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.OpeningHours;
import gal.furar.facility.domain.schedule.PublicHoliday;
import gal.furar.facility.domain.schedule.ScheduleError;
import gal.furar.facility.domain.schedule.ScheduleException;
import gal.furar.facility.domain.schedule.Season;
import gal.furar.facility.domain.schedule.SeasonId;
import gal.furar.facility.domain.schedule.WeekHours;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.ValidationError;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
 * Lectura do horario e casos de uso das tempadas e a súa semana (D1-15a, D1-15b). Cada escritura
 * toma primeiro o bloqueo da instalación (ADR-0022, punto 4), que serializa todas as escrituras do
 * horario: por iso o solape de tempadas compróbase aquí, co nome da que choca, antes de que o diga
 * a exclusión da base. Un cambio que pode quitar horas (editar a semana, encoller as datas, borrar)
 * pregunta a {@link BookingImpact} polas reservas afectadas e abre o modal se hai (P6). Cada
 * escritura queda no rexistro de actividade (ADR-0020).
 */
@Service
public class ScheduleService implements ReadScheduleUseCase, ManageSeasonsUseCase {

  /** O horario é de reloxo de parede en Europe/Madrid (ADR-0022, punto 8). */
  public static final ZoneId ZONE = OpeningHours.ZONE;

  static final String CREATE = "facility.season-create";
  static final String UPDATE = "facility.season-update";
  static final String DELETE = "facility.season-delete";

  /** Os días sen tempada míranse de hoxe en diante, 365 contando hoxe (plan §2.2). */
  public static final int GAP_DAYS = 365;

  /**
   * Ata onde se buscan reservas afectadas nunha tempada sen fin: dez anos desde hoxe, o mesmo
   * máximo ca a duración dunha tempada con fin ({@link Season#YEARS_MAX}).
   */
  static final int OPEN_END_YEARS = Season.YEARS_MAX;

  private final FacilityRepository facilities;
  private final SeasonRepository seasons;
  private final PublicHolidayRepository holidays;
  private final ScheduleExceptionRepository exceptions;
  private final FacilityLock lock;
  private final AffectedBookingsGate gate;
  private final AuditLog audit;
  private final Clock clock;

  /** Un só {@link BookingImpact} (ADR-0005): sen ningún, {@link NoBookingImpact}. */
  @Autowired
  public ScheduleService(
      FacilityRepository facilities,
      SeasonRepository seasons,
      PublicHolidayRepository holidays,
      ScheduleExceptionRepository exceptions,
      FacilityLock lock,
      ObjectProvider<BookingImpact> bookingImpact,
      AuditLog audit,
      Clock clock) {
    this(
        facilities,
        seasons,
        holidays,
        exceptions,
        lock,
        bookingImpact.getIfAvailable(NoBookingImpact::new),
        audit,
        clock);
  }

  public ScheduleService(
      FacilityRepository facilities,
      SeasonRepository seasons,
      PublicHolidayRepository holidays,
      ScheduleExceptionRepository exceptions,
      FacilityLock lock,
      BookingImpact impact,
      AuditLog audit,
      Clock clock) {
    this.facilities = facilities;
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
  public ScheduleView schedule(FacilityId facilityId, @Nullable Integer year) {
    if (facilities.findById(facilityId).isEmpty()) {
      throw new DomainException(FacilityError.NOT_FOUND);
    }
    ZonedDateTime now = ZonedDateTime.now(clock.withZone(ZONE));
    LocalDate today = now.toLocalDate();
    int shown = year == null ? today.getYear() : PublicHolidayService.checkedYear(year);
    List<Season> list = seasons.findByFacility(facilityId);
    SeasonId current =
        list.stream().filter(s -> s.covers(today)).map(Season::id).findFirst().orElse(null);
    LocalTime time = now.toLocalTime();
    return new ScheduleView(
        list,
        current,
        shown,
        holidays.findBetween(LocalDate.of(shown, 1, 1), LocalDate.of(shown, 12, 31)),
        exceptions.findByFacility(facilityId),
        today,
        time.getHour() * 60 + time.getMinute(),
        new OpeningHours(list, List.of(), Set.of()).gaps(today, today.plusDays(GAP_DAYS - 1L)));
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(FacilityPermission.Code.VIEW)
  public Season season(FacilityId facilityId, SeasonId seasonId) {
    return seasons
        .findById(facilityId, seasonId)
        .orElseThrow(() -> new DomainException(ScheduleError.SEASON_NOT_FOUND));
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public Season create(CreateSeasonCommand command) {
    return audited(
        CREATE,
        command.facilityId(),
        null,
        () -> {
          lockFacility(command.facilityId());
          Season source = null;
          SeasonId copyFrom = command.copyFrom();
          if (copyFrom != null) {
            source =
                seasons
                    .findById(command.facilityId(), copyFrom)
                    .orElseThrow(
                        () ->
                            new DomainException(
                                ScheduleError.SEASON_INVALID,
                                Map.of(),
                                List.of(FieldViolation.of("copyFrom", ValidationError.INVALID))));
          }
          Season season =
              Season.create(
                  SeasonId.newId(), command.facilityId(), command.spec(), source, clock.instant());
          refuseOverlap(season);
          // Unha tempada nova só engade horas a días que antes estaban pechados (sen tempada):
          // non afecta a ningunha reserva.
          return seasons.insert(season);
        });
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public Season update(UpdateSeasonCommand command) {
    return audited(
        UPDATE,
        command.facilityId(),
        command.seasonId(),
        () -> {
          lockFacility(command.facilityId());
          Season current =
              current(command.facilityId(), command.seasonId(), command.expectedVersion());
          Season changed =
              current.update(command.spec(), WeekHours.of(command.week()), clock.instant());
          refuseOverlap(changed);
          // Só se pode perder algo nos días que cubría a tempada.
          FacilityChange.@Nullable ScheduleChanged change =
              lost(current, changed, current.startDate(), current.lastDay(horizon()));
          List<BookingDecision> accepted =
              change == null ? List.of() : gate.check(change, command.decisions());
          Season saved = seasons.update(changed);
          if (change != null) {
            gate.apply(change, accepted);
          }
          return saved;
        });
  }

  @Override
  @Transactional
  @RequiresPermission(FacilityPermission.Code.MANAGE)
  public void delete(DeleteSeasonCommand command) {
    audited(
        DELETE,
        command.facilityId(),
        command.seasonId(),
        () -> {
          lockFacility(command.facilityId());
          Season current =
              current(command.facilityId(), command.seasonId(), command.expectedVersion());
          FacilityChange.@Nullable ScheduleChanged change =
              lost(current, null, current.startDate(), current.lastDay(horizon()));
          List<BookingDecision> accepted =
              change == null ? List.of() : gate.check(change, command.decisions());
          seasons.delete(current);
          if (change != null) {
            gate.apply(change, accepted);
          }
          return current;
        });
  }

  /**
   * Os tramos que se perden entre as dúas datas ao cambiar {@code before} por {@code after} (nulo:
   * borrala), coas excepcións e os festivos do rango: unha excepción manda sobre a tempada, así que
   * os seus días non perden nada.
   */
  private FacilityChange.@Nullable ScheduleChanged lost(
      Season before, @Nullable Season after, LocalDate from, LocalDate to) {
    List<Season> all = seasons.findByFacility(before.facilityId());
    List<Season> changed = new ArrayList<>();
    for (Season season : all) {
      if (!season.id().equals(before.id())) {
        changed.add(season);
      }
    }
    if (after != null) {
      changed.add(after);
    }
    List<ScheduleException> days = exceptions.findBetween(before.facilityId(), from, to);
    Set<LocalDate> holidayDates =
        holidays.findBetween(from, to).stream()
            .map(PublicHoliday::date)
            .collect(Collectors.toUnmodifiableSet());
    OpeningHours old = new OpeningHours(all, days, holidayDates);
    return ScheduleChanges.of(
        before.facilityId(),
        new OpeningHours(changed, days, holidayDates).lostSince(old, from, to, ZONE));
  }

  /**
   * Xa baixo o bloqueo: ningunha outra escritura do horario pode colarse entre isto e o gardado.
   */
  private void refuseOverlap(Season season) {
    for (Season other : seasons.findByFacility(season.facilityId())) {
      if (season.overlaps(other)) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("seasonId", other.id().value().toString());
        params.put("name", other.name());
        params.put("startDate", other.startDate().toString());
        if (other.endDate() != null) {
          params.put("endDate", other.endDate().toString());
        }
        throw new DomainException(ScheduleError.SEASON_OVERLAP, params);
      }
    }
  }

  /** Último día que se mira nunha tempada sen fin. */
  private LocalDate horizon() {
    return LocalDate.now(clock.withZone(ZONE)).plusYears(OPEN_END_YEARS);
  }

  private void lockFacility(FacilityId id) {
    if (!lock.lock(id)) {
      throw new DomainException(FacilityError.NOT_FOUND);
    }
  }

  private Season current(FacilityId facilityId, SeasonId seasonId, long expectedVersion) {
    Season current =
        seasons
            .findById(facilityId, seasonId)
            .orElseThrow(() -> new DomainException(ScheduleError.SEASON_NOT_FOUND));
    if (current.version() == null || current.version() != expectedVersion) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return current;
  }

  /**
   * Executa a escritura e rexístraa (ADR-0020): o éxito coa instalación e a tempada; o fallo co
   * código. Se o rexistro do fallo non se pode escribir, mantense o erro orixinal.
   */
  private Season audited(
      String action, FacilityId facilityId, @Nullable SeasonId seasonId, Supplier<Season> work) {
    TenantRef tenant = TenantContext.require();
    try {
      Season season = work.get();
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("facilityId", facilityId.value().toString());
      details.put("seasonId", season.id().value().toString());
      audit.record(new AuditEvent(action, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
      return season;
    } catch (RuntimeException e) {
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("facilityId", facilityId.value().toString());
      if (seasonId != null) {
        details.put("seasonId", seasonId.value().toString());
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
