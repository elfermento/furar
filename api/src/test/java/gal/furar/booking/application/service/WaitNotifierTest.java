// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.application.service.WaitNotifier.Freed;
import gal.furar.booking.domain.BookableUse;
import gal.furar.booking.domain.BookingRules;
import gal.furar.booking.domain.Occupied;
import gal.furar.booking.domain.OpenInterval;
import gal.furar.booking.domain.RentalMode;
import gal.furar.booking.domain.SlotGrid;
import gal.furar.booking.domain.Wait;
import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Só se avisa das franxas que pasan de ocupadas a libres (ADR-0033, punto 7): a grella de antes
 * reconstrúese coa de agora, menos o escrito, máis o soltado.
 */
class WaitNotifierTest {

  static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
  static final LocalDate DAY = LocalDate.of(2026, 11, 11);
  static final Instant NOW = Instant.parse("2026-11-10T09:00:00Z");

  static final UUID FACILITY = UUID.fromString("019a0b6e-0000-7000-8000-0000000000f1");
  static final UUID USE = UUID.fromString("019a0b6e-0000-7000-8000-0000000000a1");
  static final UUID OTHER = UUID.fromString("019a0b6e-0000-7000-8000-0000000000a2");
  static final UUID NORTH = UUID.fromString("019a0b6e-0000-7000-8000-0000000000b1");
  static final UUID SOUTH = UUID.fromString("019a0b6e-0000-7000-8000-0000000000b2");

  static Instant at(int hour) {
    return DAY.atTime(hour, 0).atZone(ZONE).toInstant();
  }

  static BookableUse use(UUID id, RentalMode mode, Integer capacity, List<UUID> parts) {
    return new BookableUse(
        id, mode, 60, capacity, parts, List.of(), 127, false, false, BookingRules.none());
  }

  static final BookableUse WHOLE = use(USE, RentalMode.WHOLE, null, List.of());
  static final BookableUse SEATS = use(USE, RentalMode.SEATS, 2, List.of());
  static final BookableUse PARTS = use(USE, RentalMode.PARTS, null, List.of(NORTH, SOUTH));

  static Occupied taken(UUID use, UUID part, int hour, int hours, int seats) {
    return new Occupied(use, part, at(hour), at(hour + hours), seats);
  }

  static Occupied taken(int hour) {
    return taken(USE, null, hour, 1, 1);
  }

  /** A grella de agora: aberta de 16:00 a 22:00 coa ocupación que queda. */
  static SlotGrid after(Occupied... occupancy) {
    return new SlotGrid(
        DAY,
        List.of(new OpenInterval(DAY, at(16), at(22), 16 * 60)),
        List.of(occupancy),
        null,
        NOW);
  }

  static Wait wait(UUID use, int hour) {
    return new Wait(
        UUID.randomUUID(),
        FACILITY,
        use,
        at(hour),
        at(hour + 1),
        "sub-ana",
        "ana@example.org",
        Language.GL,
        NOW);
  }

  static List<Freed> freed(
      SlotGrid after,
      List<Occupied> released,
      List<Occupied> written,
      BookableUse use,
      Wait... waits) {
    return List.copyOf(
        WaitNotifier.freed(after, released, written, Map.of(use.id(), use), List.of(waits)));
  }

  @Test
  void aCancelledBookingFreesItsHourOnceForEveryoneWaiting() {
    List<Freed> freed =
        freed(after(), List.of(taken(19)), List.of(), WHOLE, wait(USE, 19), wait(USE, 19));

    assertThat(freed).containsExactly(new Freed(USE, at(19), at(20)));
  }

  /** Unha reserva de dúas franxas libera as dúas; só se avisa das que alguén espera. */
  @Test
  void onlyTheHoursSomeoneWaitsFor() {
    List<Freed> freed =
        freed(
            after(),
            List.of(taken(USE, null, 18, 2, 1)),
            List.of(),
            WHOLE,
            wait(USE, 19),
            wait(USE, 21));

    assertThat(freed).containsExactly(new Freed(USE, at(19), at(20)));
  }

  /** Por prazas: a última praza que se solta avisa; a segunda cancelación xa non. */
  @Test
  void aSecondCancellationBySeatsDoesNotNotifyAgain() {
    // Estaba chea (2 de 2) e sóltase unha: de ocupada a libre.
    assertThat(freed(after(taken(19)), List.of(taken(19)), List.of(), SEATS, wait(USE, 19)))
        .hasSize(1);
    // Xa tiña sitio (1 de 2) e sóltase a outra: non cambia nada.
    assertThat(freed(after(), List.of(taken(19)), List.of(), SEATS, wait(USE, 19))).isEmpty();
    // Unha reserva de dúas prazas que enchía ela soa.
    assertThat(freed(after(), List.of(taken(USE, null, 19, 1, 2)), List.of(), SEATS, wait(USE, 19)))
        .hasSize(1);
  }

  /** Por partes: a última parte que se solta avisa; a segunda cancelación xa non. */
  @Test
  void aSecondCancellationByPartsDoesNotNotifyAgain() {
    Occupied north = taken(USE, NORTH, 19, 1, 1);
    Occupied south = taken(USE, SOUTH, 19, 1, 1);

    assertThat(freed(after(south), List.of(north), List.of(), PARTS, wait(USE, 19))).hasSize(1);
    assertThat(freed(after(), List.of(south), List.of(), PARTS, wait(USE, 19))).isEmpty();
  }

  /** Un cambio que deixa a hora igual de chea non avisa; un que a baleira, si. */
  @Test
  void aChangeNotifiesOnlyOfWhatItLeavesFree() {
    // Cambia de dúas franxas (19 e 20) a unha (20): só queda libre a das 19.
    Occupied before = taken(USE, null, 19, 2, 1);
    Occupied now = taken(20);
    assertThat(
            freed(after(now), List.of(before), List.of(now), WHOLE, wait(USE, 19), wait(USE, 20)))
        .containsExactly(new Freed(USE, at(19), at(20)));

    // Coas dúas partes collidas, unha reserva cambia sen soltar a súa parte: segue chea.
    Occupied north = taken(USE, NORTH, 19, 1, 1);
    Occupied south = taken(USE, SOUTH, 19, 1, 1);
    Occupied longer = taken(USE, NORTH, 19, 2, 1);
    assertThat(freed(after(longer, south), List.of(north), List.of(longer), PARTS, wait(USE, 19)))
        .isEmpty();
    // E se cambia a outra hora, a súa parte queda libre: avísase.
    Occupied moved = taken(USE, NORTH, 21, 1, 1);
    assertThat(freed(after(moved, south), List.of(north), List.of(moved), PARTS, wait(USE, 19)))
        .containsExactly(new Freed(USE, at(19), at(20)));
  }

  /** A hora que ocupaba outro uso da instalación tamén queda libre para quen espera por este. */
  @Test
  void theHourAnotherUseOccupiedIsFreedToo() {
    assertThat(
            freed(after(), List.of(taken(OTHER, null, 19, 1, 1)), List.of(), WHOLE, wait(USE, 19)))
        .containsExactly(new Freed(USE, at(19), at(20)));
    // Pero non se o propio uso segue ocupándoa.
    assertThat(
            freed(
                after(taken(19)),
                List.of(taken(OTHER, null, 19, 1, 1)),
                List.of(),
                SEATS,
                wait(USE, 19)))
        .hasSize(1);
    assertThat(
            freed(
                after(taken(USE, null, 19, 1, 2)),
                List.of(taken(OTHER, null, 19, 1, 1)),
                List.of(),
                SEATS,
                wait(USE, 19)))
        .isEmpty();
  }

  /** Fóra de horario (un bloqueo ou un peche quitaron a hora) ou cun uso descoñecido, nada. */
  @Test
  void anHourThatNoLongerExistsIsNotFreed() {
    SlotGrid closed = new SlotGrid(DAY, List.of(), List.of(), null, NOW);
    assertThat(freed(closed, List.of(taken(19)), List.of(), WHOLE, wait(USE, 19))).isEmpty();
    assertThat(freed(after(), List.of(taken(19)), List.of(), WHOLE, wait(OTHER, 19))).isEmpty();
    // Xa empezada.
    SlotGrid late =
        new SlotGrid(
            DAY, List.of(new OpenInterval(DAY, at(16), at(22), 16 * 60)), List.of(), null, at(19));
    assertThat(freed(late, List.of(taken(19)), List.of(), WHOLE, wait(USE, 19))).isEmpty();
  }
}
