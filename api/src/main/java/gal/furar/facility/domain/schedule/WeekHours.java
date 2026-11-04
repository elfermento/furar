// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.ValidationError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Semana dunha tempada: os tramos de cada unha das 8 filas (luns a domingo e festivos), ordenados.
 * Un día sen tramos está pechado. Inmutable (A13); só se constrúe con tramos válidos e sen solapes
 * (especificación, bloque 2: «fin despois do inicio, sen pasar da medianoite, sen tramos
 * solapados»).
 */
public final class WeekHours {

  /** Tramos como máximo nun día. */
  public static final int INTERVALS_MAX = 10;

  private static final WeekHours CLOSED = new WeekHours(new EnumMap<>(ScheduleDay.class));

  private final Map<ScheduleDay, List<TimeRange>> days;

  private WeekHours(Map<ScheduleDay, List<TimeRange>> days) {
    EnumMap<ScheduleDay, List<TimeRange>> copy = new EnumMap<>(ScheduleDay.class);
    for (ScheduleDay day : ScheduleDay.values()) {
      List<TimeRange> list = new ArrayList<>(days.getOrDefault(day, List.of()));
      Collections.sort(list);
      copy.put(day, List.copyOf(list));
    }
    this.days = Collections.unmodifiableMap(copy);
  }

  /** Semana toda pechada (tempada nova sen copiar doutra). */
  public static WeekHours closed() {
    return CLOSED;
  }

  /**
   * Semana enteira tal como a escribe o xestor: as filas que non veñen quedan pechadas. Os erros
   * saen xuntos, cada un co seu campo ({@code week[i].intervals[j]}): primeiro as filas mal
   * formadas ({@code facility.season-invalid}), despois os tramos non válidos ({@code
   * facility.interval-invalid}) e por último os que se pisan ({@code facility.interval-overlap}).
   */
  public static WeekHours of(List<DayHoursSpec> week) {
    List<DayHoursSpec> rows = week == null ? List.of() : week;
    List<FieldViolation> malformed = new ArrayList<>();
    List<FieldViolation> invalid = new ArrayList<>();
    List<FieldViolation> overlapping = new ArrayList<>();
    EnumMap<ScheduleDay, List<TimeRange>> days = new EnumMap<>(ScheduleDay.class);
    for (int i = 0; i < rows.size(); i++) {
      DayHoursSpec row = rows.get(i);
      String field = "week[" + i + "]";
      if (row == null || row.day() == null) {
        malformed.add(FieldViolation.of(field + ".day", ValidationError.REQUIRED));
        continue;
      }
      if (days.containsKey(row.day())) {
        malformed.add(FieldViolation.of(field + ".day", ValidationError.INVALID));
        continue;
      }
      List<TimeRange> intervals = row.intervals();
      if (intervals.size() > INTERVALS_MAX) {
        malformed.add(
            FieldViolation.of(
                field + ".intervals",
                ValidationError.SIZE,
                Map.of("min", 0, "max", INTERVALS_MAX)));
      }
      for (int j = 0; j < intervals.size(); j++) {
        TimeRange interval = intervals.get(j);
        if (interval == null || !interval.isValid()) {
          invalid.add(
              FieldViolation.of(
                  field + ".intervals[" + j + "]",
                  ScheduleError.INTERVAL_INVALID,
                  Map.of("step", TimeRange.STEP)));
          continue;
        }
        for (int k = 0; k < j; k++) {
          TimeRange before = intervals.get(k);
          if (before != null && before.isValid() && before.overlaps(interval)) {
            overlapping.add(
                FieldViolation.of(field + ".intervals[" + j + "]", ScheduleError.INTERVAL_OVERLAP));
            break;
          }
        }
      }
      days.put(row.day(), intervals);
    }
    if (!malformed.isEmpty()) {
      throw new DomainException(ScheduleError.SEASON_INVALID, Map.of(), malformed);
    }
    if (!invalid.isEmpty()) {
      throw new DomainException(
          ScheduleError.INTERVAL_INVALID, Map.of("step", TimeRange.STEP), invalid);
    }
    if (!overlapping.isEmpty()) {
      throw new DomainException(ScheduleError.INTERVAL_OVERLAP, Map.of(), overlapping);
    }
    return new WeekHours(days);
  }

  /** Filas gardadas, sen comprobar de novo (xa as comprobou o dominio e a base). */
  public static WeekHours rehydrate(Map<ScheduleDay, List<TimeRange>> days) {
    return new WeekHours(days);
  }

  /**
   * A mesma semana coa fila de festivos igual á do domingo: así se enche ao crear ou copiar unha
   * tempada (plan §2.1; non hai outra marca).
   */
  public WeekHours withHolidaysLikeSunday() {
    EnumMap<ScheduleDay, List<TimeRange>> copy = new EnumMap<>(days);
    copy.put(ScheduleDay.HOLIDAYS, days.get(ScheduleDay.SUNDAY));
    return new WeekHours(copy);
  }

  /** Os tramos dunha fila, ordenados; baleiro = pechado. */
  public List<TimeRange> hours(ScheduleDay day) {
    return days.get(day);
  }

  /** As 8 filas, na orde de {@link ScheduleDay}. */
  public Map<ScheduleDay, List<TimeRange>> days() {
    return days;
  }

  /** Se algunha fila ten algún tramo (P8: unha tempada «con horario»). */
  public boolean hasAnyInterval() {
    return days.values().stream().anyMatch(list -> !list.isEmpty());
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof WeekHours other && days.equals(other.days);
  }

  @Override
  public int hashCode() {
    return Objects.hash(days);
  }

  @Override
  public String toString() {
    return "WeekHours" + days;
  }
}
