// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import gal.furar.facility.domain.FacilityId;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.ValidationError;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Tempada do horario dunha instalación (agregado; especificación, bloque 2): un nome, as súas datas
 * (os dous extremos incluídos; sen data de fin, cobre todos os días desde o inicio) e a súa semana.
 * Inmutable (A13). Que dúas tempadas da mesma instalación non se solapen mira outras tempadas:
 * compróbao o servizo baixo o bloqueo da instalación con {@link #overlaps(Season)}, e a base
 * garánteo.
 */
public final class Season {

  public static final int NAME_MAX = 100;

  /** Unha tempada non dura máis de dez anos: evita datas tecleadas mal (p. ex. 2206). */
  public static final int YEARS_MAX = 10;

  private final SeasonId id;
  private final FacilityId facilityId;
  private final String name;
  private final LocalDate startDate;
  private final @Nullable LocalDate endDate;
  private final WeekHours week;
  private final @Nullable Long version;
  private final Instant createdAt;
  private final Instant updatedAt;

  private Season(
      SeasonId id,
      FacilityId facilityId,
      String name,
      LocalDate startDate,
      @Nullable LocalDate endDate,
      WeekHours week,
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.facilityId = Objects.requireNonNull(facilityId, "facilityId");
    this.name = Objects.requireNonNull(name, "name");
    this.startDate = Objects.requireNonNull(startDate, "startDate");
    this.endDate = endDate;
    this.week = Objects.requireNonNull(week, "week");
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  /**
   * Tempada nova. Sen {@code copyFrom}, a semana empeza pechada; con ela, cos tramos desa tempada.
   * En ambos casos a fila de festivos énchese coa do domingo (plan §2.1).
   */
  public static Season create(
      SeasonId id, FacilityId facilityId, SeasonSpec spec, @Nullable Season copyFrom, Instant now) {
    Checked c = check(spec);
    WeekHours week = copyFrom == null ? WeekHours.closed() : copyFrom.week;
    return new Season(
        id,
        facilityId,
        c.name(),
        c.startDate(),
        c.endDate(),
        week.withHolidaysLikeSunday(),
        null,
        now,
        now);
  }

  /** Fila gardada, sen comprobar de novo (xa a comprobou o dominio e a base). */
  public static Season rehydrate(
      SeasonId id,
      FacilityId facilityId,
      String name,
      LocalDate startDate,
      @Nullable LocalDate endDate,
      WeekHours week,
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt) {
    return new Season(
        id, facilityId, name, startDate, endDate, week, version, createdAt, updatedAt);
  }

  /**
   * Edición enteira: nome, datas e a semana completa (substitúese nun só gardado). Mesma identidade
   * e versión (o adaptador compróbaa con {@code If-Match}). A fila de festivos é a que manda o
   * xestor.
   */
  public Season update(SeasonSpec spec, WeekHours newWeek, Instant now) {
    Checked c = check(spec);
    return new Season(
        id,
        facilityId,
        c.name(),
        c.startDate(),
        c.endDate(),
        Objects.requireNonNull(newWeek, "week"),
        version,
        createdAt,
        now);
  }

  /**
   * Se as dúas tempadas, distintas, da mesma instalación teñen algún día en común. Unha sen fin
   * choca con calquera que empece o día do seu inicio ou despois.
   */
  public boolean overlaps(Season other) {
    return !other.id.equals(id)
        && other.facilityId.equals(facilityId)
        && (other.endDate == null || !startDate.isAfter(other.endDate))
        && (endDate == null || !other.startDate.isAfter(endDate));
  }

  /** Se a tempada cobre ese día. */
  public boolean covers(LocalDate date) {
    return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
  }

  /** P8: conta para saír de borrador se ten algún tramo e non remata antes de hoxe. */
  public boolean countsAsSchedule(LocalDate today) {
    return week.hasAnyInterval() && (endDate == null || !endDate.isBefore(today));
  }

  /** Se a tempada non ten data de fin. */
  public boolean openEnded() {
    return endDate == null;
  }

  /**
   * O último día que cobre a tempada contando ata {@code horizon}: a data de fin, ou {@code
   * horizon} se non a ten. Para percorrer os días dunha tempada sen fin.
   */
  public LocalDate lastDay(LocalDate horizon) {
    return endDate == null ? horizon : endDate;
  }

  /** Rango de datas cos dous extremos incluídos. */
  public record DateSpan(LocalDate from, LocalDate to) {}

  private record Checked(String name, LocalDate startDate, @Nullable LocalDate endDate) {}

  /**
   * Nome de 1 a 100 caracteres, inicio obrigatorio e, se hai fin, en orde e a menos de dez anos; os
   * erros por campo saen xuntos.
   */
  private static Checked check(SeasonSpec spec) {
    List<FieldViolation> invalid = new ArrayList<>();
    String name = spec.name() == null ? "" : spec.name().strip();
    if (name.isEmpty() || name.length() > NAME_MAX) {
      invalid.add(
          FieldViolation.of("name", ValidationError.SIZE, Map.of("min", 1, "max", NAME_MAX)));
    }
    LocalDate start = spec.startDate();
    LocalDate end = spec.endDate();
    if (start == null) {
      invalid.add(FieldViolation.of("startDate", ValidationError.REQUIRED));
    }
    if (start != null && end != null) {
      if (end.isBefore(start)) {
        invalid.add(FieldViolation.of("endDate", ValidationError.RANGE));
      } else if (end.isAfter(start.plusYears(YEARS_MAX))) {
        invalid.add(
            FieldViolation.of("endDate", ValidationError.RANGE, Map.of("years", YEARS_MAX)));
      }
    }
    if (!invalid.isEmpty()) {
      throw new DomainException(ScheduleError.SEASON_INVALID, Map.of(), invalid);
    }
    return new Checked(name, start, end);
  }

  public SeasonId id() {
    return id;
  }

  public FacilityId facilityId() {
    return facilityId;
  }

  public String name() {
    return name;
  }

  public LocalDate startDate() {
    return startDate;
  }

  /** Último día (incluído), ou {@code null} se a tempada non ten fin. */
  public @Nullable LocalDate endDate() {
    return endDate;
  }

  public WeekHours week() {
    return week;
  }

  public @Nullable Long version() {
    return version;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
