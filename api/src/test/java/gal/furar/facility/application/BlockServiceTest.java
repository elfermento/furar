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
import gal.furar.facility.application.port.in.schedule.CreateBlockCommand;
import gal.furar.facility.application.port.in.schedule.DeleteBlockCommand;
import gal.furar.facility.application.port.in.schedule.UpdateBlockCommand;
import gal.furar.facility.application.service.schedule.BlockService;
import gal.furar.facility.domain.Address;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.Block;
import gal.furar.facility.domain.schedule.BlockId;
import gal.furar.facility.domain.schedule.BlockSpec;
import gal.furar.facility.domain.schedule.ScheduleError;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.ErrorCode;
import gal.furar.shared.security.Actor;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Casos de uso dos bloqueos con repositorios en memoria e un dobre de BookingImpact: bloqueo da
 * instalación primeiro, solape co bloqueo que choca (P7), modal só cando se bloquean horas novas,
 * autoría e rexistro.
 */
class BlockServiceTest {

  final InMemoryFacilityRepository facilities = new InMemoryFacilityRepository();
  final InMemoryBlockRepository blocks = new InMemoryBlockRepository(facilities.calls);
  final ProgrammableBookingImpact impact = new ProgrammableBookingImpact();
  final List<AuditEvent> audit = new ArrayList<>();
  final Clock clock = Clock.fixed(Instant.parse("2026-10-25T09:05:00Z"), ZoneOffset.UTC);
  final BlockService service =
      new BlockService(
          facilities,
          blocks,
          facilities,
          impact,
          () -> Optional.of(new Actor("manager-1", Set.of())),
          audit::add,
          clock);
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

  static BlockSpec spec(String date, int start, int end) {
    return new BlockSpec(LocalDate.parse(date), start, end, "Mantemento do céspede");
  }

  Block create(BlockSpec spec, BookingDecision... decisions) {
    return service.create(new CreateBlockCommand(facility, spec, List.of(decisions)));
  }

  Block update(Block b, BlockSpec spec, BookingDecision... decisions) {
    return service.update(
        new UpdateBlockCommand(facility, b.id(), b.version(), spec, List.of(decisions)));
  }

  static AffectedBooking booking(UUID id) {
    return new AffectedBooking(
        id, "Club Deportivo Narón", true, LocalDate.of(2026, 11, 3), 480, 570, "Fútbol 11");
  }

  static void assertCode(Runnable call, ErrorCode code) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
  }

  @Test
  void createTakesTheFacilityLockFirstRecordsTheAuthorAndAudits() {
    Block created = create(spec("2026-11-03", 480, 720));
    assertThat(created.version()).isZero();
    assertThat(created.createdBy()).isEqualTo("manager-1");
    assertThat(facilities.calls).containsExactly("lock", "insert-block");
    assertThat(audit)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.action()).isEqualTo("facility.block-create");
              assertThat(e.outcome()).isEqualTo(AuditEvent.Outcome.SUCCESS);
              assertThat(e.details())
                  .containsEntry("blockId", created.id().value().toString())
                  .containsEntry("date", "2026-11-03");
            });
    assertThat(service.blocks(facility)).extracting(Block::id).containsExactly(created.id());
  }

  @Test
  void anOverlapSaysWhichBlockHasThoseHours() {
    Block first = create(spec("2026-11-03", 480, 720));
    assertThatThrownBy(() -> create(spec("2026-11-03", 660, 780)))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(ScheduleError.BLOCK_OVERLAP);
              assertThat(e.params())
                  .containsEntry("blockId", first.id().value().toString())
                  .containsEntry("startMinute", 480)
                  .containsEntry("endMinute", 720);
            });
    assertThat(audit.getLast().outcome()).isEqualTo(AuditEvent.Outcome.FAILURE);
    assertThat(audit.getLast().details()).containsEntry("code", "facility.block-overlap");
    // Seguido non se pisa; e o mesmo bloqueo pode moverse sobre as súas horas.
    Block afternoon = create(spec("2026-11-03", 720, 780));
    assertCode(() -> update(afternoon, spec("2026-11-03", 600, 780)), ScheduleError.BLOCK_OVERLAP);
    assertThat(update(first, spec("2026-11-03", 420, 720)).version()).isEqualTo(1);
  }

  @Test
  void aNewBlockWithBookingsOpensTheDialogAndAppliesTheDecisions() {
    UUID bookingId = UUID.randomUUID();
    impact.affect(facility.value(), List.of(booking(bookingId)));
    assertThatThrownBy(() -> create(spec("2026-11-03", 480, 720)))
        .isInstanceOfSatisfying(
            AffectedBookingsException.class,
            e ->
                assertThat(e.affected())
                    .extracting(AffectedBooking::bookingId)
                    .containsExactly(bookingId));
    assertThat(blocks.findByFacility(facility)).isEmpty();

    BookingDecision cancel =
        new BookingDecision(
            bookingId, BookingDecision.Action.CANCEL, Set.of(BookingDecision.Channel.WEB));
    Block created = create(spec("2026-11-03", 480, 720), cancel);
    assertThat(impact.appliedTo(facility.value()))
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.change())
                  .isEqualTo(
                      new FacilityChange.Block(
                          facility.value(), null, created.start(), created.end()));
              assertThat(a.decisions()).containsExactly(cancel);
            });

    // Sen canle ao cancelar → 422.
    BookingDecision mute = new BookingDecision(bookingId, BookingDecision.Action.CANCEL, Set.of());
    assertCode(() -> create(spec("2026-11-04", 480, 720), mute), FacilityError.DECISION_INVALID);
  }

  @Test
  void onlyChangesThatBlockNewHoursAskForBookings() {
    Block block = create(spec("2026-11-03", 480, 720));
    UUID bookingId = UUID.randomUUID();
    impact.affect(facility.value(), List.of(booking(bookingId)));

    // Só o motivo, ou acurtalo: as reservas que xa respectaba seguen igual.
    Block renamed =
        update(block, new BlockSpec(LocalDate.parse("2026-11-03"), 480, 720, "Pintura"));
    Block shorter = update(renamed, spec("2026-11-03", 540, 720));
    assertThat(impact.appliedTo(facility.value())).isEmpty();

    // Alongalo bloquea horas novas: modal co período novo e o id do bloqueo.
    assertThatThrownBy(() -> update(shorter, spec("2026-11-03", 480, 780)))
        .isInstanceOf(AffectedBookingsException.class);
    BookingDecision keep = new BookingDecision(bookingId, BookingDecision.Action.KEEP, Set.of());
    Block longer = update(shorter, spec("2026-11-03", 480, 780), keep);
    assertThat(longer.version()).isEqualTo(3);
    assertThat(impact.appliedTo(facility.value()))
        .singleElement()
        .satisfies(
            a ->
                assertThat(a.change())
                    .isEqualTo(
                        new FacilityChange.Block(
                            facility.value(), block.id().value(), longer.start(), longer.end())));

    // Borrar non deixa fóra ningunha reserva.
    service.delete(new DeleteBlockCommand(facility, longer.id(), 3));
    assertThat(blocks.findByFacility(facility)).isEmpty();
    assertThat(impact.appliedTo(facility.value())).hasSize(1);
    assertThat(audit.getLast().action()).isEqualTo("facility.block-delete");
  }

  @Test
  void writesNeedTheVersionAndTheFacility() {
    Block created = create(spec("2026-11-03", 480, 720));
    assertCode(
        () ->
            service.update(
                new UpdateBlockCommand(
                    facility, created.id(), 4, spec("2026-11-03", 480, 720), List.of())),
        CommonError.STALE_VERSION);
    assertCode(
        () -> service.delete(new DeleteBlockCommand(facility, BlockId.newId(), 0)),
        ScheduleError.BLOCK_NOT_FOUND);
    assertCode(
        () ->
            service.create(
                new CreateBlockCommand(
                    FacilityId.newId(), spec("2026-11-03", 480, 720), List.of())),
        FacilityError.NOT_FOUND);
    assertCode(() -> service.blocks(FacilityId.newId()), FacilityError.NOT_FOUND);
    assertCode(() -> service.block(facility, BlockId.newId()), ScheduleError.BLOCK_NOT_FOUND);
    assertThat(service.block(facility, created.id()).reason()).isEqualTo("Mantemento do céspede");
  }
}
