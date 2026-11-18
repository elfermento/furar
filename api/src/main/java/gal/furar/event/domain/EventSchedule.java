// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

import gal.furar.shared.domain.DomainException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O horario dunha actividade (ADR-0041), coma o dunha tempada (ADR-0039, punto 8): nunha
 * instalación, o uso e, nos usos por partes, a parte (fóra dunha instalación, ningún); os días da
 * semana; as horas de reloxo de parede en Europe/Madrid, de 15 en 15 minutos ({@code 1440} é a
 * medianoite do día seguinte); e as datas, as dúas incluídas e como moito un ano. Cada semana.
 */
public record EventSchedule(
    @Nullable UUID useId,
    @Nullable UUID partId,
    SortedSet<DayOfWeek> weekdays,
    int startMinute,
    int endMinute,
    LocalDate firstDate,
    LocalDate lastDate) {

  /** As horas da actividade: as da veciñanza. */
  public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

  /** Días como moito entre a primeira e a última data. */
  public static final int MAX_DAYS = 365;

  /** Os minutos van de 15 en 15. */
  public static final int STEP = 15;

  public EventSchedule {
    Objects.requireNonNull(firstDate, "firstDate");
    Objects.requireNonNull(lastDate, "lastDate");
    weekdays =
        Collections.unmodifiableSortedSet(
            new TreeSet<>(weekdays == null ? Set.<DayOfWeek>of() : weekdays));
    if (partId != null && useId == null) {
      throw new DomainException(EventError.SCHEDULE_INVALID);
    }
  }

  /**
   * Cos días da semana ISO (luns = 1).
   *
   * @throws DomainException {@code event.schedule-weekdays-required} cun día que non existe
   */
  public static EventSchedule of(
      @Nullable UUID useId,
      @Nullable UUID partId,
      @Nullable List<Integer> isoWeekdays,
      int startMinute,
      int endMinute,
      LocalDate firstDate,
      LocalDate lastDate) {
    SortedSet<DayOfWeek> days = new TreeSet<>();
    for (Integer d : isoWeekdays == null ? List.<Integer>of() : isoWeekdays) {
      if (d == null || d < 1 || d > 7) {
        throw new DomainException(EventError.SCHEDULE_WEEKDAYS_REQUIRED);
      }
      days.add(DayOfWeek.of(d));
    }
    return new EventSchedule(useId, partId, days, startMinute, endMinute, firstDate, lastDate);
  }

  /**
   * A forma (422): días, minutos e datas, desde {@code today} (Europe/Madrid) e como moito {@value
   * #MAX_DAYS} días.
   */
  public void check(LocalDate today) {
    if (weekdays.isEmpty()) {
      throw new DomainException(EventError.SCHEDULE_WEEKDAYS_REQUIRED);
    }
    if (startMinute < 0
        || startMinute % STEP != 0
        || endMinute > 1440
        || endMinute % STEP != 0
        || endMinute <= startMinute) {
      throw new DomainException(EventError.SCHEDULE_TIME_INVALID);
    }
    if (firstDate.isBefore(today)
        || lastDate.isBefore(firstDate)
        || ChronoUnit.DAYS.between(firstDate, lastDate) > MAX_DAYS) {
      throw new DomainException(EventError.SCHEDULE_RANGE_INVALID, Map.of("max", MAX_DAYS));
    }
  }

  /** Os días da semana ISO (luns = 1), en orde. */
  public List<Integer> isoWeekdays() {
    return weekdays.stream().map(DayOfWeek::getValue).toList();
  }

  /** As datas: as de {@code firstDate} a {@code lastDate} cun dos días escollidos. */
  public List<LocalDate> dates() {
    List<LocalDate> dates = new ArrayList<>();
    for (LocalDate d = firstDate; !d.isAfter(lastDate); d = d.plusDays(1)) {
      if (weekdays.contains(d.getDayOfWeek())) {
        dates.add(d);
      }
    }
    return dates;
  }

  /** Inicio dese día en Europe/Madrid. */
  public Instant start(LocalDate date) {
    return at(date, startMinute);
  }

  /** Fin dese día; {@code 1440} é a medianoite do día seguinte. */
  public Instant end(LocalDate date) {
    return at(date, endMinute);
  }

  /**
   * As sesións fóra dunha instalación («Outro sitio»): cada data que aínda non empezou. Nunha
   * instalación, quen di que días se crean é o planificador das reservas.
   */
  public List<Session> sessionsOutside(Instant now) {
    List<Session> sessions = new ArrayList<>();
    for (LocalDate date : dates()) {
      Instant start = start(date);
      Instant end = end(date);
      if (start.isAfter(now) && end.isAfter(start)) {
        sessions.add(new Session(date, start, end));
      }
    }
    return sessions;
  }

  /** Unha sesión que se crea. */
  public record Session(LocalDate date, Instant start, Instant end) {

    public Session {
      Objects.requireNonNull(date, "date");
      Objects.requireNonNull(start, "start");
      Objects.requireNonNull(end, "end");
    }
  }

  private static Instant at(LocalDate date, int minute) {
    return ZonedDateTime.ofLocal(date.atStartOfDay().plusMinutes(minute), ZONE, null).toInstant();
  }
}
