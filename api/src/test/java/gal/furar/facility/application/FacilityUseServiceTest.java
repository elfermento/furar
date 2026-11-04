// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.facility.AffectedBooking;
import gal.furar.facility.BookingDecision;
import gal.furar.facility.FacilityChange;
import gal.furar.facility.ProgrammableBookingImpact;
import gal.furar.facility.application.port.in.AffectedBookingsException;
import gal.furar.facility.application.port.in.ArchiveUseCommand;
import gal.furar.facility.application.port.in.CreateUseCommand;
import gal.furar.facility.application.port.in.UpdateUseCommand;
import gal.furar.facility.application.service.FacilityUseService;
import gal.furar.facility.domain.Address;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityUse;
import gal.furar.facility.domain.RentalMode;
import gal.furar.facility.domain.UseAvailability;
import gal.furar.facility.domain.UseError;
import gal.furar.facility.domain.UseId;
import gal.furar.facility.domain.UsePeriod;
import gal.furar.facility.domain.UseSpec;
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

/** Casos de uso dos usos con repositorios en memoria e un dobre de BookingImpact. */
class FacilityUseServiceTest {

  final InMemoryFacilityRepository facilities = new InMemoryFacilityRepository();
  final InMemoryFacilityUseRepository uses = new InMemoryFacilityUseRepository(facilities.calls);
  final ProgrammableBookingImpact impact = new ProgrammableBookingImpact();
  final List<AuditEvent> audit = new ArrayList<>();
  final Clock clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC);
  final FacilityUseService service =
      new FacilityUseService(facilities, uses, facilities, impact, audit::add, clock);
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
                    "Campo de fútbol 1 de O Alto",
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

  static UseSpec whole(String name) {
    return new UseSpec(
        name, RentalMode.WHOLE, null, 60, UseAvailability.ALWAYS, false, List.of(), List.of());
  }

  static UseSpec blocking(String name, String from, String to) {
    return new UseSpec(
        name,
        RentalMode.WHOLE,
        null,
        120,
        UseAvailability.DATES,
        true,
        List.of(),
        List.of(new UsePeriod(LocalDate.parse(from), LocalDate.parse(to))));
  }

  static UseSpec parts(String name, UseSpec.PartSpec... parts) {
    return new UseSpec(
        name, RentalMode.PARTS, null, 60, UseAvailability.ALWAYS, false, List.of(parts), List.of());
  }

  FacilityUse create(UseSpec spec) {
    return service.create(new CreateUseCommand(facility, spec, List.of()));
  }

  static AffectedBooking booking(UUID id) {
    return new AffectedBooking(
        id, "Uxía Pérez", true, LocalDate.of(2026, 11, 3), 480, 570, "Fútbol 7");
  }

  static void assertCode(Runnable call, ErrorCode code) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
  }

  @Test
  void createTakesTheFacilityLockFirstAndAudits() {
    FacilityUse created = create(whole("Fútbol 11"));
    assertThat(created.version()).isZero();
    assertThat(facilities.calls).containsExactly("lock", "insert-use");
    assertThat(service.list(facility)).extracting(FacilityUse::name).containsExactly("Fútbol 11");
    assertThat(audit)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.action()).isEqualTo("facility.use-create");
              assertThat(e.outcome()).isEqualTo(AuditEvent.Outcome.SUCCESS);
              assertThat(e.details()).containsEntry("useId", created.id().value().toString());
            });
  }

  @Test
  void unknownFacilityIsNotFound() {
    FacilityId other = FacilityId.newId();
    assertCode(
        () -> service.create(new CreateUseCommand(other, whole("Fútbol 11"), List.of())),
        FacilityError.NOT_FOUND);
    assertCode(() -> service.list(other), FacilityError.NOT_FOUND);
    assertCode(() -> service.get(facility, UseId.newId()), UseError.NOT_FOUND);
    assertThat(audit.getLast().details()).containsEntry("code", "facility.not-found");
  }

  @Test
  void updateNeedsTheCurrentVersion() {
    FacilityUse created = create(whole("Fútbol 11"));
    FacilityUse renamed =
        service.update(
            new UpdateUseCommand(facility, created.id(), 0, whole("Fútbol once"), List.of()));
    assertThat(renamed.version()).isEqualTo(1);
    assertThat(renamed.name()).isEqualTo("Fútbol once");
    assertCode(
        () ->
            service.update(
                new UpdateUseCommand(facility, created.id(), 0, whole("Outro"), List.of())),
        CommonError.STALE_VERSION);
  }

  @Test
  void twoBlockingUsesCannotOverlap() {
    create(blocking("Torneo de verán", "2026-07-01", "2026-07-31"));
    assertThatThrownBy(() -> create(blocking("Campus", "2026-07-31", "2026-08-15")))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(UseError.EXCLUSIVE_USE_OVERLAP);
              assertThat(e.params()).containsEntry("name", "Torneo de verán");
            });
    assertThat(create(blocking("Campus", "2026-08-01", "2026-08-15")).blocksOthers()).isTrue();
  }

  @Test
  void aBlockingUseAsksForAffectedBookingsOfTheNewDaysOnly() {
    UUID b1 = UUID.randomUUID();
    impact.affect(facility.value(), List.of(booking(b1)));
    UseSpec spec = blocking("Torneo", "2026-11-01", "2026-11-05");
    assertThatThrownBy(() -> create(spec))
        .isInstanceOfSatisfying(
            AffectedBookingsException.class,
            e ->
                assertThat(e.affected())
                    .extracting(AffectedBooking::bookingId)
                    .containsExactly(b1));
    assertThat(uses.findActive(facility)).isEmpty();

    BookingDecision keep = new BookingDecision(b1, BookingDecision.Action.KEEP, Set.of());
    FacilityUse saved = service.create(new CreateUseCommand(facility, spec, List.of(keep)));
    assertThat(impact.appliedTo(facility.value()))
        .singleElement()
        .satisfies(
            applied -> {
              assertThat(applied.change())
                  .isEqualTo(
                      new FacilityChange.ExclusiveUse(
                          facility.value(),
                          saved.id().value(),
                          List.of(
                              new FacilityChange.Dates(
                                  LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-05")))));
              assertThat(applied.decisions()).containsExactly(keep);
            });

    // Renomear non engade días bloqueados: non se pregunta outra vez.
    FacilityUse renamed =
        service.update(
            new UpdateUseCommand(
                facility,
                saved.id(),
                0,
                blocking("Torneo de Samaín", "2026-11-01", "2026-11-05"),
                List.of()));
    assertThat(renamed.name()).isEqualTo("Torneo de Samaín");
    assertThat(impact.appliedTo(facility.value())).hasSize(1);
  }

  @Test
  void structuralChangesWithFutureBookingsAreRefused() {
    FacilityUse seven =
        create(parts("Fútbol 7", new UseSpec.PartSpec(null, "A"), new UseSpec.PartSpec(null, "B")));
    impact.affect(facility.value(), List.of(booking(UUID.randomUUID())));
    UUID a = seven.parts().get(0).id();
    UUID b = seven.parts().get(1).id();

    // Quitar unha parte toca reservas.
    UseSpec withoutB =
        parts("Fútbol 7", new UseSpec.PartSpec(a, "A"), new UseSpec.PartSpec(null, "C"));
    assertThatThrownBy(
            () ->
                service.update(new UpdateUseCommand(facility, seven.id(), 0, withoutB, List.of())))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(UseError.HAS_BOOKINGS);
              assertThat(e.params()).containsEntry("count", 1);
            });
    assertCode(
        () -> service.archive(new ArchiveUseCommand(facility, seven.id(), 0)),
        UseError.HAS_BOOKINGS);

    // Renomear partes e engadir outra, si.
    UseSpec renamed =
        parts(
            "Fútbol sete",
            new UseSpec.PartSpec(b, "Campo A"),
            new UseSpec.PartSpec(a, "Campo B"),
            new UseSpec.PartSpec(null, "Campo C"));
    FacilityUse saved =
        service.update(new UpdateUseCommand(facility, seven.id(), 0, renamed, List.of()));
    assertThat(saved.parts()).hasSize(3);
  }

  static UseSpec seats(int capacity) {
    return new UseSpec(
        "Spinning",
        RentalMode.SEATS,
        capacity,
        60,
        UseAvailability.ALWAYS,
        false,
        List.of(),
        List.of());
  }

  @Test
  void changingTheSeatsTellsBookingTheNewCapacityOnlyWhenTheyChange() {
    FacilityUse spinning = create(seats(8));
    impact.affect(facility.value(), List.of());

    service.update(new UpdateUseCommand(facility, spinning.id(), 0, seats(10), List.of()));
    service.update(new UpdateUseCommand(facility, spinning.id(), 1, seats(10), List.of()));
    service.update(new UpdateUseCommand(facility, spinning.id(), 2, seats(6), List.of()));

    assertThat(impact.appliedTo(facility.value()))
        .extracting(ProgrammableBookingImpact.Applied::change)
        .containsExactly(
            new FacilityChange.SeatsChanged(facility.value(), spinning.id().value(), 10),
            new FacilityChange.SeatsChanged(facility.value(), spinning.id().value(), 6));
  }

  @Test
  void loweringTheSeatsWithFutureBookingsIsRefusedAndBookingIsNotTold() {
    FacilityUse spinning = create(seats(8));
    impact.affect(facility.value(), List.of(booking(UUID.randomUUID())));

    assertCode(
        () -> service.update(new UpdateUseCommand(facility, spinning.id(), 0, seats(6), List.of())),
        UseError.HAS_BOOKINGS);
    assertThat(impact.appliedTo(facility.value())).isEmpty();
  }

  @Test
  void archiveKeepsTheUseAndFreesItsName() {
    FacilityUse created = create(whole("Fútbol 11"));
    FacilityUse archived = service.archive(new ArchiveUseCommand(facility, created.id(), 0));
    assertThat(archived.isArchived()).isTrue();
    assertThat(service.list(facility)).isEmpty();
    assertThat(service.get(facility, created.id()).isArchived()).isTrue();
    assertThat(service.archive(new ArchiveUseCommand(facility, created.id(), 1)).version())
        .isEqualTo(1);
    assertCode(
        () ->
            service.update(
                new UpdateUseCommand(facility, created.id(), 1, whole("Fútbol 11"), List.of())),
        UseError.ARCHIVED);
    assertThat(create(whole("Fútbol 11")).isArchived()).isFalse();
  }
}
