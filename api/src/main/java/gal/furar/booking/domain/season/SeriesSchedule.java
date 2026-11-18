// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain.season;

import gal.furar.booking.domain.BookingError;
import gal.furar.shared.domain.DomainException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * O horario fixo dunha tempada ou dunha solicitude (ADR-0039, punto 8): instalación, uso, parte,
 * días da semana, horas de reloxo de parede en Europe/Madrid (de 15 en 15 minutos; {@code 1440} é a
 * medianoite do día seguinte) e datas, os dous extremos incluídos. Cada semana. A forma compróbaa
 * {@link #check}; as clases de cada día, {@link SeriesPlanner}.
 */
public record SeriesSchedule(
    UUID facilityId,
    UUID useId,
    @Nullable UUID partId,
    SortedSet<DayOfWeek> weekdays,
    int startMinute,
    int endMinute,
    LocalDate firstDate,
    LocalDate lastDate) {

  /** As horas da tempada: as da veciñanza. */
  public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

  /** Días como moito entre a primeira e a última data. */
  public static final int MAX_DAYS = 365;

  /** Os minutos van de 15 en 15. */
  public static final int STEP = 15;

  public SeriesSchedule {
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(useId, "useId");
    Objects.requireNonNull(firstDate, "firstDate");
    Objects.requireNonNull(lastDate, "lastDate");
    weekdays =
        Collections.unmodifiableSortedSet(
            new TreeSet<>(weekdays == null ? Set.<DayOfWeek>of() : weekdays));
  }

  /** Cos días da semana ISO (luns = 1). */
  public static SeriesSchedule of(
      UUID facilityId,
      UUID useId,
      @Nullable UUID partId,
      List<Integer> isoWeekdays,
      int startMinute,
      int endMinute,
      LocalDate firstDate,
      LocalDate lastDate) {
    SortedSet<DayOfWeek> days = new TreeSet<>();
    for (Integer d : isoWeekdays == null ? List.<Integer>of() : isoWeekdays) {
      if (d == null || d < 1 || d > 7) {
        throw new DomainException(BookingError.SERIES_WEEKDAYS_REQUIRED);
      }
      days.add(DayOfWeek.of(d));
    }
    return new SeriesSchedule(
        facilityId, useId, partId, days, startMinute, endMinute, firstDate, lastDate);
  }

  /**
   * A forma (422): días, minutos e datas. As datas desde {@code today} (Europe/Madrid) e como moito
   * {@value #MAX_DAYS} días.
   *
   * @throws DomainException {@code booking.series-weekdays-required}, {@code
   *     booking.series-time-invalid} ou {@code booking.series-range-invalid}
   */
  public void check(LocalDate today) {
    if (weekdays.isEmpty()) {
      throw new DomainException(BookingError.SERIES_WEEKDAYS_REQUIRED);
    }
    if (startMinute < 0
        || startMinute % STEP != 0
        || endMinute > 1440
        || endMinute % STEP != 0
        || endMinute <= startMinute) {
      throw new DomainException(BookingError.SERIES_TIME_INVALID);
    }
    if (firstDate.isBefore(today)
        || lastDate.isBefore(firstDate)
        || ChronoUnit.DAYS.between(firstDate, lastDate) > MAX_DAYS) {
      throw new DomainException(BookingError.SERIES_RANGE_INVALID);
    }
  }

  /** Os días da semana ISO (luns = 1), en orde. */
  public List<Integer> isoWeekdays() {
    return weekdays.stream().map(DayOfWeek::getValue).toList();
  }

  /** As datas da tempada: as de {@code firstDate} a {@code lastDate} cun dos seus días. */
  public List<LocalDate> dates() {
    List<LocalDate> dates = new ArrayList<>();
    for (LocalDate d = firstDate; !d.isAfter(lastDate); d = d.plusDays(1)) {
      if (weekdays.contains(d.getDayOfWeek())) {
        dates.add(d);
      }
    }
    return dates;
  }

  /** Inicio dese día en Europe/Madrid ({@link ZonedDateTime#ofLocal}). */
  public ZonedDateTime start(LocalDate date) {
    return at(date, startMinute);
  }

  /** Fin dese día; {@code 1440} é a medianoite do día seguinte. */
  public ZonedDateTime end(LocalDate date) {
    return at(date, endMinute);
  }

  /** Se a hora de reloxo dese día non existe (o oco do cambio de hora). */
  public static boolean inGap(LocalDate date, int minute) {
    LocalDateTime local = date.atStartOfDay().plusMinutes(minute);
    return ZONE.getRules().getValidOffsets(local).isEmpty();
  }

  private static ZonedDateTime at(LocalDate date, int minute) {
    return ZonedDateTime.ofLocal(date.atStartOfDay().plusMinutes(minute), ZONE, null);
  }

  /**
   * A entrada en texto canónico (sen a entidade): a pegada da clave de reintento sae disto e da
   * entidade.
   */
  public String canonical() {
    return String.join(
        "|",
        facilityId.toString(),
        useId.toString(),
        partId == null ? "" : partId.toString(),
        weekdays.stream().map(d -> Integer.toString(d.getValue())).collect(Collectors.joining(",")),
        Integer.toString(startMinute),
        Integer.toString(endMinute),
        firstDate.toString(),
        lastDate.toString());
  }
}
