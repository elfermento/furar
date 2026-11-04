// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Os minutos abertos (o denominador da ocupación; plan do dominio 3, §1.2.4): en total, por día e
 * por día da semana ISO × hora, en Europe/Madrid. Sae dos instantes reais de cada tramo, cortados
 * en cada cambio de hora de reloxo: o 29 de marzo, de 0:00 a 4:00 son tres horas (a das 2 non
 * existe) e o 25 de outubro a hora das 2 conta dúas veces. Os tramos de varias instalacións
 * súmanse.
 */
public final class OpeningMinutes {

  private static final int HOURS = 24;

  private final long total;
  private final Map<LocalDate, Long> perDay;
  private final long[] perWeekdayHour;

  private OpeningMinutes(long total, Map<LocalDate, Long> perDay, long[] perWeekdayHour) {
    this.total = total;
    this.perDay = perDay;
    this.perWeekdayHour = perWeekdayHour;
  }

  /** Suma os tramos. */
  public static OpeningMinutes of(Collection<TimeSpan> spans) {
    Map<LocalDate, Long> perDay = new TreeMap<>();
    long[] perWeekdayHour = new long[DayOfWeek.values().length * HOURS];
    long total = 0;
    for (TimeSpan span : spans) {
      Instant t = span.start();
      while (t.isBefore(span.end())) {
        ZonedDateTime local = t.atZone(ReportPeriod.ZONE);
        // A seguinte hora en punto do reloxo, no eixo real: plusHours non salta o cambio de hora.
        Instant next = local.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant();
        Instant chunkEnd = next.isBefore(span.end()) ? next : span.end();
        long minutes = Duration.between(t, chunkEnd).toMinutes();
        total += minutes;
        perDay.merge(local.toLocalDate(), minutes, Long::sum);
        perWeekdayHour[index(local.getDayOfWeek(), local.getHour())] += minutes;
        t = chunkEnd;
      }
    }
    return new OpeningMinutes(total, Map.copyOf(perDay), perWeekdayHour);
  }

  /** Todos os minutos abertos. */
  public long total() {
    return total;
  }

  /** Os minutos abertos dese día (0 se non abre). */
  public long onDay(LocalDate date) {
    return perDay.getOrDefault(date, 0L);
  }

  /** Os días con minutos abertos. */
  public Map<LocalDate, Long> perDay() {
    return perDay;
  }

  /** Os minutos abertos dese día da semana e hora de reloxo (0–23), sumando todas as semanas. */
  public long at(DayOfWeek day, int hour) {
    if (hour < 0 || hour >= HOURS) {
      throw new IllegalArgumentException("Hour out of range: " + hour);
    }
    return perWeekdayHour[index(day, hour)];
  }

  private static int index(DayOfWeek day, int hour) {
    return (day.getValue() - 1) * HOURS + hour;
  }
}
