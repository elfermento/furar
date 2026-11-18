// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.in.FacilitiesAvailability;
import gal.furar.booking.application.port.in.ReadAvailabilityUseCase;
import gal.furar.booking.application.port.in.UseAvailability;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.domain.BookableUse;
import gal.furar.booking.domain.BusySlot;
import gal.furar.booking.domain.FreeSlot;
import gal.furar.booking.domain.Occupied;
import gal.furar.booking.domain.OpenInterval;
import gal.furar.booking.domain.SlotGrid;
import gal.furar.facility.FacilityQueries;
import gal.furar.facility.FacilitySummary;
import gal.furar.facility.OpeningInterval;
import gal.furar.facility.UseSummary;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.PublicUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ocos libres para a veciñanza (ADR-0025, puntos 6, 7 e 17): le o horario xa cortado polos
 * bloqueos, os usos e a ocupación activa, e a {@link SlotGrid} fai o resto. Só le: consultar non
 * retén ningún oco. As pechadas temporalmente non teñen ocos; as que só amosan os ocos libres (sen
 * reserva por internet) si, pero sen poder reservar.
 */
@Service
public class AvailabilityService implements ReadAvailabilityUseCase {

  /** As horas que leva cada instalación da páxina. */
  static final int FIRST_STARTS = 3;

  /** Instalacións por lectura ao percorrer todas as do concello. */
  private static final int ALL_PAGE = 100;

  private static final String ACTIVE = "ACTIVE";

  private final FacilityQueries facilities;
  private final BookingRepository bookings;
  private final Clock clock;

  public AvailabilityService(FacilityQueries facilities, BookingRepository bookings, Clock clock) {
    this.facilities = facilities;
    this.bookings = bookings;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public UseAvailability ofUse(UUID facilityId, UUID useId, @Nullable LocalDate date) {
    Instant now = clock.instant();
    return ofUse(facilityId, useId, date == null ? today(now) : date, now);
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public FacilitiesAvailability ofPage(
      @Nullable LocalDate date, int page, int size, Set<String> sports) {
    Instant now = clock.instant();
    return ofPage(date == null ? today(now) : date, page, size, Set.copyOf(sports), now);
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public FacilitiesAvailability ofAllFacilities(@Nullable LocalDate date, Set<String> sports) {
    Instant now = clock.instant();
    LocalDate day = date == null ? today(now) : date;
    List<FacilitiesAvailability.Item> items = ofAll(day, Set.copyOf(sports), now);
    return new FacilitiesAvailability(day, today(now), now, 0, items.size(), items);
  }

  private UseAvailability ofUse(UUID facilityId, UUID useId, LocalDate date, Instant now) {
    FacilitySummary facility =
        facilities
            .findPublic(facilityId)
            .orElseThrow(() -> new DomainException(CommonError.NOT_FOUND));
    UseSummary use =
        facilities
            .use(useId)
            .filter(u -> u.facilityId().equals(facilityId) && !u.archived())
            .orElseThrow(() -> new DomainException(CommonError.NOT_FOUND));
    boolean active = ACTIVE.equals(facility.status());
    boolean bookable = active && facility.onlineBooking();
    List<FreeSlot> slots = List.of();
    List<BusySlot> busy = List.of();
    if (active && (facility.onlineBooking() || facility.showFreeSlots()) && open(date, now)) {
      SlotGrid grid =
          new SlotGrid(
              date,
              intervals(facilityId, date),
              bookings
                  .activeOccupancy(List.of(facilityId), dayStart(date), dayStart(date.plusDays(1)))
                  .getOrDefault(facilityId, List.of()),
              facilities.exclusiveUseOn(facilityId, date).orElse(null),
              now);
      slots = grid.freeSlots(Uses.bookable(use));
      // As ocupadas, só onde se pode reservar: quen só mira os ocos libres non ten onde esperar.
      if (bookable) {
        busy = grid.busySlots(Uses.bookable(use));
      }
    }
    return new UseAvailability(date, today(now), now, bookable, slots, busy);
  }

  private FacilitiesAvailability ofPage(
      LocalDate date, int page, int size, Set<String> sports, Instant now) {
    List<UUID> ids = facilities.visibleIds(page, size);
    Map<UUID, FacilitySummary> summaries =
        facilities.summary(ids).stream()
            .collect(Collectors.toMap(FacilitySummary::id, Function.identity()));
    List<UUID> active =
        ids.stream()
            .filter(id -> summaries.containsKey(id) && ACTIVE.equals(summaries.get(id).status()))
            .toList();
    Map<UUID, List<Occupied>> occupancy =
        active.isEmpty() || !open(date, now)
            ? Map.of()
            : bookings.activeOccupancy(active, dayStart(date), dayStart(date.plusDays(1)));
    List<FacilitiesAvailability.Item> items = new ArrayList<>();
    for (UUID id : ids) {
      if (!active.contains(id) || !open(date, now)) {
        items.add(new FacilitiesAvailability.Item(id, 0, List.of()));
        continue;
      }
      items.add(item(id, date, facilities.uses(id), sports, intervals(id, date), occupancy, now));
    }
    return new FacilitiesAvailability(date, today(now), now, page, size, items);
  }

  /**
   * Como {@link #ofPage} para todas as do concello, pero cun número fixo de lecturas: os usos e os
   * tramos abertos veñen por lote e a ocupación nunha consulta, sexan cantas sexan as instalacións.
   */
  private List<FacilitiesAvailability.Item> ofAll(LocalDate date, Set<String> sports, Instant now) {
    List<UUID> ids = new ArrayList<>();
    for (int page = 0; ; page++) {
      List<UUID> some = facilities.visibleIds(page, ALL_PAGE);
      ids.addAll(some);
      if (some.size() < ALL_PAGE) {
        break;
      }
    }
    if (ids.isEmpty()) {
      return List.of();
    }
    if (!open(date, now)) {
      return ids.stream().map(id -> new FacilitiesAvailability.Item(id, 0, List.of())).toList();
    }
    Map<UUID, FacilitySummary> summaries =
        facilities.summary(ids).stream()
            .collect(Collectors.toMap(FacilitySummary::id, Function.identity()));
    List<UUID> active =
        ids.stream()
            .filter(id -> summaries.containsKey(id) && ACTIVE.equals(summaries.get(id).status()))
            .toList();
    if (active.isEmpty()) {
      return ids.stream().map(id -> new FacilitiesAvailability.Item(id, 0, List.of())).toList();
    }
    Map<UUID, List<UseSummary>> uses =
        facilities.allUses().stream()
            .filter(use -> !use.archived())
            .collect(Collectors.groupingBy(UseSummary::facilityId));
    Map<UUID, List<OpeningInterval>> opening =
        facilities.openingIntervalsByFacility(null, date, date);
    Map<UUID, List<Occupied>> occupancy =
        bookings.activeOccupancy(active, dayStart(date), dayStart(date.plusDays(1)));
    List<FacilitiesAvailability.Item> items = new ArrayList<>();
    for (UUID id : ids) {
      if (!active.contains(id)) {
        items.add(new FacilitiesAvailability.Item(id, 0, List.of()));
        continue;
      }
      items.add(
          item(
              id,
              date,
              uses.getOrDefault(id, List.of()),
              sports,
              open(opening.getOrDefault(id, List.of())),
              occupancy,
              now));
    }
    return items;
  }

  /**
   * Os ocos libres dunha instalación e as tres primeiras horas: as horas nas que algún dos usos que
   * conta o listado ({@link #counted}) ten un oco, coa mesma grella ca a páxina de reservar ({@code
   * SlotGrid.freeSlots}) (2026-11-18).
   */
  private static FacilitiesAvailability.Item item(
      UUID id,
      LocalDate date,
      List<UseSummary> summaries,
      Set<String> sports,
      List<OpenInterval> intervals,
      Map<UUID, List<Occupied>> occupancy,
      Instant now) {
    List<BookableUse> uses = summaries.stream().map(Uses::bookable).toList();
    SlotGrid grid =
        new SlotGrid(
            date,
            intervals,
            occupancy.getOrDefault(id, List.of()),
            SlotGrid.exclusiveUse(uses, date).orElse(null),
            now);
    List<UseSummary> counted = counted(summaries, sports, today(now));
    TreeMap<Instant, Integer> starts = new TreeMap<>();
    for (UseSummary use : counted) {
      grid.freeSlots(Uses.bookable(use))
          .forEach(s -> starts.putIfAbsent(s.start(), s.startMinute()));
    }
    return new FacilitiesAvailability.Item(
        id,
        starts.size(),
        starts.entrySet().stream()
            .limit(FIRST_STARTS)
            .map(e -> new FacilitiesAvailability.Start(e.getKey(), e.getValue()))
            .toList());
  }

  /**
   * Os usos cuxos ocos conta o listado (ADR-0025, revisión do 2026-11-18): dos que aínda ofrece a
   * ficha ({@code BookableUse.listed}), na súa orde, os que teñen algún dos deportes pedidos (sen
   * deportes, todos). Unha hora conta se algún deles está libre; o primeiro é o uso co que abre a
   * páxina de reservar (`?deporte=`). Para contar só o primeiro, abondaría con {@code .limit(1)}.
   */
  static List<UseSummary> counted(List<UseSummary> uses, Set<String> sports, LocalDate today) {
    return uses.stream()
        .filter(use -> Uses.bookable(use).listed(today))
        .filter(use -> sports.isEmpty() || use.sports().stream().anyMatch(sports::contains))
        .toList();
  }

  private List<OpenInterval> intervals(UUID facilityId, LocalDate date) {
    return open(facilities.openingIntervals(facilityId, date, date));
  }

  private static List<OpenInterval> open(List<OpeningInterval> intervals) {
    return intervals.stream()
        .map(i -> new OpenInterval(i.date(), i.start(), i.end(), i.alignFromMinute()))
        .toList();
  }

  /** Un día pasado non ten ocos: non fai falta ler nada. */
  private static boolean open(LocalDate date, Instant now) {
    return !date.isBefore(today(now));
  }

  private static LocalDate today(Instant now) {
    return now.atZone(SlotGrid.ZONE).toLocalDate();
  }

  private static Instant dayStart(LocalDate date) {
    return date.atStartOfDay(SlotGrid.ZONE).toInstant();
  }
}
