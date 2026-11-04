// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import gal.furar.facility.AffectedBooking;
import gal.furar.facility.BookingDecision;
import gal.furar.facility.FacilityChange;
import gal.furar.facility.OpeningInterval;
import gal.furar.facility.ProgrammableBookingImpact;
import gal.furar.facility.application.port.in.AffectedBookingsException;
import gal.furar.facility.application.port.in.schedule.CreateExceptionCommand;
import gal.furar.facility.application.port.in.schedule.CreateSeasonCommand;
import gal.furar.facility.application.port.in.schedule.DeleteExceptionCommand;
import gal.furar.facility.application.port.in.schedule.ScheduleView;
import gal.furar.facility.application.port.in.schedule.UpdateExceptionCommand;
import gal.furar.facility.application.port.in.schedule.UpdateSeasonCommand;
import gal.furar.facility.application.service.schedule.ScheduleExceptionService;
import gal.furar.facility.application.service.schedule.ScheduleService;
import gal.furar.facility.domain.Address;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.DayHoursSpec;
import gal.furar.facility.domain.schedule.ExceptionSpec;
import gal.furar.facility.domain.schedule.PublicHoliday;
import gal.furar.facility.domain.schedule.ScheduleDay;
import gal.furar.facility.domain.schedule.ScheduleError;
import gal.furar.facility.domain.schedule.ScheduleException;
import gal.furar.facility.domain.schedule.ScheduleExceptionId;
import gal.furar.facility.domain.schedule.Season;
import gal.furar.facility.domain.schedule.Season.DateSpan;
import gal.furar.facility.domain.schedule.SeasonSpec;
import gal.furar.facility.domain.schedule.TimeRange;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.ErrorCode;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Casos de uso das excepcións con repositorios en memoria e un dobre de BookingImpact: bloqueo
 * primeiro, data repetida, horas perdidas (P6), rexistro e a vista do horario (hoxe, agora, días
 * sen tempada).
 */
class ScheduleExceptionServiceTest {

  final InMemoryFacilityRepository facilities = new InMemoryFacilityRepository();
  final InMemorySeasonRepository seasons = new InMemorySeasonRepository(facilities.calls);
  final InMemoryScheduleExceptionRepository exceptions =
      new InMemoryScheduleExceptionRepository(facilities.calls);
  final ProgrammableBookingImpact impact = new ProgrammableBookingImpact();
  final List<AuditEvent> audit = new ArrayList<>();
  // 9:05 UTC do 25 de outubro de 2026: en Europe/Madrid, 10:05 (xa en horario de inverno).
  final Clock clock = Clock.fixed(Instant.parse("2026-10-25T09:05:00Z"), ZoneOffset.UTC);
  final ScheduleExceptionService service =
      new ScheduleExceptionService(
          seasons, seasons, exceptions, facilities, impact, audit::add, clock);
  final ScheduleService schedule =
      new ScheduleService(
          facilities, seasons, seasons, exceptions, facilities, impact, audit::add, clock);
  final TenantRef naron = new TenantRef(UUID.randomUUID(), "naron");
  TenantContext.Scope scope;
  FacilityId facility;

  @BeforeEach
  void enter() {
    scope = TenantContext.open(naron);
    facility =
        facilities
            .insert(
                Facility.create(
                    FacilityId.newId(),
                    "Pavillón de O Alto",
                    new Address(
                        "Rúa do Alto",
                        null,
                        "15570",
                        "Narón",
                        "15054",
                        new BigDecimal("43.5"),
                        new BigDecimal("-8.2")),
                    clock.instant()))
            .id();
    // Inverno: de luns a sábado 8:00–22:00, domingo e festivos 9:00–14:00.
    Season winter =
        schedule.create(
            new CreateSeasonCommand(
                facility,
                new SeasonSpec(
                    "Inverno", LocalDate.parse("2026-09-01"), LocalDate.parse("2027-06-30")),
                null));
    List<DayHoursSpec> week = new ArrayList<>();
    for (ScheduleDay day : ScheduleDay.values()) {
      boolean sunday = day == ScheduleDay.SUNDAY || day == ScheduleDay.HOLIDAYS;
      week.add(
          new DayHoursSpec(
              day, List.of(sunday ? new TimeRange(540, 840) : new TimeRange(480, 1320))));
    }
    schedule.update(
        new UpdateSeasonCommand(
            facility,
            winter.id(),
            0,
            new SeasonSpec("Inverno", winter.startDate(), winter.endDate()),
            week,
            List.of()));
    facilities.calls.clear();
    audit.clear();
  }

  @AfterEach
  void leave() {
    scope.close();
  }

  static ExceptionSpec open(String date, int start, int end) {
    return new ExceptionSpec(LocalDate.parse(date), true, start, end, null);
  }

  static ExceptionSpec closed(String date) {
    return new ExceptionSpec(LocalDate.parse(date), false, null, null, "Peche");
  }

  ScheduleException create(ExceptionSpec spec, BookingDecision... decisions) {
    return service.create(new CreateExceptionCommand(facility, spec, List.of(decisions)));
  }

  ScheduleException update(ScheduleException e, ExceptionSpec spec, BookingDecision... decisions) {
    return service.update(
        new UpdateExceptionCommand(facility, e.id(), e.version(), spec, List.of(decisions)));
  }

  static AffectedBooking booking(UUID id) {
    return new AffectedBooking(
        id, "Uxía Pérez", true, LocalDate.of(2026, 12, 24), 480, 570, "Fútbol sala");
  }

  static void assertCode(Runnable call, ErrorCode code) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
  }

  /** Os días cos tramos perdidos que leva o cambio. */
  static List<LocalDate> days(FacilityChange change) {
    assertThat(change).isInstanceOf(FacilityChange.ScheduleChanged.class);
    return ((FacilityChange.ScheduleChanged) change)
        .lost().stream().map(OpeningInterval::date).distinct().toList();
  }

  static List<OpeningInterval> lostOn(FacilityChange change, String date) {
    return ((FacilityChange.ScheduleChanged) change)
        .lost().stream().filter(i -> i.date().equals(LocalDate.parse(date))).toList();
  }

  @Test
  void createTakesTheFacilityLockFirstAndAudits() {
    ScheduleException created = create(open("2026-12-24", 540, 840));
    assertThat(created.version()).isZero();
    assertThat(facilities.calls).containsExactly("lock", "insert-exception");
    assertThat(audit)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.action()).isEqualTo("facility.exception-create");
              assertThat(e.outcome()).isEqualTo(AuditEvent.Outcome.SUCCESS);
              assertThat(e.details())
                  .containsEntry("exceptionId", created.id().value().toString())
                  .containsEntry("date", "2026-12-24");
            });
  }

  @Test
  void aRepeatedDateSaysWhichExceptionHasIt() {
    ScheduleException first = create(closed("2026-12-31"));
    assertThatThrownBy(() -> create(open("2026-12-31", 600, 720)))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(ScheduleError.EXCEPTION_DATE_TAKEN);
              assertThat(e.params())
                  .containsEntry("exceptionId", first.id().value().toString())
                  .containsEntry("date", "2026-12-31");
            });
    assertThat(audit.getLast().outcome()).isEqualTo(AuditEvent.Outcome.FAILURE);
    assertThat(audit.getLast().details()).containsEntry("code", "facility.exception-date-taken");

    ScheduleException other = create(closed("2026-12-30"));
    assertCode(() -> update(other, closed("2026-12-31")), ScheduleError.EXCEPTION_DATE_TAKEN);
    // A mesma excepción pode gardarse na súa data.
    assertThat(update(first, open("2026-12-31", 600, 720)).version()).isEqualTo(1);
  }

  @Test
  void closingADayWithHoursAsksForTheBookingsOfThatDay() {
    UUID bookingId = UUID.randomUUID();
    impact.affect(facility.value(), List.of(booking(bookingId)));
    assertThatThrownBy(() -> create(closed("2026-12-24")))
        .isInstanceOf(AffectedBookingsException.class);
    assertThat(exceptions.findByFacility(facility)).isEmpty();

    BookingDecision cancel =
        new BookingDecision(
            bookingId, BookingDecision.Action.CANCEL, Set.of(BookingDecision.Channel.EMAIL));
    create(closed("2026-12-24"), cancel);
    assertThat(impact.appliedTo(facility.value()))
        .singleElement()
        .satisfies(
            a -> {
              assertThat(days(a.change())).containsExactly(LocalDate.parse("2026-12-24"));
              assertThat(a.decisions()).containsExactly(cancel);
            });
  }

  @Test
  void openingMoreThanTheWeekDoesNotAskForBookings() {
    impact.affect(facility.value(), List.of(booking(UUID.randomUUID())));
    // Mércores 8:00–22:00 → 7:00–24:00: só gaña horas.
    ScheduleException longer = create(open("2026-12-23", 420, 1440));
    // Un día sen tempada (agosto de 2027): antes estaba pechado.
    create(open("2027-08-10", 600, 720));
    assertThat(impact.appliedTo(facility.value())).isEmpty();

    // Acurtala ata a semana quita as horas que ela engadira.
    assertThatThrownBy(() -> update(longer, open("2026-12-23", 480, 1320)))
        .isInstanceOf(AffectedBookingsException.class);
    impact.affect(facility.value(), List.of());
    ScheduleException shorter = update(longer, open("2026-12-23", 480, 1320));
    assertThat(shorter.version()).isEqualTo(1);
  }

  @Test
  void movingAnExceptionChecksBothDays() {
    // Domingo 27 de decembro aberto de 8:00 a 22:00 (máis ca o domingo).
    ScheduleException sunday = create(open("2026-12-27", 480, 1320));
    UUID bookingId = UUID.randomUUID();
    impact.affect(facility.value(), List.of(booking(bookingId)));
    // Movela ao luns 28 pechado: o 27 volve a 9:00–14:00 e o 28 pecha.
    assertThatThrownBy(() -> update(sunday, closed("2026-12-28")))
        .isInstanceOf(AffectedBookingsException.class);
    BookingDecision keep = new BookingDecision(bookingId, BookingDecision.Action.KEEP, Set.of());
    ScheduleException moved = update(sunday, closed("2026-12-28"), keep);
    assertThat(moved.date()).isEqualTo(LocalDate.parse("2026-12-28"));
    assertThat(impact.appliedTo(facility.value()))
        .singleElement()
        .satisfies(
            a -> {
              assertThat(days(a.change()))
                  .containsExactly(LocalDate.parse("2026-12-27"), LocalDate.parse("2026-12-28"));
              // O 27 só perde o que a excepción abría de máis: 8:00–9:00 e 14:00–22:00.
              assertThat(lostOn(a.change(), "2026-12-27"))
                  .extracting(OpeningInterval::startMinute, OpeningInterval::endMinute)
                  .containsExactly(tuple(480, 540), tuple(840, 1320));
            });
  }

  @Test
  void deletingAnExceptionThatOpenedMoreAsksForBookings() {
    ScheduleException closedDay = create(closed("2026-12-31"));
    ScheduleException longer = create(open("2026-12-30", 420, 1440));
    UUID bookingId = UUID.randomUUID();
    impact.affect(facility.value(), List.of(booking(bookingId)));

    // Borrar un peche só devolve horas.
    service.delete(new DeleteExceptionCommand(facility, closedDay.id(), 0, List.of()));
    assertThat(impact.appliedTo(facility.value())).isEmpty();

    assertThatThrownBy(
            () -> service.delete(new DeleteExceptionCommand(facility, longer.id(), 0, List.of())))
        .isInstanceOf(AffectedBookingsException.class);
    BookingDecision keep = new BookingDecision(bookingId, BookingDecision.Action.KEEP, Set.of());
    service.delete(new DeleteExceptionCommand(facility, longer.id(), 0, List.of(keep)));
    assertThat(exceptions.findByFacility(facility)).isEmpty();
    assertThat(impact.appliedTo(facility.value()))
        .singleElement()
        .satisfies(
            a -> assertThat(days(a.change())).containsExactly(LocalDate.parse("2026-12-30")));
    assertThat(audit.getLast().action()).isEqualTo("facility.exception-delete");
  }

  @Test
  void aHolidayUsesItsRowToDecideLostHours() {
    seasons.holidays.add(new PublicHoliday(LocalDate.parse("2026-12-25"), "Nadal", "Navidad", "x"));
    impact.affect(facility.value(), List.of(booking(UUID.randomUUID())));
    // Venres festivo: a fila de festivos é 9:00–14:00, así que 9:00–15:00 non quita nada.
    create(open("2026-12-25", 540, 900));
    assertThat(impact.appliedTo(facility.value())).isEmpty();
  }

  @Test
  void writesNeedTheVersionAndTheFacility() {
    ScheduleException created = create(closed("2026-12-31"));
    assertCode(
        () ->
            service.update(
                new UpdateExceptionCommand(
                    facility, created.id(), 4, closed("2026-12-31"), List.of())),
        CommonError.STALE_VERSION);
    assertCode(
        () ->
            service.delete(
                new DeleteExceptionCommand(facility, ScheduleExceptionId.newId(), 0, List.of())),
        ScheduleError.EXCEPTION_NOT_FOUND);
    assertCode(
        () ->
            service.create(
                new CreateExceptionCommand(FacilityId.newId(), closed("2026-12-31"), List.of())),
        FacilityError.NOT_FOUND);
    assertCode(
        () -> service.exception(facility, ScheduleExceptionId.newId()),
        ScheduleError.EXCEPTION_NOT_FOUND);
    assertThat(service.exception(facility, created.id()).reason()).isEqualTo("Peche");
  }

  @Test
  void scheduleShowsExceptionsTodayNowAndGaps() {
    create(closed("2026-12-31"));
    create(open("2026-12-24", 540, 840));
    ScheduleView view = schedule.schedule(facility, null);
    assertThat(view.exceptions())
        .extracting(ScheduleException::date)
        .containsExactly(LocalDate.parse("2026-12-24"), LocalDate.parse("2026-12-31"));
    assertThat(view.today()).isEqualTo(LocalDate.parse("2026-10-25"));
    assertThat(view.nowMinute()).isEqualTo(10 * 60 + 5);
    // O inverno remata o 30 de xuño de 2027; os 365 días contando hoxe chegan ao 24 de outubro.
    assertThat(view.gaps())
        .containsExactly(
            new DateSpan(LocalDate.parse("2027-07-01"), LocalDate.parse("2027-10-24")));
  }
}
