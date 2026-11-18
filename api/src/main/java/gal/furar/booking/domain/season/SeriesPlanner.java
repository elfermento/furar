// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain.season;

import gal.furar.booking.domain.BookableUse;
import gal.furar.booking.domain.RentalMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Que sesións crea unha tempada (ADR-0039, punto 8). Puro e sen reloxo: o caso de uso dálle o
 * calendario da instalación, as ocupacións activas e o instante de agora. Cada data da tempada que
 * aínda non empezou cae nunha clase, nesta orde: festivo (teña ou non horario propio), uso non
 * vixente, pechado (ou a sesión cae enteira na hora que non existe o día do cambio de hora), outro
 * uso exclusivo, fóra de horario, bloqueo, reserva ou sesión que a pisa, e libre. Só as libres se
 * crean; nunca se pisa nada.
 */
public final class SeriesPlanner {

  private SeriesPlanner() {}

  /** Un tramo aberto do día, sen cortar polos bloqueos. */
  public record Span(Instant start, Instant end) {

    public Span {
      Objects.requireNonNull(start, "start");
      Objects.requireNonNull(end, "end");
    }
  }

  /** Un bloqueo que pisa o día, co seu motivo. */
  public record Block(Instant start, Instant end, @Nullable String reason) {

    public Block {
      Objects.requireNonNull(start, "start");
      Objects.requireNonNull(end, "end");
    }
  }

  /**
   * Un día do calendario da instalación.
   *
   * @param exclusiveUseId o uso que bloquea os demais ese día, se o hai
   */
  public record Day(
      LocalDate date,
      boolean holiday,
      List<Span> open,
      List<Block> blocks,
      @Nullable UUID exclusiveUseId) {

    public Day {
      Objects.requireNonNull(date, "date");
      open = List.copyOf(open);
      blocks = List.copyOf(blocks);
    }
  }

  /**
   * Unha ocupación activa da instalación.
   *
   * @param name de quen é (o nome do titular ou da entidade)
   * @param session se é unha sesión doutra tempada
   */
  public record Occupant(
      UUID bookingId,
      UUID useId,
      @Nullable UUID partId,
      Instant start,
      Instant end,
      @Nullable String name,
      boolean session) {

    public Occupant {
      Objects.requireNonNull(bookingId, "bookingId");
      Objects.requireNonNull(useId, "useId");
      Objects.requireNonNull(start, "start");
      Objects.requireNonNull(end, "end");
    }
  }

  /**
   * As sesións e os días que non se crean.
   *
   * @param schedule o horario
   * @param use o uso da tempada (as súas datas e días)
   * @param days o calendario da instalación nas datas da tempada
   * @param occupants as ocupacións activas da instalación nese rango
   * @param useNames os nomes dos usos (o exclusivo de cada día)
   */
  public static SeriesPlan plan(
      SeriesSchedule schedule,
      BookableUse use,
      List<Day> days,
      List<Occupant> occupants,
      Map<UUID, String> useNames,
      Instant now) {
    Map<LocalDate, Day> byDate =
        days.stream().collect(Collectors.toMap(Day::date, Function.identity(), (a, b) -> a));
    List<SeriesPlan.Session> sessions = new ArrayList<>();
    List<LocalDate> holidays = new ArrayList<>();
    List<LocalDate> useOff = new ArrayList<>();
    List<LocalDate> closed = new ArrayList<>();
    List<SeriesConflict> conflicts = new ArrayList<>();
    for (LocalDate date : schedule.dates()) {
      Instant start = schedule.start(date).toInstant();
      Instant end = schedule.end(date).toInstant();
      if (!start.isAfter(now)) {
        continue;
      }
      Day day = byDate.getOrDefault(date, new Day(date, false, List.of(), List.of(), null));
      if (day.holiday()) {
        holidays.add(date);
      } else if (!use.availableOn(date)) {
        useOff.add(date);
      } else if (day.open().isEmpty() || !end.isAfter(start) || wholeInGap(schedule, date)) {
        closed.add(date);
      } else if (day.exclusiveUseId() != null && !day.exclusiveUseId().equals(use.id())) {
        conflicts.add(
            new SeriesConflict(
                date,
                start,
                end,
                SeriesConflict.Kind.OTHER_USE_EXCLUSIVE,
                null,
                null,
                useNames.get(day.exclusiveUseId())));
      } else if (day.open().stream()
          .noneMatch(s -> !s.start().isAfter(start) && !s.end().isBefore(end))) {
        conflicts.add(
            new SeriesConflict(
                date, start, end, SeriesConflict.Kind.OUT_OF_HOURS, null, null, null));
      } else {
        List<SeriesConflict> found = new ArrayList<>();
        for (Block b : day.blocks()) {
          if (overlaps(b.start(), b.end(), start, end)) {
            found.add(
                new SeriesConflict(
                    date, b.start(), b.end(), SeriesConflict.Kind.BLOCK, null, null, b.reason()));
          }
        }
        if (found.isEmpty()) {
          for (Occupant o : occupants) {
            if (overlaps(o.start(), o.end(), start, end) && collides(o, use, schedule.partId())) {
              found.add(
                  new SeriesConflict(
                      date,
                      o.start(),
                      o.end(),
                      o.session() ? SeriesConflict.Kind.SESSION : SeriesConflict.Kind.BOOKING,
                      o.bookingId(),
                      o.name(),
                      null));
            }
          }
        }
        if (found.isEmpty()) {
          sessions.add(new SeriesPlan.Session(date, start, end));
        } else {
          conflicts.addAll(found);
        }
      }
    }
    return new SeriesPlan(sessions, holidays, useOff, closed, conflicts);
  }

  /**
   * A ocupación impide a sesión coas exclusións da base: outro uso da instalación sempre; o mesmo
   * uso, enteiro sempre e por partes só na mesma parte.
   */
  private static boolean collides(Occupant o, BookableUse use, @Nullable UUID partId) {
    if (!o.useId().equals(use.id())) {
      return true;
    }
    return use.rentalMode() != RentalMode.PARTS || Objects.equals(o.partId(), partId);
  }

  /** A sesión enteira na hora que non existe o día do cambio de hora. */
  private static boolean wholeInGap(SeriesSchedule schedule, LocalDate date) {
    if (!SeriesSchedule.inGap(date, schedule.startMinute())) {
      return false;
    }
    int lastMinute = schedule.endMinute() - SeriesSchedule.STEP;
    return SeriesSchedule.inGap(date, lastMinute);
  }

  private static boolean overlaps(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
    return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
  }
}
