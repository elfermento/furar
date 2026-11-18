// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A ocupación dun día dunha instalación por tramos fixos de 15 minutos (ADR-0034, punto 4): cada
 * tramo aberto vale a parte reservada (0–100) do uso que o ocupaba, co peso de cada modo dos
 * indicadores ({@link UseWeight}, {@link Ratio}) e co aforo e as partes que había ese día. Enteira,
 * 100 ou 0; por partes, partes reservadas entre partes vivas; por prazas, a suma das prazas
 * reservadas entre o aforo da franxa. Pechada ou bloqueada, sen valor. Os tramos cóntanse en tempo
 * real desde a medianoite: 92 o día do cambio de hora de marzo e 100 o de outubro.
 */
public final class QuarterOccupancy {

  /** A unidade: 15 minutos. */
  public static final Duration QUARTER = Duration.ofMinutes(15);

  private QuarterOccupancy() {}

  /** Un tramo aberto, en instantes (inicio incluído, fin excluído). */
  public record Open(Instant start, Instant end) {}

  /**
   * Unha reserva confirmada.
   *
   * @param partId a parte, só nos usos por partes
   * @param seats as prazas (1 fóra dos usos por prazas)
   */
  public record Booking(UUID useId, @Nullable UUID partId, Instant start, Instant end, int seats) {}

  /**
   * Un uso tal como era ese día.
   *
   * @param rentalMode {@code WHOLE}, {@code PARTS} ou {@code SEATS}
   * @param liveParts as partes vivas ese día
   * @param capacity o aforo de hoxe (só se a franxa non ten aforo conxelado)
   * @param slotMinutes a franxa do uso
   */
  public record Use(
      UUID id, String rentalMode, int liveParts, @Nullable Integer capacity, int slotMinutes) {}

  /** O aforo conxelado dunha franxa dun uso por prazas. */
  public record SeatSlot(UUID useId, Instant slotStart, int capacity) {}

  /**
   * O resultado dun día.
   *
   * @param quarters 0–100 ou {@code null} (pechada ou bloqueada)
   * @param uses os usos distintos que a ocuparon, na orde en que aparecen
   * @param quarterUses posición desde 1 en {@code uses}, ou {@code null} se estaba libre
   */
  public record Day(List<Integer> quarters, List<UUID> uses, List<Integer> quarterUses) {

    /** Se abriu algún tramo. */
    public boolean opened() {
      return quarters.stream().anyMatch(Objects::nonNull);
    }
  }

  /**
   * Calcula un día.
   *
   * @param open os tramos abertos dese día (sen os bloqueos)
   * @param bookings as confirmadas da instalación que pisan o día
   * @param uses os usos citados polas reservas, por id
   * @param seatSlots os aforos conxelados das franxas por prazas
   */
  public static Day of(
      LocalDate date,
      ZoneId zone,
      List<Open> open,
      List<Booking> bookings,
      Map<UUID, Use> uses,
      List<SeatSlot> seatSlots) {
    Instant dayStart = date.atStartOfDay(zone).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
    int count = (int) (Duration.between(dayStart, dayEnd).toMinutes() / QUARTER.toMinutes());
    List<Integer> quarters = new ArrayList<>(count);
    List<Integer> quarterUses = new ArrayList<>(count);
    Map<UUID, Integer> position = new LinkedHashMap<>();
    for (int i = 0; i < count; i++) {
      Instant qs = dayStart.plus(QUARTER.multipliedBy(i));
      Instant qe = qs.plus(QUARTER);
      if (open.stream().noneMatch(o -> !o.start().isAfter(qs) && !o.end().isBefore(qe))) {
        quarters.add(null);
        quarterUses.add(null);
        continue;
      }
      UUID best = null;
      int bestValue = 0;
      Map<UUID, List<Booking>> byUse = new LinkedHashMap<>();
      for (Booking b : bookings) {
        if (!b.start().isAfter(qs) && !b.end().isBefore(qe)) {
          byUse.computeIfAbsent(b.useId(), k -> new ArrayList<>()).add(b);
        }
      }
      for (Map.Entry<UUID, List<Booking>> e : byUse.entrySet()) {
        Use use = uses.get(e.getKey());
        int value = use == null ? 100 : value(use, e.getValue(), qs, seatSlots);
        if (best == null || value > bestValue) {
          best = e.getKey();
          bestValue = value;
        }
      }
      quarters.add(bestValue);
      if (best == null) {
        quarterUses.add(null);
      } else {
        quarterUses.add(position.computeIfAbsent(best, k -> position.size() + 1));
      }
    }
    return new Day(quarters, List.copyOf(position.keySet()), quarterUses);
  }

  /** A parte reservada dun uso nun tramo, de 0 a 100, redondeada á metade cara arriba. */
  static int value(Use use, List<Booking> covering, Instant quarterStart, List<SeatSlot> slots) {
    double part;
    double whole;
    switch (use.rentalMode()) {
      case "PARTS" -> {
        Set<UUID> parts = new HashSet<>();
        covering.forEach(b -> parts.add(b.partId() == null ? b.useId() : b.partId()));
        part = parts.size();
        whole = UseWeight.of("PARTS", use.liveParts(), null).divisor();
      }
      case "SEATS" -> {
        part = covering.stream().mapToInt(Booking::seats).sum();
        whole = seatCapacity(use, quarterStart, slots);
      }
      default -> {
        part = 1;
        whole = 1;
      }
    }
    double ratio = new Ratio(part, whole).value().orElse(0);
    return BigDecimal.valueOf(ratio * 100).setScale(0, RoundingMode.HALF_UP).intValue();
  }

  /** O aforo da franxa que contén o tramo; sen franxa conxelada, o do uso (ou 1). */
  private static int seatCapacity(Use use, Instant quarterStart, List<SeatSlot> slots) {
    Instant earliest = quarterStart.minus(Duration.ofMinutes(use.slotMinutes()));
    return slots.stream()
        .filter(s -> s.useId().equals(use.id()))
        .filter(s -> !s.slotStart().isAfter(quarterStart) && s.slotStart().isAfter(earliest))
        .max((a, b) -> a.slotStart().compareTo(b.slotStart()))
        .map(SeatSlot::capacity)
        .orElseGet(() -> UseWeight.of("SEATS", 0, use.capacity()).divisor());
  }

  /**
   * As partes vivas dun día: as vivas hoxe máis as retiradas despois do comezo dese día (a parte
   * non garda cando se creou, así que unha engadida despois conta tamén nos días anteriores).
   */
  public static int livePartsOn(
      LocalDate date, ZoneId zone, int liveToday, List<Instant> archivedAt) {
    Instant start = date.atStartOfDay(zone).toInstant();
    return liveToday + (int) archivedAt.stream().filter(a -> a.isAfter(start)).count();
  }
}
