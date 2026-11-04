// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.facility.AffectedBooking;
import gal.furar.facility.BookingDecision;
import gal.furar.facility.FacilityChange;
import gal.furar.facility.OpeningInterval;
import gal.furar.facility.ProgrammableBookingImpact;
import gal.furar.facility.application.port.in.AffectedBookingsException;
import gal.furar.facility.application.port.in.schedule.CreateSeasonCommand;
import gal.furar.facility.application.port.in.schedule.DeleteSeasonCommand;
import gal.furar.facility.application.port.in.schedule.ScheduleView;
import gal.furar.facility.application.port.in.schedule.UpdateSeasonCommand;
import gal.furar.facility.application.service.schedule.PublicHolidayService;
import gal.furar.facility.application.service.schedule.ScheduleService;
import gal.furar.facility.domain.Address;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.DayHoursSpec;
import gal.furar.facility.domain.schedule.PublicHoliday;
import gal.furar.facility.domain.schedule.ScheduleDay;
import gal.furar.facility.domain.schedule.ScheduleError;
import gal.furar.facility.domain.schedule.Season;
import gal.furar.facility.domain.schedule.SeasonId;
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

/** Casos de uso das tempadas con repositorios en memoria e un dobre de BookingImpact. */
class ScheduleServiceTest {

  final InMemoryFacilityRepository facilities = new InMemoryFacilityRepository();
  final InMemorySeasonRepository seasons = new InMemorySeasonRepository(facilities.calls);
  final InMemoryScheduleExceptionRepository exceptions =
      new InMemoryScheduleExceptionRepository(facilities.calls);
  final ProgrammableBookingImpact impact = new ProgrammableBookingImpact();
  final List<AuditEvent> audit = new ArrayList<>();
  // 23:30 UTC do 31 de decembro: en Europe/Madrid xa é 1 de xaneiro de 2027.
  final Clock clock = Clock.fixed(Instant.parse("2026-12-31T23:30:00Z"), ZoneOffset.UTC);
  final ScheduleService service =
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
    facilities.calls.clear();
  }

  @AfterEach
  void leave() {
    scope.close();
  }

  static SeasonSpec spec(String name, String from, String to) {
    return new SeasonSpec(name, LocalDate.parse(from), to == null ? null : LocalDate.parse(to));
  }

  static List<DayHoursSpec> week(int start, int end) {
    return List.of(
        new DayHoursSpec(ScheduleDay.MONDAY, List.of(new TimeRange(start, end))),
        new DayHoursSpec(ScheduleDay.SUNDAY, List.of(new TimeRange(600, 840))));
  }

  Season create(String name, String from, String to) {
    return service.create(new CreateSeasonCommand(facility, spec(name, from, to), null));
  }

  Season update(Season s, List<DayHoursSpec> week, List<BookingDecision> decisions) {
    return service.update(
        new UpdateSeasonCommand(
            facility,
            s.id(),
            s.version(),
            spec(
                s.name(),
                s.startDate().toString(),
                s.endDate() == null ? null : s.endDate().toString()),
            week,
            decisions));
  }

  static void assertCode(Runnable call, ErrorCode code) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
  }

  static AffectedBooking booking(UUID id) {
    return new AffectedBooking(
        id, "Uxía Pérez", true, LocalDate.of(2027, 1, 4), 480, 570, "Fútbol sala");
  }

  @Test
  void createTakesTheFacilityLockFirstAndAudits() {
    Season created = create("Inverno", "2026-09-01", "2027-06-30");
    assertThat(created.version()).isZero();
    assertThat(facilities.calls).containsExactly("lock", "insert-season");
    assertThat(audit)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.action()).isEqualTo("facility.season-create");
              assertThat(e.outcome()).isEqualTo(AuditEvent.Outcome.SUCCESS);
              assertThat(e.details()).containsEntry("seasonId", created.id().value().toString());
            });
  }

  @Test
  void overlappingSeasonSaysWhichOneClashes() {
    Season winter = create("Inverno", "2026-09-01", "2027-06-30");
    assertThatThrownBy(() -> create("Verán", "2027-06-30", "2027-08-31"))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(ScheduleError.SEASON_OVERLAP);
              assertThat(e.params())
                  .containsEntry("seasonId", winter.id().value().toString())
                  .containsEntry("name", "Inverno")
                  .containsEntry("startDate", "2026-09-01")
                  .containsEntry("endDate", "2027-06-30");
            });
    assertThat(audit.getLast().outcome()).isEqualTo(AuditEvent.Outcome.FAILURE);
    assertThat(audit.getLast().details()).containsEntry("code", "facility.season-overlap");
    create("Verán", "2027-07-01", "2027-08-31");
  }

  @Test
  void copyFromTakesTheOtherWeekAndMustBeOfTheSameFacility() {
    Season winter =
        update(create("Inverno", "2026-09-01", "2027-06-30"), week(480, 1380), List.of());
    Season summer =
        service.create(
            new CreateSeasonCommand(
                facility, spec("Verán", "2027-07-01", "2027-08-31"), winter.id()));
    assertThat(summer.week().hours(ScheduleDay.MONDAY)).containsExactly(new TimeRange(480, 1380));
    assertThat(summer.week().hours(ScheduleDay.HOLIDAYS)).containsExactly(new TimeRange(600, 840));
    assertCode(
        () ->
            service.create(
                new CreateSeasonCommand(
                    facility, spec("Outono", "2027-09-01", "2027-09-30"), SeasonId.newId())),
        ScheduleError.SEASON_INVALID);
  }

  @Test
  void updateNeedsTheVersionAndReplacesTheWeek() {
    Season created = create("Inverno", "2026-09-01", "2027-06-30");
    Season updated = update(created, week(480, 840), List.of());
    assertThat(updated.version()).isEqualTo(1);
    assertThat(updated.week().hours(ScheduleDay.MONDAY)).containsExactly(new TimeRange(480, 840));
    assertCode(() -> update(created, week(480, 900), List.of()), CommonError.STALE_VERSION);
    assertCode(
        () ->
            service.update(
                new UpdateSeasonCommand(
                    facility,
                    SeasonId.newId(),
                    0,
                    spec("X", "2027-09-01", "2027-09-30"),
                    List.of(),
                    List.of())),
        ScheduleError.SEASON_NOT_FOUND);
  }

  @Test
  void changingTheWeekOpensTheAffectedBookingsDialog() {
    Season created =
        update(create("Inverno", "2026-09-01", "2027-06-30"), week(480, 1380), List.of());
    UUID bookingId = UUID.randomUUID();
    impact.affect(facility.value(), List.of(booking(bookingId)));

    assertThatThrownBy(() -> update(created, week(600, 1380), List.of()))
        .isInstanceOf(AffectedBookingsException.class);
    BookingDecision keep = new BookingDecision(bookingId, BookingDecision.Action.KEEP, Set.of());
    Season saved = update(created, week(600, 1380), List.of(keep));
    assertThat(saved.week().hours(ScheduleDay.MONDAY)).containsExactly(new TimeRange(600, 1380));
    assertThat(impact.appliedTo(facility.value()))
        .singleElement()
        .satisfies(
            a -> {
              // Só as dúas primeiras horas de cada luns, de punta a punta da tempada.
              assertThat(a.change()).isInstanceOf(FacilityChange.ScheduleChanged.class);
              List<OpeningInterval> lost = ((FacilityChange.ScheduleChanged) a.change()).lost();
              assertThat(lost).allMatch(i -> i.startMinute() == 480 && i.endMinute() == 600);
              assertThat(lost.getFirst().date()).isEqualTo(LocalDate.parse("2026-09-07"));
              assertThat(lost.getLast().date()).isEqualTo(LocalDate.parse("2027-06-28"));
              assertThat(a.decisions()).containsExactly(keep);
            });
  }

  @Test
  void aSeasonWithoutEndLosesHoursUpToTenYearsFromToday() {
    Season always = update(create("Todo o ano", "2026-01-01", null), week(480, 1380), List.of());
    assertThat(always.endDate()).isNull();
    BookingDecision keep =
        new BookingDecision(UUID.randomUUID(), BookingDecision.Action.KEEP, Set.of());
    impact.affect(facility.value(), List.of(booking(keep.bookingId())));
    update(always, week(600, 1380), List.of(keep));
    List<OpeningInterval> lost =
        ((FacilityChange.ScheduleChanged) impact.appliedTo(facility.value()).getFirst().change())
            .lost();
    assertThat(lost.getFirst().date()).isEqualTo(LocalDate.parse("2026-01-05"));
    // Hoxe é o 1 de xaneiro de 2027 en Madrid: o último luns antes do 1 de xaneiro de 2037.
    assertThat(lost.getLast().date()).isEqualTo(LocalDate.parse("2036-12-29"));
  }

  @Test
  void aSeasonWithoutEndClashesWithAnyLaterOne() {
    create("Todo o ano", "2027-01-01", null);
    assertThatThrownBy(() -> create("Verán", "2030-07-01", "2030-08-31"))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(ScheduleError.SEASON_OVERLAP);
              assertThat(e.params())
                  .containsEntry("name", "Todo o ano")
                  .doesNotContainKey("endDate");
            });
    create("Outono", "2026-09-01", "2026-12-31");
  }

  @Test
  void renamingDoesNotAskForBookings() {
    Season created =
        update(create("Inverno", "2026-09-01", "2027-06-30"), week(480, 1380), List.of());
    impact.affect(facility.value(), List.of(booking(UUID.randomUUID())));
    Season renamed =
        service.update(
            new UpdateSeasonCommand(
                facility,
                created.id(),
                created.version(),
                spec("Inverno 26-27", "2026-09-01", "2027-07-15"),
                week(480, 1380),
                List.of()));
    assertThat(renamed.name()).isEqualTo("Inverno 26-27");
    assertThat(impact.appliedTo(facility.value())).isEmpty();
  }

  @Test
  void deleteAsksForBookingsOnlyIfTheSeasonHadHours() {
    Season empty = create("Baleira", "2028-01-01", "2028-01-31");
    impact.affect(facility.value(), List.of(booking(UUID.randomUUID())));
    service.delete(new DeleteSeasonCommand(facility, empty.id(), 0, List.of()));
    assertThat(seasons.findByFacility(facility)).isEmpty();

    impact.affect(facility.value(), List.of());
    Season winter =
        update(create("Inverno", "2026-09-01", "2027-06-30"), week(480, 1380), List.of());
    UUID bookingId = UUID.randomUUID();
    impact.affect(facility.value(), List.of(booking(bookingId)));
    assertThatThrownBy(
            () -> service.delete(new DeleteSeasonCommand(facility, winter.id(), 1, List.of())))
        .isInstanceOf(AffectedBookingsException.class);
    BookingDecision cancel =
        new BookingDecision(
            bookingId, BookingDecision.Action.CANCEL, Set.of(BookingDecision.Channel.EMAIL));
    service.delete(new DeleteSeasonCommand(facility, winter.id(), 1, List.of(cancel)));
    assertThat(seasons.findByFacility(facility)).isEmpty();
    assertThat(impact.appliedTo(facility.value())).hasSize(1);
    assertThat(audit.getLast().action()).isEqualTo("facility.season-delete");
  }

  @Test
  void writesOnAnUnknownFacilityAreNotFound() {
    FacilityId other = FacilityId.newId();
    assertCode(
        () ->
            service.create(
                new CreateSeasonCommand(other, spec("Inverno", "2026-09-01", "2027-06-30"), null)),
        FacilityError.NOT_FOUND);
    assertCode(() -> service.schedule(other, null), FacilityError.NOT_FOUND);
  }

  @Test
  void scheduleShowsTheCurrentSeasonAndHolidaysOfTodayInMadrid() {
    seasons.holidays.add(new PublicHoliday(LocalDate.parse("2026-12-25"), "Nadal", "Navidad", "x"));
    seasons.holidays.add(
        new PublicHoliday(LocalDate.parse("2027-01-01"), "Aninovo", "Año Nuevo", "x"));
    create("Outono", "2026-09-01", "2026-12-31");
    Season winter = create("Inverno", "2027-01-01", "2027-06-30");
    ScheduleView view = service.schedule(facility, null);
    assertThat(view.currentSeasonId()).isEqualTo(winter.id());
    assertThat(view.year()).isEqualTo(2027);
    assertThat(view.today()).isEqualTo(LocalDate.parse("2027-01-01"));
    assertThat(view.nowMinute()).isEqualTo(30);
    assertThat(view.holidays()).extracting(PublicHoliday::nameGl).containsExactly("Aninovo");
    assertThat(view.seasons()).extracting(Season::name).containsExactly("Outono", "Inverno");
    assertThat(service.schedule(facility, 2026).holidays()).hasSize(1);
    assertCode(() -> service.schedule(facility, 1999), ScheduleError.HOLIDAY_YEAR_INVALID);
    assertCode(
        () -> new PublicHolidayService(seasons).holidays(2101), ScheduleError.HOLIDAY_YEAR_INVALID);
  }
}
