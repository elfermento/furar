// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import gal.furar.facility.domain.schedule.Season.DateSpan;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Horario resolto dunha instalación (especificación, bloque 2): que tramos ten aberto cada día. A
 * prioridade é excepción &gt; festivo &gt; semana: unha excepción manda sobre todo; se non a hai,
 * un festivo de Galicia usa a fila «Festivos» da tempada que cobre o día; se non, a fila do día da
 * semana. Sen tempada nin excepción, o día está pechado. Os bloqueos réstanse só nos tramos reais
 * ({@link #slots(LocalDate, LocalDate, ZoneId, Collection)}, D1-16): a ficha amósaos á parte.
 * Inmutable e sen estado: constrúese coas tempadas, as excepcións e os festivos do rango que se
 * mira.
 */
public final class OpeningHours {

  /** O horario é de reloxo de parede en Europe/Madrid (ADR-0022, punto 8). */
  public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

  private final List<Season> seasons;
  private final Map<LocalDate, ScheduleException> exceptions;
  private final Set<LocalDate> holidays;

  public OpeningHours(
      List<Season> seasons, Collection<ScheduleException> exceptions, Set<LocalDate> holidays) {
    this.seasons = List.copyOf(seasons);
    Map<LocalDate, ScheduleException> byDate = new HashMap<>();
    exceptions.forEach(e -> byDate.put(e.date(), e));
    this.exceptions = Map.copyOf(byDate);
    this.holidays = Set.copyOf(holidays);
  }

  /** Os tramos dese día, ordenados; baleiro = pechado. */
  public List<TimeRange> on(LocalDate date) {
    ScheduleException exception = exceptions.get(date);
    if (exception != null) {
      return exception.hours();
    }
    return withoutException(date);
  }

  /** Os tramos dese día se non houbese excepción: festivo e despois semana da tempada. */
  public List<TimeRange> withoutException(LocalDate date) {
    for (Season season : seasons) {
      if (season.covers(date)) {
        ScheduleDay row =
            holidays.contains(date) ? ScheduleDay.HOLIDAYS : ScheduleDay.of(date.getDayOfWeek());
        return season.week().hours(row);
      }
    }
    return List.of();
  }

  /**
   * Os tramos da fila do día da semana na tempada que cobre o día, sexa ou non festivo e sen
   * excepcións; sen tempada, ningún.
   */
  public List<TimeRange> weekdayHours(LocalDate date) {
    for (Season season : seasons) {
      if (season.covers(date)) {
        return season.week().hours(ScheduleDay.of(date.getDayOfWeek()));
      }
    }
    return List.of();
  }

  /**
   * Os tramos abertos entre as dúas datas (incluídas), en orde, como instantes reais de
   * Europe/Madrid (ADR-0022, punto 8). Nos cambios de hora o tramo dura o que dura no reloxo real:
   * unha hora que non existe (de 2:00 a 3:00 o último domingo de marzo) é o instante do salto, e
   * unha hora que se repite (de 2:00 a 3:00 o último domingo de outubro) toma a primeira vez; un
   * tramo que queda baleiro co salto non sae.
   */
  public List<Slot> slots(LocalDate from, LocalDate to, ZoneId zone) {
    return slots(from, to, zone, List.of());
  }

  /**
   * Coma {@link #slots(LocalDate, LocalDate, ZoneId)}, pero sen as horas dos bloqueos (D1-16): un
   * tramo que un bloqueo corta polo medio sae en dous anacos. Como cada bloqueo é dun día e se pasa
   * a instantes coa mesma regra ca o horario ({@link #instant}), restar en minutos dese día é o
   * mesmo que restar os instantes reais, tamén nos cambios de hora.
   */
  public List<Slot> slots(LocalDate from, LocalDate to, ZoneId zone, Collection<Block> blocks) {
    Map<LocalDate, List<TimeRange>> blocked = new HashMap<>();
    blocks.forEach(
        b -> blocked.computeIfAbsent(b.date(), k -> new ArrayList<>()).add(b.interval()));
    List<Slot> result = new ArrayList<>();
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      List<TimeRange> cuts = blocked.getOrDefault(date, List.of());
      // Tramo a tramo: cada anaco leva o inicio do tramo enteiro, a orixe da grella dos ocos.
      for (TimeRange range : on(date).stream().sorted().toList()) {
        result.addAll(slotsOf(date, minus(List.of(range), cuts), zone, range.startMinute()));
      }
    }
    return List.copyOf(result);
  }

  /**
   * Os tramos que {@code before} tiña abertos entre as dúas datas (incluídas) e este horario xa non
   * (P6), en orde e cos instantes reais: o que pode deixar reservas fóra. Acurtar unha tempada
   * polos dous extremos só perde os días de cada extremo; cambiar a semana, só as horas que se
   * quitan.
   */
  public List<Slot> lostSince(OpeningHours before, LocalDate from, LocalDate to, ZoneId zone) {
    List<Slot> result = new ArrayList<>();
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      result.addAll(lost(date, before.on(date), on(date), zone));
    }
    return List.copyOf(result);
  }

  /**
   * Se {@code after} deixa fóra algún minuto que {@code before} tiña aberto (P6). Os dous, listas
   * de tramos dun día sen solapes.
   */
  public static boolean losesHours(List<TimeRange> before, List<TimeRange> after) {
    return !minus(before, after).isEmpty();
  }

  /** Os tramos dun día que {@code before} tiña abertos e {@code after} non, en orde. */
  public static List<Slot> lost(
      LocalDate date, List<TimeRange> before, List<TimeRange> after, ZoneId zone) {
    return slotsOf(date, minus(before, after), zone);
  }

  private static List<Slot> slotsOf(LocalDate date, List<TimeRange> ranges, ZoneId zone) {
    List<Slot> result = new ArrayList<>();
    for (TimeRange range : ranges) {
      result.addAll(slotsOf(date, List.of(range), zone, range.startMinute()));
    }
    return result;
  }

  private static List<Slot> slotsOf(
      LocalDate date, List<TimeRange> ranges, ZoneId zone, int alignFromMinute) {
    List<Slot> result = new ArrayList<>();
    for (TimeRange range : ranges) {
      Instant start = instant(date, range.startMinute(), zone);
      Instant end = instant(date, range.endMinute(), zone);
      if (start.isBefore(end)) {
        result.add(new Slot(date, range, start, end, alignFromMinute));
      }
    }
    return result;
  }

  /**
   * O instante dun minuto de reloxo de parede (1440 = medianoite do día seguinte). Unha hora que
   * non existe é o instante do salto (o reloxo pasa de 1:59 a 3:00: «ata as 2:30» remata ás 3:00);
   * unha que se repite, a primeira vez.
   */
  public static Instant instant(LocalDate date, int minute, ZoneId zone) {
    LocalDateTime local = date.atStartOfDay().plusMinutes(minute);
    ZoneOffsetTransition transition = zone.getRules().getTransition(local);
    if (transition != null && transition.isGap()) {
      return transition.getInstant();
    }
    return ZonedDateTime.of(local, zone).toInstant();
  }

  /**
   * Os días entre as dúas datas (incluídas) que ningunha tempada cobre, como rangos ordenados
   * (especificación, bloque 2: «avísase se quedan días sen tempada»). As excepcións non contan: un
   * día sen tempada segue sen tempada aínda que teña excepción.
   */
  public List<DateSpan> gaps(LocalDate from, LocalDate to) {
    List<DateSpan> result = new ArrayList<>();
    LocalDate gapStart = null;
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      LocalDate day = date;
      boolean covered = seasons.stream().anyMatch(s -> s.covers(day));
      if (!covered && gapStart == null) {
        gapStart = date;
      } else if (covered && gapStart != null) {
        result.add(new DateSpan(gapStart, date.minusDays(1)));
        gapStart = null;
      }
    }
    if (gapStart != null) {
      result.add(new DateSpan(gapStart, to));
    }
    return List.copyOf(result);
  }

  /**
   * Os tramos de {@code open} sen os minutos de {@code removed}, ordenados (inicio incluído e fin
   * excluído, coma sempre). Os de {@code open} non se pisan entre si; os de {@code removed} poden.
   */
  public static List<TimeRange> minus(List<TimeRange> open, List<TimeRange> removed) {
    if (removed.isEmpty()) {
      return open.stream().sorted().toList();
    }
    List<TimeRange> cuts = removed.stream().sorted().toList();
    List<TimeRange> result = new ArrayList<>();
    for (TimeRange range : open.stream().sorted().toList()) {
      int cursor = range.startMinute();
      for (TimeRange cut : cuts) {
        if (cut.endMinute() <= cursor || cut.startMinute() >= range.endMinute()) {
          continue;
        }
        if (cut.startMinute() > cursor) {
          result.add(new TimeRange(cursor, cut.startMinute()));
        }
        cursor = Math.max(cursor, cut.endMinute());
      }
      if (cursor < range.endMinute()) {
        result.add(new TimeRange(cursor, range.endMinute()));
      }
    }
    return List.copyOf(result);
  }

  /**
   * Un tramo aberto nun día, co seu reloxo de parede e os seus instantes reais. {@code
   * alignFromMinute}: o inicio do tramo do horario do que sae (un bloqueo pode cortalo polo
   * principio).
   */
  public record Slot(
      LocalDate date, TimeRange range, Instant start, Instant end, int alignFromMinute) {

    public Slot {
      Objects.requireNonNull(date, "date");
      Objects.requireNonNull(range, "range");
      Objects.requireNonNull(start, "start");
      Objects.requireNonNull(end, "end");
    }

    public Slot(LocalDate date, TimeRange range, Instant start, Instant end) {
      this(date, range, start, end, range.startMinute());
    }
  }
}
