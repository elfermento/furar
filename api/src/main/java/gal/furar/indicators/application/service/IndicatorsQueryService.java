// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.application.service;

import gal.furar.booking.BookingStatistics;
import gal.furar.booking.BookingStatistics.ConfirmedInterval;
import gal.furar.booking.BookingStatistics.ConfirmedIntervals;
import gal.furar.facility.FacilityOpenDataQueries;
import gal.furar.facility.FacilityOpenDataQueries.ArchivedPart;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.OpeningInterval;
import gal.furar.facility.UseSummary;
import gal.furar.indicators.IndicatorsQueries;
import gal.furar.indicators.domain.QuarterOccupancy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa {@link IndicatorsQueries} (ADR-0034, punto 4): os tramos abertos do horario (sen os
 * bloqueos) e os usos de {@code facility}, as confirmadas e os aforos das franxas de {@code
 * booking}, e o corte en tramos de {@link QuarterOccupancy}. Cinco lecturas fixas, sexa cal sexa o
 * número de instalacións.
 */
@Service
class IndicatorsQueryService implements IndicatorsQueries {

  private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

  private final FacilityQueries facilities;
  private final FacilityOpenDataQueries openData;
  private final BookingStatistics bookings;

  IndicatorsQueryService(
      FacilityQueries facilities, FacilityOpenDataQueries openData, BookingStatistics bookings) {
    this.facilities = facilities;
    this.openData = openData;
    this.bookings = bookings;
  }

  @Override
  @Transactional(readOnly = true)
  public List<DayQuarters> quarterOccupancy(
      LocalDate from, LocalDate to, Collection<UUID> facilityIds) {
    if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
      throw new IllegalArgumentException("from " + from + " to " + to);
    }
    if (facilityIds.isEmpty()) {
      return List.of();
    }
    Set<UUID> wanted = new HashSet<>(facilityIds);
    Map<UUID, List<OpeningInterval>> openByFacility =
        facilities.openingIntervalsByFacility(null, from, to);
    Map<UUID, UseSummary> uses =
        facilities.allUses().stream()
            .filter(u -> wanted.contains(u.facilityId()))
            .collect(Collectors.toMap(UseSummary::id, u -> u));
    List<UUID> partUses =
        uses.values().stream()
            .filter(u -> "PARTS".equals(u.rentalMode()))
            .map(UseSummary::id)
            .toList();
    Map<UUID, List<Instant>> archivedAt = new HashMap<>();
    for (ArchivedPart p : openData.archivedParts(partUses)) {
      archivedAt.computeIfAbsent(p.useId(), k -> new ArrayList<>()).add(p.archivedAt());
    }
    ConfirmedIntervals confirmed = bookings.confirmedIntervals(from, to, wanted);
    Map<UUID, List<ConfirmedInterval>> bookingsOf =
        confirmed.bookings().stream().collect(Collectors.groupingBy(ConfirmedInterval::facilityId));
    List<QuarterOccupancy.SeatSlot> seatSlots =
        confirmed.seatSlots().stream()
            .map(s -> new QuarterOccupancy.SeatSlot(s.useId(), s.slotStart(), s.capacity()))
            .toList();

    List<DayQuarters> result = new ArrayList<>();
    for (UUID facility : facilityIds.stream().distinct().sorted().toList()) {
      Map<LocalDate, List<QuarterOccupancy.Open>> openOf =
          openByFacility.getOrDefault(facility, List.of()).stream()
              .collect(
                  Collectors.groupingBy(
                      OpeningInterval::date,
                      Collectors.mapping(
                          o -> new QuarterOccupancy.Open(o.start(), o.end()),
                          Collectors.toList())));
      List<ConfirmedInterval> facilityBookings = bookingsOf.getOrDefault(facility, List.of());
      for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
        List<QuarterOccupancy.Open> open = openOf.getOrDefault(day, List.of());
        if (open.isEmpty()) {
          continue;
        }
        Instant start = day.atStartOfDay(ZONE).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(ZONE).toInstant();
        List<QuarterOccupancy.Booking> dayBookings =
            facilityBookings.stream()
                .filter(b -> b.start().isBefore(end) && b.end().isAfter(start))
                .map(
                    b ->
                        new QuarterOccupancy.Booking(
                            b.useId(), b.partId(), b.start(), b.end(), b.seats()))
                .toList();
        Map<UUID, QuarterOccupancy.Use> dayUses = new HashMap<>();
        for (QuarterOccupancy.Booking b : dayBookings) {
          UseSummary u = uses.get(b.useId());
          if (u != null) {
            dayUses.put(u.id(), use(u, day, archivedAt.getOrDefault(u.id(), List.of())));
          }
        }
        QuarterOccupancy.Day computed =
            QuarterOccupancy.of(day, ZONE, open, dayBookings, dayUses, seatSlots);
        if (computed.opened()) {
          result.add(
              new DayQuarters(
                  facility, day, computed.quarters(), computed.uses(), computed.quarterUses()));
        }
      }
    }
    return result;
  }

  private static QuarterOccupancy.Use use(UseSummary u, LocalDate day, List<Instant> archived) {
    return new QuarterOccupancy.Use(
        u.id(),
        u.rentalMode(),
        QuarterOccupancy.livePartsOn(day, ZONE, u.parts().size(), archived),
        u.capacity(),
        u.slotMinutes());
  }
}
