// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.WaitNotice;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.application.port.out.BookingWaits;
import gal.furar.booking.application.port.out.NoShowLedger;
import gal.furar.booking.domain.BookableUse;
import gal.furar.booking.domain.SlotGrid;
import gal.furar.booking.domain.SlotGrid.SlotState;
import gal.furar.booking.domain.Wait;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.UseSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * A quen avisar dunha franxa liberada (ADR-0033, punto 7), cando o correo xa vai saír: despois do
 * commit, co estado de agora. Sen quenda: a orde é aleatoria.
 */
@Component
class WaitNotices {

  private static final String ACTIVE = "ACTIVE";

  private final BookingWaits waits;
  private final BookingRepository bookings;
  private final NoShowLedger ledger;
  private final NoShowPenalty penalty;
  private final FacilityQueries facilities;
  private final SlotGrids grids;
  private final Clock clock;

  WaitNotices(
      BookingWaits waits,
      BookingRepository bookings,
      NoShowLedger ledger,
      NoShowPenalty penalty,
      FacilityQueries facilities,
      SlotGrids grids,
      Clock clock) {
    this.waits = waits;
    this.bookings = bookings;
    this.ledger = ledger;
    this.penalty = penalty;
    this.facilities = facilities;
    this.grids = grids;
    this.clock = clock;
  }

  List<WaitNotice> of(UUID useId, Instant start, Instant releasedAt) {
    Instant now = clock.instant();
    // As vencidas e as creadas despois da liberación (son dunha ocupación posterior), fóra.
    List<Wait> waiting = waits.ofSlot(useId, start, releasedAt, now);
    if (waiting.isEmpty()) {
      return List.of();
    }
    Optional<UseSummary> use = facilities.use(useId).filter(u -> !u.archived());
    Optional<FacilitySummary> facility =
        use.flatMap(u -> facilities.findPublic(u.facilityId()))
            .filter(f -> ACTIVE.equals(f.status()) && f.onlineBooking());
    if (use.isEmpty() || facility.isEmpty()) {
      return List.of();
    }
    // Se a hora xa non está libre (colleuna alguén, ou é un reintento tardío), a ninguén.
    BookableUse bookable = Uses.bookable(use.get());
    SlotGrid grid = grids.of(facility.get().id(), start.atZone(SlotGrid.ZONE).toLocalDate(), now);
    if (grid.stateAt(bookable, start) != SlotState.FREE || !bookable.rules().allows(start, now)) {
      return List.of();
    }
    Set<String> emails = waiting.stream().map(Wait::emailKey).collect(Collectors.toSet());
    Instant end = waiting.getFirst().end();
    // Quen xa ten reserva nesa franxa e quen está bloqueada por faltas: unha consulta cada cousa.
    Set<String> booked = bookings.bookedAmong(useId, emails, start, end);
    Set<String> blocked = penalty.rule().enabled() ? ledger.blockedAmong(emails, now) : Set.of();
    FacilitySummary.Address address = facility.get().address();
    List<WaitNotice> notices =
        new ArrayList<>(
            waiting.stream()
                .filter(w -> !booked.contains(w.emailKey()) && !blocked.contains(w.emailKey()))
                .map(
                    w ->
                        new WaitNotice(
                            w.id(),
                            w.emailKey(),
                            w.language().code(),
                            w.facilityId(),
                            facility.get().name(),
                            address == null ? null : address.oneLine(),
                            w.useId(),
                            use.get().name(),
                            w.start(),
                            w.end()))
                .toList());
    Collections.shuffle(notices);
    return notices;
  }
}
