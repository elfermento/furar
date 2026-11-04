// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A grella dos ocos (ADR-0025, puntos 6 e 7): aliñada desde a apertura de cada tramo, tamén despois
 * dun bloqueo; só os que caben enteiros; vixencia e días do uso; uso que bloquea; ocupación,
 * partes, prazas e antelación; cambios de hora.
 */
class SlotGridTest {

  /** Mércores 4 de novembro de 2026 (UTC+1). */
  static final LocalDate DAY = LocalDate.of(2026, 11, 4);

  /** A véspera ás 10:00 en Galicia. */
  static final Instant EVE = Instant.parse("2026-11-03T09:00:00Z");

  static final UUID USE = UUID.fromString("019a0b6e-0000-7000-8000-0000000000a1");
  static final UUID OTHER = UUID.fromString("019a0b6e-0000-7000-8000-0000000000a2");
  static final UUID NORTH = UUID.fromString("019a0b6e-0000-7000-8000-0000000000b1");
  static final UUID SOUTH = UUID.fromString("019a0b6e-0000-7000-8000-0000000000b2");

  static final int ALL_WEEK = 127;

  static int minute(int hour, int minute) {
    return hour * 60 + minute;
  }

  /** Un anaco aberto de {@code from} a {@code to} (minutos) cuxa grella empeza en {@code align}. */
  static OpenInterval open(LocalDate date, int from, int to, int align) {
    return new OpenInterval(date, SlotGrid.instant(date, from), SlotGrid.instant(date, to), align);
  }

  static OpenInterval open(int from, int to) {
    return open(DAY, from, to, from);
  }

  static Instant at(int hour, int minute) {
    return SlotGrid.instant(DAY, minute(hour, minute));
  }

  static BookableUse whole(int slotMinutes) {
    return use(USE, RentalMode.WHOLE, slotMinutes, null, List.of(), BookingRules.none());
  }

  static BookableUse use(
      UUID id,
      RentalMode mode,
      int slotMinutes,
      Integer capacity,
      List<UUID> parts,
      BookingRules rules) {
    return new BookableUse(
        id, mode, slotMinutes, capacity, parts, List.of(), ALL_WEEK, false, false, rules);
  }

  static SlotGrid grid(List<OpenInterval> intervals, List<Occupied> occupancy) {
    return new SlotGrid(DAY, intervals, occupancy, null, EVE);
  }

  static List<Integer> starts(List<FreeSlot> slots) {
    return slots.stream().map(FreeSlot::startMinute).toList();
  }

  static Occupied taken(UUID use, UUID part, int hour, int minute, int minutes) {
    Instant start = at(hour, minute);
    return new Occupied(use, part, start, start.plusSeconds(minutes * 60L));
  }

  // --- Aliñación ---

  @Test
  void opensAtHalfPastNineAndStepsByTheSlotDuration() {
    List<FreeSlot> slots =
        grid(List.of(open(minute(9, 30), minute(14, 0))), List.of()).freeSlots(whole(60));

    assertThat(starts(slots))
        .as("13:30 acabaría ás 14:30: non cabe e non sae")
        .containsExactly(minute(9, 30), minute(10, 30), minute(11, 30), minute(12, 30));
    assertThat(slots.getFirst().start()).isEqualTo(Instant.parse("2026-11-04T08:30:00Z"));
    assertThat(slots.getFirst().end()).isEqualTo(Instant.parse("2026-11-04T09:30:00Z"));
    assertThat(slots.getFirst().maxSlots()).as("ata o fin do anaco, sen norma").isEqualTo(4);
    assertThat(slots.getLast().maxSlots()).isEqualTo(1);
  }

  @Test
  void afterABlockTheSlotsFollowTheGridOfTheWholeInterval() {
    // Tramo 9:30–14:30 cortado por un bloqueo de 12:00 a 13:00: dous anacos coa mesma orixe.
    int align = minute(9, 30);
    SlotGrid grid =
        grid(
            List.of(
                open(DAY, minute(9, 30), minute(12, 0), align),
                open(DAY, minute(13, 0), minute(14, 30), align)),
            List.of());

    List<FreeSlot> slots = grid.freeSlots(whole(60));

    assertThat(starts(slots))
        .as("11:30 pisaría o bloqueo; despois del, 13:30 e non 13:00")
        .containsExactly(minute(9, 30), minute(10, 30), minute(13, 30));
    assertThat(slots.get(1).maxSlots()).as("as franxas seguidas non cruzan o bloqueo").isEqualTo(1);
  }

  @Test
  void aBlockUntilTheEndOfTheIntervalLeavesNoSlotThatDoesNotFit() {
    int align = minute(9, 30);
    List<FreeSlot> slots =
        grid(
                List.of(
                    open(DAY, minute(9, 30), minute(12, 0), align),
                    open(DAY, minute(13, 0), minute(14, 0), align)),
                List.of())
            .freeSlots(whole(60));

    assertThat(starts(slots)).containsExactly(minute(9, 30), minute(10, 30));
  }

  @Test
  void eachIntervalHasItsOwnGrid() {
    List<FreeSlot> slots =
        grid(
                List.of(open(minute(16, 15), minute(18, 15)), open(minute(9, 0), minute(11, 0))),
                List.of())
            .freeSlots(whole(60));

    assertThat(starts(slots))
        .containsExactly(minute(9, 0), minute(10, 0), minute(16, 15), minute(17, 15));
  }

  // --- Vixencia e días do uso, uso que bloquea ---

  @Test
  void aUseOutsideItsDatesOrWeekdaysOrArchivedHasNoSlots() {
    SlotGrid grid = grid(List.of(open(minute(9, 0), minute(11, 0))), List.of());
    int weekendOnly = 32 | 64;
    BookableUse weekend =
        new BookableUse(
            USE,
            RentalMode.WHOLE,
            60,
            null,
            List.of(),
            List.of(),
            weekendOnly,
            false,
            false,
            BookingRules.none());
    BookableUse summer =
        new BookableUse(
            USE,
            RentalMode.WHOLE,
            60,
            null,
            List.of(),
            List.of(new BookableUse.Period(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 9, 30))),
            ALL_WEEK,
            false,
            false,
            BookingRules.none());
    BookableUse archived =
        new BookableUse(
            USE,
            RentalMode.WHOLE,
            60,
            null,
            List.of(),
            List.of(),
            ALL_WEEK,
            true,
            false,
            BookingRules.none());
    BookableUse wednesdays =
        new BookableUse(
            USE,
            RentalMode.WHOLE,
            60,
            null,
            List.of(),
            List.of(),
            4,
            false,
            false,
            BookingRules.none());

    assertThat(grid.freeSlots(weekend)).isEmpty();
    assertThat(grid.freeSlots(summer)).isEmpty();
    assertThat(grid.freeSlots(archived)).isEmpty();
    assertThat(grid.freeSlots(wednesdays)).hasSize(2);
  }

  @Test
  void aUseThatBlocksTheOthersLeavesThemWithoutSlots() {
    BookableUse tournament =
        new BookableUse(
            OTHER,
            RentalMode.WHOLE,
            60,
            null,
            List.of(),
            List.of(),
            ALL_WEEK,
            false,
            true,
            BookingRules.none());
    BookableUse court = whole(60);
    UUID exclusive = SlotGrid.exclusiveUse(List.of(court, tournament), DAY).orElseThrow();
    SlotGrid grid =
        new SlotGrid(DAY, List.of(open(minute(9, 0), minute(11, 0))), List.of(), exclusive, EVE);

    assertThat(exclusive).isEqualTo(OTHER);
    assertThat(grid.freeSlots(court)).isEmpty();
    assertThat(grid.freeSlots(tournament)).hasSize(2);
    assertThat(SlotGrid.exclusiveUse(List.of(court), DAY)).isEmpty();
  }

  // --- Ocupación ---

  @Test
  void aWholeUseSkipsItsTakenSlotsAndCountsTheFreeOnesInARow() {
    SlotGrid grid =
        grid(List.of(open(minute(9, 0), minute(14, 0))), List.of(taken(USE, null, 11, 0, 60)));

    List<FreeSlot> slots = grid.freeSlots(whole(60));

    assertThat(starts(slots))
        .containsExactly(minute(9, 0), minute(10, 0), minute(12, 0), minute(13, 0));
    assertThat(slots.stream().map(FreeSlot::maxSlots)).containsExactly(2, 1, 2, 1);
    assertThat(slots.getFirst().freeParts()).isEmpty();
    assertThat(slots.getFirst().freeSeats()).isNull();
  }

  @Test
  void theMaximumDurationCapsTheSlotsInARow() {
    BookableUse use =
        use(
            USE,
            RentalMode.WHOLE,
            60,
            null,
            List.of(),
            new BookingRules(null, null, 2, null, null, null));
    List<FreeSlot> slots =
        grid(List.of(open(minute(9, 0), minute(14, 0))), List.of()).freeSlots(use);

    assertThat(slots.stream().map(FreeSlot::maxSlots)).containsExactly(2, 2, 2, 2, 1);
  }

  @Test
  void anotherUseOfTheFacilityTakesTheSlot() {
    SlotGrid grid =
        grid(List.of(open(minute(9, 0), minute(12, 0))), List.of(taken(OTHER, null, 10, 0, 60)));

    assertThat(starts(grid.freeSlots(whole(60)))).containsExactly(minute(9, 0), minute(11, 0));
  }

  @Test
  void partsShowTheFreeOnesInTheirOrder() {
    BookableUse halves =
        use(USE, RentalMode.PARTS, 60, null, List.of(NORTH, SOUTH), BookingRules.none());
    SlotGrid grid =
        grid(
            List.of(open(minute(9, 0), minute(12, 0))),
            List.of(
                taken(USE, NORTH, 9, 0, 60),
                taken(USE, NORTH, 10, 0, 60),
                taken(USE, SOUTH, 10, 0, 60)));

    List<FreeSlot> slots = grid.freeSlots(halves);

    assertThat(starts(slots))
        .as("ás 10:00 as dúas partes están collidas")
        .containsExactly(minute(9, 0), minute(11, 0));
    assertThat(slots.get(0).freeParts()).containsExactly(SOUTH);
    assertThat(slots.get(0).maxSlots()).as("a sur, só unha franxa ata as 10:00").isEqualTo(1);
    assertThat(slots.get(1).freeParts()).containsExactly(NORTH, SOUTH);
    assertThat(slots.get(1).freeSeats()).isNull();
  }

  @Test
  void seatsShowTheFreeOnesAndAFullSlotDisappears() {
    BookableUse classes = use(USE, RentalMode.SEATS, 60, 30, List.of(), BookingRules.none());
    List<Occupied> occupancy = new ArrayList<>();
    for (int i = 0; i < 29; i++) {
      occupancy.add(taken(USE, null, 9, 0, 60));
    }
    for (int i = 0; i < 30; i++) {
      occupancy.add(taken(USE, null, 10, 0, 60));
    }
    // Unha praza de dúas franxas conta nas dúas.
    occupancy.add(taken(USE, null, 11, 0, 120));

    List<FreeSlot> slots =
        grid(List.of(open(minute(9, 0), minute(13, 0))), occupancy).freeSlots(classes);

    assertThat(starts(slots))
        .as("ás 10:00, 30 de 30: non sae")
        .containsExactly(minute(9, 0), minute(11, 0), minute(12, 0));
    assertThat(slots.stream().map(FreeSlot::freeSeats)).containsExactly(1, 29, 29);
    assertThat(slots.getFirst().maxSlots()).isEqualTo(1);
    assertThat(slots.get(1).maxSlots()).isEqualTo(2);
  }

  // --- Antelación ---

  @Test
  void aSlotThatAlreadyStartedIsNeverShown() {
    SlotGrid grid =
        new SlotGrid(DAY, List.of(open(minute(9, 0), minute(12, 0))), List.of(), null, at(10, 0));

    assertThat(starts(grid.freeSlots(whole(60)))).containsExactly(minute(11, 0));
  }

  @Test
  void minimumAdvanceHidesTheSlotsTooSoon() {
    BookableUse use =
        use(
            USE,
            RentalMode.WHOLE,
            60,
            null,
            List.of(),
            new BookingRules(2, null, null, null, null, null));
    SlotGrid grid =
        new SlotGrid(DAY, List.of(open(minute(9, 0), minute(13, 0))), List.of(), null, at(8, 30));

    assertThat(starts(grid.freeSlots(use)))
        .as("10:30 é o primeiro instante permitido")
        .containsExactly(minute(11, 0), minute(12, 0));
  }

  @Test
  void maximumAdvanceHidesTheDaysTooFar() {
    BookableUse use =
        use(
            USE,
            RentalMode.WHOLE,
            60,
            null,
            List.of(),
            new BookingRules(null, 1, null, null, null, null));
    SlotGrid tomorrow = grid(List.of(open(minute(9, 0), minute(10, 0))), List.of());
    LocalDate later = DAY.plusDays(1);
    SlotGrid afterTomorrow =
        new SlotGrid(
            later,
            List.of(open(later, minute(9, 0), minute(10, 0), minute(9, 0))),
            List.of(),
            null,
            EVE);

    assertThat(tomorrow.freeSlots(use)).hasSize(1);
    assertThat(afterTomorrow.freeSlots(use)).isEmpty();
  }

  // --- Cambios de hora ---

  @Test
  void onTheDayTheClocksGoForwardTheSlotsKeepTheWallClockAndRealDuration() {
    LocalDate day = LocalDate.of(2026, 3, 29);
    SlotGrid grid =
        new SlotGrid(
            day,
            List.of(
                open(day, minute(1, 0), minute(4, 0), minute(1, 0)),
                open(day, minute(9, 30), minute(11, 30), minute(9, 30))),
            List.of(),
            null,
            Instant.parse("2026-03-28T12:00:00Z"));

    List<FreeSlot> slots = grid.freeSlots(whole(60));

    assertThat(slots.stream().map(FreeSlot::start))
        .as("de 1:00 a 4:00 hai dúas horas reais: 1:00 (UTC+1) e 3:00 (UTC+2)")
        .containsExactly(
            Instant.parse("2026-03-29T00:00:00Z"),
            Instant.parse("2026-03-29T01:00:00Z"),
            Instant.parse("2026-03-29T07:30:00Z"),
            Instant.parse("2026-03-29T08:30:00Z"));
    assertThat(starts(slots))
        .containsExactly(minute(1, 0), minute(3, 0), minute(9, 30), minute(10, 30));
  }

  @Test
  void onTheDayTheClocksGoBackTheSlotsKeepTheWallClockAndRealDuration() {
    LocalDate day = LocalDate.of(2026, 10, 25);
    SlotGrid grid =
        new SlotGrid(
            day,
            List.of(open(day, minute(9, 30), minute(11, 30), minute(9, 30))),
            List.of(),
            null,
            Instant.parse("2026-10-24T12:00:00Z"));

    List<FreeSlot> slots = grid.freeSlots(whole(60));

    assertThat(slots.stream().map(FreeSlot::start))
        .containsExactly(
            Instant.parse("2026-10-25T08:30:00Z"), Instant.parse("2026-10-25T09:30:00Z"));
    assertThat(starts(slots)).containsExactly(minute(9, 30), minute(10, 30));
  }

  @Test
  void intervalsOfAnotherDayAreRejected() {
    assertThatThrownBy(
            () ->
                new SlotGrid(
                    DAY.plusDays(1),
                    List.of(open(minute(9, 0), minute(10, 0))),
                    List.of(),
                    null,
                    EVE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // --- Reservar: comprobar e asignar ---

  static BookableUse parts() {
    return use(USE, RentalMode.PARTS, 60, null, List.of(NORTH, SOUTH), BookingRules.none());
  }

  static String code(Runnable call) {
    try {
      call.run();
    } catch (gal.furar.shared.domain.DomainException e) {
      return e.errorCode().code();
    }
    return "ok";
  }

  @Test
  void allocateChecksTheGridAndTheBlocks() {
    int align = minute(9, 30);
    SlotGrid grid =
        grid(
            List.of(
                open(DAY, minute(9, 30), minute(12, 0), align),
                open(DAY, minute(13, 0), minute(14, 30), align)),
            List.of());
    BookableUse use = whole(60);

    assertThat(grid.allocate(use, at(9, 30), 2, null)).isEmpty();
    assertThat(code(() -> grid.allocate(use, at(10, 0), 1, null)))
        .as("fóra da grella")
        .isEqualTo("booking.slot-invalid");
    assertThat(code(() -> grid.allocate(use, at(10, 30), 2, null)))
        .as("a segunda franxa pisa o bloqueo")
        .isEqualTo("booking.slot-invalid");
    assertThat(code(() -> grid.allocate(use, at(13, 0), 1, null)))
        .as("despois do bloqueo a grella segue as 13:30")
        .isEqualTo("booking.slot-invalid");
    assertThat(grid.allocate(use, at(13, 30), 1, null)).isEmpty();
    assertThat(code(() -> grid.allocate(use, at(8, 30), 1, null)))
        .isEqualTo("booking.slot-invalid");
  }

  @Test
  void allocateRejectsATakenSlotAndAnotherUse() {
    SlotGrid taken =
        grid(List.of(open(minute(9, 0), minute(13, 0))), List.of(taken(USE, null, 10, 0, 60)));
    SlotGrid byOther =
        grid(List.of(open(minute(9, 0), minute(13, 0))), List.of(taken(OTHER, null, 11, 0, 60)));
    SlotGrid exclusive =
        new SlotGrid(DAY, List.of(open(minute(9, 0), minute(13, 0))), List.of(), OTHER, EVE);

    assertThat(code(() -> taken.allocate(whole(60), at(9, 0), 2, null)))
        .isEqualTo("booking.slot-taken");
    assertThat(code(() -> byOther.allocate(whole(60), at(11, 0), 1, null)))
        .isEqualTo("booking.slot-taken");
    assertThat(code(() -> exclusive.allocate(whole(60), at(9, 0), 1, null)))
        .isEqualTo("booking.use-not-available");
  }

  @Test
  void anyPartTakesTheFirstFreeOneInEverySlotAndAChosenPartMustBeFree() {
    SlotGrid grid =
        grid(
            List.of(open(minute(9, 0), minute(13, 0))),
            List.of(taken(USE, NORTH, 10, 0, 60), taken(USE, SOUTH, 11, 0, 60)));

    assertThat(grid.allocate(parts(), at(9, 0), 1, null)).contains(NORTH);
    assertThat(grid.allocate(parts(), at(9, 0), 2, null))
        .as("norte está collida ás 10:00")
        .contains(SOUTH);
    assertThat(code(() -> grid.allocate(parts(), at(10, 0), 2, null)))
        .as("ningunha libre nas dúas franxas")
        .isEqualTo("booking.slot-taken");
    assertThat(code(() -> grid.allocate(parts(), at(10, 0), 1, NORTH)))
        .isEqualTo("booking.part-taken");
    assertThat(grid.allocate(parts(), at(10, 0), 1, SOUTH)).contains(SOUTH);
    assertThat(code(() -> grid.allocate(parts(), at(9, 0), 1, OTHER)))
        .as("unha parte doutro uso")
        .isEqualTo("booking.slot-invalid");
    assertThat(code(() -> grid.allocate(whole(60), at(9, 0), 1, NORTH)))
        .isEqualTo("booking.slot-invalid");
  }

  @Test
  void seatsAreTakenOnlyWhenFull() {
    BookableUse seats = use(USE, RentalMode.SEATS, 60, 2, List.of(), BookingRules.none());
    SlotGrid one =
        grid(List.of(open(minute(9, 0), minute(11, 0))), List.of(taken(USE, null, 9, 0, 60)));
    SlotGrid full =
        grid(
            List.of(open(minute(9, 0), minute(11, 0))),
            List.of(taken(USE, null, 9, 0, 60), taken(USE, null, 9, 0, 60)));

    assertThat(one.allocate(seats, at(9, 0), 1, null)).isEmpty();
    assertThat(code(() -> full.allocate(seats, at(9, 0), 1, null))).isEqualTo("booking.slot-taken");
  }

  @Test
  void theAlternativesAreTheThreeNearestFreeStartsWhereTheDurationFits() {
    SlotGrid grid =
        grid(
            List.of(open(minute(9, 0), minute(15, 0))),
            List.of(taken(USE, null, 12, 0, 60), taken(USE, null, 10, 0, 60)));

    assertThat(starts(grid.alternatives(whole(60), at(12, 0), 1)))
        .containsExactly(minute(11, 0), minute(13, 0), minute(14, 0));
    assertThat(starts(grid.alternatives(whole(60), at(12, 0), 2)))
        .as("con dúas franxas, só onde caben")
        .containsExactly(minute(13, 0));
  }
}
