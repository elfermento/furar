// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Peche dun día ou de varios seguidos que ve a veciñanza na ficha pública («Próximos peches»,
 * maqueta 54): unha excepción pechada, un festivo que pecha a instalación (a fila «Festivos» da
 * tempada está baleira e a do día da semana non) ou un bloqueo. Só o público: datas, horas e o
 * motivo; nunca quen o puxo.
 *
 * @param from primeiro día (incluído)
 * @param to último día (incluído)
 * @param hours as horas pechadas dun bloqueo de parte do día; {@code null} = todo o día
 * @param reason motivo da excepción ou do bloqueo, se o ten
 * @param holiday o festivo dese día, se o é (só nun peche dun día)
 */
public record Closure(
    LocalDate from,
    LocalDate to,
    @Nullable TimeRange hours,
    @Nullable String reason,
    @Nullable PublicHoliday holiday) {

  /** O día enteiro en minutos: un bloqueo así pecha todo o día (P13). */
  private static final TimeRange WHOLE_DAY = new TimeRange(0, 24 * 60);

  public Closure {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
  }

  /**
   * Os peches entre as dúas datas (incluídas), en orde. Os días seguidos co mesmo motivo e as
   * mesmas horas xúntanse nunha liña; os festivos van cada un na súa. Un bloqueo nun día que xa
   * está pechado non se repite.
   *
   * @param hours o horario resolto coas tempadas, as excepcións e os festivos do rango
   * @param holidays os festivos do rango
   * @param exceptions as excepcións do rango
   * @param blocks os bloqueos do rango
   */
  public static List<Closure> between(
      OpeningHours hours,
      Collection<PublicHoliday> holidays,
      Collection<ScheduleException> exceptions,
      Collection<Block> blocks,
      LocalDate from,
      LocalDate to) {
    Map<LocalDate, PublicHoliday> holidayOf = new HashMap<>();
    holidays.forEach(h -> holidayOf.put(h.date(), h));
    Map<LocalDate, ScheduleException> exceptionOf = new HashMap<>();
    exceptions.forEach(e -> exceptionOf.put(e.date(), e));
    Map<LocalDate, List<Block>> blocksOf = new HashMap<>();
    blocks.forEach(b -> blocksOf.computeIfAbsent(b.date(), k -> new ArrayList<>()).add(b));

    List<Closure> days = new ArrayList<>();
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      PublicHoliday holiday = holidayOf.get(date);
      ScheduleException exception = exceptionOf.get(date);
      if (exception != null && !exception.isOpen()) {
        days.add(new Closure(date, date, null, exception.reason(), holiday));
        continue;
      }
      if (exception == null && holiday != null && closedByHoliday(hours, date)) {
        days.add(new Closure(date, date, null, null, holiday));
        continue;
      }
      if (hours.on(date).isEmpty()) {
        continue;
      }
      blocksOf.getOrDefault(date, List.of()).stream()
          .sorted(Comparator.comparing(Block::interval))
          .forEach(
              b ->
                  days.add(
                      new Closure(
                          b.date(),
                          b.date(),
                          b.interval().equals(WHOLE_DAY) ? null : b.interval(),
                          b.reason(),
                          null)));
    }
    return merged(days);
  }

  /** O festivo pecha ese día: a tempada abriría coa fila do día da semana e non coa de festivos. */
  private static boolean closedByHoliday(OpeningHours hours, LocalDate date) {
    return hours.withoutException(date).isEmpty() && !hours.weekdayHours(date).isEmpty();
  }

  private static List<Closure> merged(List<Closure> days) {
    List<Closure> result = new ArrayList<>();
    for (Closure day : days) {
      Closure last = result.isEmpty() ? null : result.getLast();
      if (last != null
          && last.holiday == null
          && day.holiday == null
          && last.to.plusDays(1).equals(day.from)
          && Objects.equals(last.hours, day.hours)
          && Objects.equals(last.reason, day.reason)) {
        result.set(
            result.size() - 1, new Closure(last.from, day.to, last.hours, last.reason, null));
      } else {
        result.add(day);
      }
    }
    return List.copyOf(result);
  }
}
