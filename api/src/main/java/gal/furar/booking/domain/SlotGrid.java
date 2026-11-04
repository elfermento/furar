// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A grella dos ocos dunha instalación nun día (ADR-0025, puntos 6 e 7; plan do dominio 2, §2.3).
 * Dominio puro: o horario xa cortado polos bloqueos, a ocupación activa e o reloxo veñen de fóra.
 *
 * <ul>
 *   <li>O primeiro oco de cada tramo empeza no seu inicio ({@link OpenInterval#alignFromMinute}) e
 *       os seguintes en pasos de {@code slotMinutes}; despois dun bloqueo seguen a mesma grella. Un
 *       oco vale se cabe enteiro no anaco aberto.
 *   <li>O uso ten que valer ese día (datas e días da semana) e ningún outro uso pode bloquear os
 *       demais ese día.
 *   <li>Unha franxa ocupada por outro uso da instalación non se reserva (un uso por franxa).
 *       Enteira: libre se ninguén a ocupa; por partes: as partes libres; por prazas: as prazas que
 *       quedan.
 *   <li>Só os ocos que a antelación permite; unha franxa xa empezada nunca.
 * </ul>
 *
 * Os pasos son de tempo real: o día do cambio de hora unha franxa de 60 minutos dura 60 minutos.
 *
 * @param intervals os anacos abertos do día, xa sen os bloqueos
 * @param occupancy as ocupacións activas da instalación que pisan o día
 * @param exclusiveUseId o uso que bloquea os demais ese día, se hai
 */
public record SlotGrid(
    LocalDate date,
    List<OpenInterval> intervals,
    List<Occupied> occupancy,
    @Nullable UUID exclusiveUseId,
    Instant now) {

  /** O reloxo de parede dos ocos (§1.2.7). */
  public static final ZoneId ZONE = BookingRules.ZONE;

  /** Cantas alternativas leva un {@code booking.slot-taken}. */
  public static final int ALTERNATIVES = 3;

  public SlotGrid {
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(now, "now");
    for (OpenInterval interval : intervals) {
      if (!interval.date().equals(date)) {
        throw new IllegalArgumentException("Interval of another day: " + interval);
      }
    }
    intervals = intervals.stream().sorted(Comparator.comparing(OpenInterval::start)).toList();
    occupancy = List.copyOf(occupancy);
  }

  /**
   * O uso que bloquea os demais ese día entre os da instalación (como moito un, P2 do dominio 1).
   */
  public static Optional<UUID> exclusiveUse(List<BookableUse> uses, LocalDate date) {
    return uses.stream()
        .filter(BookableUse::blocksOthers)
        .filter(u -> u.availableOn(date))
        .map(BookableUse::id)
        .findFirst();
  }

  /** Os ocos libres do uso, en orde. */
  public List<FreeSlot> freeSlots(BookableUse use) {
    if (!use.availableOn(date)
        || (exclusiveUseId != null && !exclusiveUseId.equals(use.id()))
        || !use.rules().reaches(date, now)) {
      return List.of();
    }
    List<FreeSlot> result = new ArrayList<>();
    for (OpenInterval interval : intervals) {
      result.addAll(freeSlots(use, interval));
    }
    return List.copyOf(result);
  }

  private List<FreeSlot> freeSlots(BookableUse use, OpenInterval interval) {
    Duration step = Duration.ofMinutes(use.slotMinutes());
    List<Cell> cells = new ArrayList<>();
    for (Instant start = firstStart(interval, step);
        !start.plus(step).isAfter(interval.end());
        start = start.plus(step)) {
      cells.add(cell(use, start, start.plus(step)));
    }
    // De atrás cara adiante: cantas franxas libres seguidas quedan desde cada unha.
    int[] wholeRun = new int[cells.size() + 1];
    int[][] partRun = new int[use.parts().size()][cells.size() + 1];
    for (int i = cells.size() - 1; i >= 0; i--) {
      Cell cell = cells.get(i);
      wholeRun[i] = cell.free() ? wholeRun[i + 1] + 1 : 0;
      for (int p = 0; p < use.parts().size(); p++) {
        partRun[p][i] = cell.freeParts().contains(use.parts().get(p)) ? partRun[p][i + 1] + 1 : 0;
      }
    }
    List<FreeSlot> result = new ArrayList<>();
    for (int i = 0; i < cells.size(); i++) {
      Cell cell = cells.get(i);
      if (!cell.free() || !use.rules().allows(cell.start(), now)) {
        continue;
      }
      int run = wholeRun[i];
      if (use.rentalMode() == RentalMode.PARTS) {
        run = 0;
        for (int p = 0; p < use.parts().size(); p++) {
          run = Math.max(run, partRun[p][i]);
        }
      }
      result.add(
          new FreeSlot(
              cell.start(),
              cell.end(),
              wallMinute(cell.start()),
              use.rules().slotsUpTo(run),
              cell.freeParts(),
              cell.freeSeats()));
    }
    return result;
  }

  /**
   * Comproba, coa ocupación de agora, que se pode reservar {@code slots} franxas seguidas do uso
   * desde {@code start} e escolle a parte (ADR-0025, puntos 6 e 10). As normas (antelación e
   * duración) xa as comprobou quen chama; aquí, o horario e a ocupación.
   *
   * <ul>
   *   <li>O uso ten que valer ese día e ningún outro uso bloquear os demais: se non, {@code
   *       booking.use-not-available}.
   *   <li>O inicio ten que estar na grella dun anaco aberto e as franxas caber enteiras nel (un
   *       bloqueo corta o anaco): se non, {@code booking.slot-invalid}. Tamén unha parte que non é
   *       do uso.
   *   <li>Ninguén pode ocupar ningunha das franxas: {@code booking.slot-taken} ou, se se pediu unha
   *       parte e está collida, {@code booking.part-taken}. Sen parte («tanto me ten»), a primeira
   *       libre en todas as franxas pola orde das partes.
   * </ul>
   *
   * @return a parte asignada, só nos usos por partes
   * @throws DomainException cos códigos de arriba
   */
  public Optional<UUID> allocate(BookableUse use, Instant start, int slots, @Nullable UUID partId) {
    if (!use.availableOn(date) || (exclusiveUseId != null && !exclusiveUseId.equals(use.id()))) {
      throw new DomainException(BookingError.USE_NOT_AVAILABLE);
    }
    if (partId != null && (use.rentalMode() != RentalMode.PARTS || !use.parts().contains(partId))) {
      throw new DomainException(BookingError.SLOT_INVALID);
    }
    Duration step = Duration.ofMinutes(use.slotMinutes());
    Instant end = start.plus(step.multipliedBy(slots));
    if (intervals.stream().noneMatch(i -> onGrid(i, step, start) && !end.isAfter(i.end()))) {
      throw new DomainException(BookingError.SLOT_INVALID);
    }
    List<Cell> cells = new ArrayList<>();
    for (Instant at = start; at.isBefore(end); at = at.plus(step)) {
      cells.add(cell(use, at, at.plus(step)));
    }
    if (use.rentalMode() != RentalMode.PARTS) {
      if (cells.stream().anyMatch(c -> !c.free())) {
        throw new DomainException(BookingError.SLOT_TAKEN);
      }
      return Optional.empty();
    }
    List<UUID> free =
        use.parts().stream()
            .filter(p -> cells.stream().allMatch(c -> c.freeParts().contains(p)))
            .toList();
    if (partId != null) {
      if (!free.contains(partId)) {
        throw new DomainException(
            free.isEmpty() ? BookingError.SLOT_TAKEN : BookingError.PART_TAKEN);
      }
      return Optional.of(partId);
    }
    if (free.isEmpty()) {
      throw new DomainException(BookingError.SLOT_TAKEN);
    }
    return Optional.of(free.getFirst());
  }

  /**
   * Ata tres ocos libres do uso nese día, os máis próximos a {@code start} e en orde, nos que caben
   * {@code slots} franxas seguidas: as alternativas dun {@code booking.slot-taken} (ADR-0025, punto
   * 9).
   */
  public List<FreeSlot> alternatives(BookableUse use, Instant start, int slots) {
    return freeSlots(use).stream()
        .filter(s -> s.maxSlots() >= slots && !s.start().equals(start))
        .sorted(
            Comparator.comparing((FreeSlot s) -> Duration.between(s.start(), start).abs())
                .thenComparing(FreeSlot::start))
        .limit(ALTERNATIVES)
        .sorted(Comparator.comparing(FreeSlot::start))
        .toList();
  }

  /** Se {@code at} cae nun paso da grella do anaco. */
  private boolean onGrid(OpenInterval interval, Duration step, Instant at) {
    if (at.isBefore(interval.start()) || !at.isBefore(interval.end())) {
      return false;
    }
    Instant origin = instant(date, interval.alignFromMinute());
    return Duration.between(origin, at).toSeconds() % step.toSeconds() == 0;
  }

  /** O primeiro paso da grella do tramo que cae dentro do anaco. */
  private Instant firstStart(OpenInterval interval, Duration step) {
    Instant origin = instant(date, interval.alignFromMinute());
    long behind = Duration.between(origin, interval.start()).toSeconds();
    long stepSeconds = step.toSeconds();
    long steps = behind <= 0 ? 0 : Math.ceilDiv(behind, stepSeconds);
    return origin.plusSeconds(steps * stepSeconds);
  }

  private Cell cell(BookableUse use, Instant start, Instant end) {
    boolean otherUse =
        occupancy.stream().anyMatch(o -> !o.useId().equals(use.id()) && o.overlaps(start, end));
    List<Occupied> mine =
        occupancy.stream()
            .filter(o -> o.useId().equals(use.id()) && o.overlaps(start, end))
            .toList();
    return switch (use.rentalMode()) {
      case WHOLE -> new Cell(start, end, !otherUse && mine.isEmpty(), List.of(), null);
      case PARTS -> {
        List<UUID> free =
            otherUse
                ? List.of()
                : use.parts().stream()
                    .filter(p -> mine.stream().noneMatch(o -> p.equals(o.partId())))
                    .toList();
        yield new Cell(start, end, !free.isEmpty(), free, null);
      }
      case SEATS -> {
        int capacity = Objects.requireNonNull(use.capacity(), "capacity");
        int seats = otherUse ? 0 : Math.max(0, capacity - mine.size());
        yield new Cell(start, end, seats > 0, List.of(), seats);
      }
    };
  }

  /**
   * O instante dun minuto de reloxo de parede, coa regra do horario: unha hora que non existe é o
   * instante do salto; unha que se repite, a primeira vez.
   */
  static Instant instant(LocalDate date, int minute) {
    LocalDateTime local = date.atStartOfDay().plusMinutes(minute);
    ZoneOffsetTransition transition = ZONE.getRules().getTransition(local);
    if (transition != null && transition.isGap()) {
      return transition.getInstant();
    }
    return ZonedDateTime.of(local, ZONE).toInstant();
  }

  /** O minuto de reloxo de parede (0–1439) dun instante. */
  static int wallMinute(Instant at) {
    ZonedDateTime local = at.atZone(ZONE);
    return local.getHour() * 60 + local.getMinute();
  }

  /** Unha franxa da grella e o que queda libre nela. */
  private record Cell(
      Instant start,
      Instant end,
      boolean free,
      List<UUID> freeParts,
      @Nullable Integer freeSeats) {}
}
