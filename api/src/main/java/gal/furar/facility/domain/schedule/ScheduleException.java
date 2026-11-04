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
 * Excepción do horario dunha instalación nun día (agregado; especificación, bloque 2): ese día está
 * pechado ou aberto cun só tramo (P9), cun motivo opcional, e manda sobre o festivo e a semana da
 * tempada. Inmutable (A13). Unha por instalación e data: compróbao o servizo baixo o bloqueo da
 * instalación e a base garánteo.
 */
public final class ScheduleException {

  public static final int REASON_MAX = 200;

  private final ScheduleExceptionId id;
  private final FacilityId facilityId;
  private final LocalDate date;
  private final @Nullable TimeRange interval;
  private final @Nullable String reason;
  private final @Nullable Long version;
  private final Instant createdAt;
  private final Instant updatedAt;

  private ScheduleException(
      ScheduleExceptionId id,
      FacilityId facilityId,
      LocalDate date,
      @Nullable TimeRange interval,
      @Nullable String reason,
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.facilityId = Objects.requireNonNull(facilityId, "facilityId");
    this.date = Objects.requireNonNull(date, "date");
    this.interval = interval;
    this.reason = reason;
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  /** Excepción nova, comprobada. */
  public static ScheduleException create(
      ScheduleExceptionId id, FacilityId facilityId, ExceptionSpec spec, Instant now) {
    Checked c = check(spec);
    return new ScheduleException(
        id, facilityId, c.date(), c.interval(), c.reason(), null, now, now);
  }

  /** Fila gardada, sen comprobar de novo (xa a comprobou o dominio e a base). */
  public static ScheduleException rehydrate(
      ScheduleExceptionId id,
      FacilityId facilityId,
      LocalDate date,
      @Nullable TimeRange interval,
      @Nullable String reason,
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt) {
    return new ScheduleException(
        id, facilityId, date, interval, reason, version, createdAt, updatedAt);
  }

  /** Edición enteira coa mesma identidade e versión (o adaptador compróbaa con If-Match). */
  public ScheduleException update(ExceptionSpec spec, Instant now) {
    Checked c = check(spec);
    return new ScheduleException(
        id, facilityId, c.date(), c.interval(), c.reason(), version, createdAt, now);
  }

  /** Os tramos dese día: un se está aberto, ningún se está pechado. */
  public List<TimeRange> hours() {
    return interval == null ? List.of() : List.of(interval);
  }

  public boolean isOpen() {
    return interval != null;
  }

  private record Checked(LocalDate date, @Nullable TimeRange interval, @Nullable String reason) {}

  /**
   * Data obrigatoria; aberto cun tramo válido (fin despois do inicio, ata as 24:00, cuartos de
   * hora); pechado sen tramo; motivo de ata 200 caracteres (baleiro = sen motivo). Os erros dos
   * campos saen xuntos; un tramo mal sae co código {@code facility.interval-invalid}.
   */
  private static Checked check(ExceptionSpec spec) {
    List<FieldViolation> invalid = new ArrayList<>();
    if (spec.date() == null) {
      invalid.add(FieldViolation.of("date", ValidationError.REQUIRED));
    }
    String reason = spec.reason() == null ? null : spec.reason().strip();
    if (reason != null && reason.isEmpty()) {
      reason = null;
    }
    if (reason != null && reason.length() > REASON_MAX) {
      invalid.add(
          FieldViolation.of("reason", ValidationError.SIZE, Map.of("min", 0, "max", REASON_MAX)));
    }
    Integer start = spec.startMinute();
    Integer end = spec.endMinute();
    TimeRange interval = null;
    if (spec.open()) {
      if (start == null) {
        invalid.add(FieldViolation.of("startMinute", ValidationError.REQUIRED));
      }
      if (end == null) {
        invalid.add(FieldViolation.of("endMinute", ValidationError.REQUIRED));
      }
      if (start != null && end != null) {
        interval = new TimeRange(start, end);
      }
    } else {
      if (start != null) {
        invalid.add(FieldViolation.of("startMinute", ValidationError.INVALID));
      }
      if (end != null) {
        invalid.add(FieldViolation.of("endMinute", ValidationError.INVALID));
      }
    }
    if (!invalid.isEmpty()) {
      throw new DomainException(ScheduleError.EXCEPTION_INVALID, Map.of(), invalid);
    }
    if (interval != null && !interval.isValid()) {
      throw new DomainException(
          ScheduleError.INTERVAL_INVALID,
          Map.of("step", TimeRange.STEP),
          List.of(
              FieldViolation.of(
                  "endMinute", ScheduleError.INTERVAL_INVALID, Map.of("step", TimeRange.STEP))));
    }
    return new Checked(spec.date(), interval, reason);
  }

  public ScheduleExceptionId id() {
    return id;
  }

  public FacilityId facilityId() {
    return facilityId;
  }

  public LocalDate date() {
    return date;
  }

  public @Nullable TimeRange interval() {
    return interval;
  }

  public @Nullable String reason() {
    return reason;
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
