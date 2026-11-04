// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Os días da semana dun uso como máscara de bits (plan do dominio 2, §2.1): luns = 1, martes = 2 …
 * domingo = 64; todos = 127. A mesma máscara viaxa na API e na web.
 */
public final class Weekdays {

  public static final int ALL = 127;
  public static final int MIN = 1;

  private Weekdays() {}

  /** O bit dun día. */
  public static int bit(DayOfWeek day) {
    return 1 << (day.getValue() - 1);
  }

  public static boolean valid(int mask) {
    return mask >= MIN && mask <= ALL;
  }

  public static boolean includes(int mask, LocalDate date) {
    return (mask & bit(date.getDayOfWeek())) != 0;
  }

  /** Se algún día entre as dúas datas (incluídas) é dun día da máscara. */
  public static boolean anyBetween(int mask, LocalDate from, LocalDate to) {
    if (!from.plusDays(6).isAfter(to)) {
      return mask != 0;
    }
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      if (includes(mask, d)) {
        return true;
      }
    }
    return false;
  }
}
