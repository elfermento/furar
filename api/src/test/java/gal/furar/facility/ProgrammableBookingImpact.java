// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

/**
 * Dobre de {@link BookingImpact} (ADR-0022) para as probas do modal en {@code facility}: cada test
 * programa as afectadas da súa instalación e le o que se aplicou. As instalacións sen programar van
 * ao {@link BookingImpact} real de {@code booking} (dominio 2), así o resto dos IT ven as reservas
 * de verdade; nas probas sen Spring, coma {@link NoBookingImpact}.
 */
@TestComponent
@Primary
public class ProgrammableBookingImpact implements BookingImpact {

  /** Unha chamada a {@link #apply}. */
  public record Applied(FacilityChange change, List<BookingDecision> decisions) {}

  private final Map<UUID, List<AffectedBooking>> affected = new ConcurrentHashMap<>();
  private final Map<UUID, List<Applied>> applied = new ConcurrentHashMap<>();
  private final Set<UUID> booked = ConcurrentHashMap.newKeySet();
  private final @Nullable ObjectProvider<BookingImpact> impacts;

  /** Sen Spring: sen reservas de verdade. */
  public ProgrammableBookingImpact() {
    this.impacts = null;
  }

  /** Nos IT: o real de {@code booking} para o que non se programou. */
  @Autowired
  public ProgrammableBookingImpact(ObjectProvider<BookingImpact> impacts) {
    this.impacts = impacts;
  }

  /** As reservas que afectará calquera cambio desta instalación. */
  public void affect(UUID facilityId, List<AffectedBooking> bookings) {
    affected.put(facilityId, List.copyOf(bookings));
  }

  /** A instalación pasa a ter historial de reservas (non se pode borrar). */
  public void book(UUID facilityId) {
    booked.add(facilityId);
  }

  public List<Applied> appliedTo(UUID facilityId) {
    return List.copyOf(applied.getOrDefault(facilityId, List.of()));
  }

  @Override
  public List<AffectedBooking> affectedBy(FacilityChange change) {
    List<AffectedBooking> programmed = affected.get(change.facilityId());
    return programmed != null ? programmed : real().affectedBy(change);
  }

  @Override
  public void apply(FacilityChange change, List<BookingDecision> decisions) {
    if (affected.containsKey(change.facilityId())) {
      applied
          .computeIfAbsent(change.facilityId(), k -> new ArrayList<>())
          .add(new Applied(change, List.copyOf(decisions)));
    } else {
      real().apply(change, decisions);
    }
  }

  @Override
  public boolean hasBookings(UUID facilityId) {
    return booked.contains(facilityId) || real().hasBookings(facilityId);
  }

  private BookingImpact real() {
    if (impacts == null) {
      return new NoBookingImpact();
    }
    return impacts
        .orderedStream()
        .filter(impact -> impact != this)
        .findFirst()
        .orElseGet(NoBookingImpact::new);
  }
}
