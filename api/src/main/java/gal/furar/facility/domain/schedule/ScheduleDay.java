// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import java.time.DayOfWeek;

/**
 * Fila da semana dunha tempada: de luns a domingo e a fila 8, festivos (especificación, bloque 2).
 * Na base é o número {@link #number()}.
 */
public enum ScheduleDay {
  MONDAY(1),
  TUESDAY(2),
  WEDNESDAY(3),
  THURSDAY(4),
  FRIDAY(5),
  SATURDAY(6),
  SUNDAY(7),
  /** Os festivos usan esta fila (por defecto, a do domingo). */
  HOLIDAYS(8);

  private final int number;

  ScheduleDay(int number) {
    this.number = number;
  }

  public int number() {
    return number;
  }

  public static ScheduleDay of(int number) {
    for (ScheduleDay day : values()) {
      if (day.number == number) {
        return day;
      }
    }
    throw new IllegalArgumentException("day " + number);
  }

  /** A fila dun día da semana (sen mirar se é festivo). */
  public static ScheduleDay of(DayOfWeek day) {
    return of(day.getValue());
  }
}
