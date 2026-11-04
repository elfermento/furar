// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Período do límite por persoa (plan do dominio 2, §2.3; P4): natural, en hora de Galicia. A semana
 * vai de luns a domingo.
 */
public enum CapPeriod {
  DAY,
  WEEK,
  MONTH;

  /** O período natural que contén {@code at}, como intervalo {@code [from, to)}. */
  public TimeWindow around(Instant at, ZoneId zone) {
    LocalDate day = at.atZone(zone).toLocalDate();
    LocalDate from =
        switch (this) {
          case DAY -> day;
          case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
          case MONTH -> day.withDayOfMonth(1);
        };
    LocalDate to =
        switch (this) {
          case DAY -> from.plusDays(1);
          case WEEK -> from.plusWeeks(1);
          case MONTH -> from.plusMonths(1);
        };
    return new TimeWindow(from.atStartOfDay(zone).toInstant(), to.atStartOfDay(zone).toInstant());
  }
}
