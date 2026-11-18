// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import java.util.List;

/** Unha fila da semana tal como chega, sen comprobar ({@link WeekHours#of(List)} valídaa). */
public record DayHoursSpec(ScheduleDay day, List<TimeRange> intervals) {

  public DayHoursSpec {
    intervals = intervals == null ? List.of() : List.copyOf(intervals);
  }
}
