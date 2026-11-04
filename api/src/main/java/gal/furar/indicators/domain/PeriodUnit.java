// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;

/**
 * Unidade de tempo dos indicadores (plan do dominio 3, §1.2.3): día, semana ISO (de luns a
 * domingo), mes, trimestre e ano naturais. As datas son de Europe/Madrid.
 */
public enum PeriodUnit {
  DAY,
  WEEK,
  MONTH,
  QUARTER,
  YEAR;

  /** O primeiro día da unidade que contén a data. */
  public LocalDate start(LocalDate date) {
    return switch (this) {
      case DAY -> date;
      case WEEK -> date.with(DayOfWeek.MONDAY);
      case MONTH -> date.withDayOfMonth(1);
      case QUARTER -> date.with(IsoFields.DAY_OF_QUARTER, 1);
      case YEAR -> date.withDayOfYear(1);
    };
  }

  /** A data movida {@code n} unidades (negativo cara atrás). */
  public LocalDate plus(LocalDate date, long n) {
    return switch (this) {
      case DAY -> date.plusDays(n);
      case WEEK -> date.plusWeeks(n);
      case MONTH -> date.plusMonths(n);
      case QUARTER -> date.plusMonths(3 * n);
      case YEAR -> date.plusYears(n);
    };
  }

  /** O último día (incluído) da unidade que contén a data. */
  public LocalDate end(LocalDate date) {
    return plus(start(date), 1).minusDays(1);
  }
}
