// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.domain.OpenInterval;
import gal.furar.booking.domain.SlotGrid;
import gal.furar.facility.FacilityQueries;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A grella dun día dunha instalación tal como está agora: o horario xa cortado polos bloqueos, a
 * ocupación activa e o uso en exclusiva. Reservar e cambiar lena baixo o bloqueo da instalación.
 */
@Component
class SlotGrids {

  private final FacilityQueries facilities;
  private final BookingRepository bookings;

  SlotGrids(FacilityQueries facilities, BookingRepository bookings) {
    this.facilities = facilities;
    this.bookings = bookings;
  }

  SlotGrid of(UUID facilityId, LocalDate date, Instant now) {
    List<OpenInterval> intervals =
        facilities.openingIntervals(facilityId, date, date).stream()
            .map(i -> new OpenInterval(i.date(), i.start(), i.end(), i.alignFromMinute()))
            .toList();
    Instant from = date.atStartOfDay(SlotGrid.ZONE).toInstant();
    Instant to = date.plusDays(1).atStartOfDay(SlotGrid.ZONE).toInstant();
    return new SlotGrid(
        date,
        intervals,
        bookings.activeOccupancy(List.of(facilityId), from, to).getOrDefault(facilityId, List.of()),
        facilities.exclusiveUseOn(facilityId, date).orElse(null),
        now);
  }
}
