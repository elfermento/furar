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
 * Bloqueo dunha instalación (agregado; especificación, bloque 2): un día cun tramo de reloxo de
 * parede (P13: todo o día = 0:00–24:00) no que non se pode reservar, cun motivo opcional. Inmutable
 * (A13). O período real ({@link #start()}, {@link #end()}) calcúlase en Europe/Madrid (ADR-0022,
 * punto 8) co cambio de hora aplicado como en {@link OpeningHours#instant}: unha hora que se repite
 * (25 de outubro, 2:00–3:00) toma a primeira vez para o inicio e a segunda para o fin, así que ese
 * bloqueo dura dúas horas reais; un tramo que o salto deixa baleiro (29 de marzo, 2:00–2:30) non é
 * un bloqueo. Dous bloqueos da instalación non se pisan (P7): compróbao o servizo baixo o bloqueo
 * da instalación e a base garánteo.
 */
public final class Block {

  public static final int REASON_MAX = 200;

  private final BlockId id;
  private final FacilityId facilityId;
  private final LocalDate date;
  private final TimeRange interval;
  private final Instant start;
  private final Instant end;
  private final @Nullable String reason;
  private final String createdBy;
  private final @Nullable Long version;
  private final Instant createdAt;
  private final Instant updatedAt;

  private Block(
      BlockId id,
      FacilityId facilityId,
      LocalDate date,
      TimeRange interval,
      Instant start,
      Instant end,
      @Nullable String reason,
      String createdBy,
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.facilityId = Objects.requireNonNull(facilityId, "facilityId");
    this.date = Objects.requireNonNull(date, "date");
    this.interval = Objects.requireNonNull(interval, "interval");
    this.start = Objects.requireNonNull(start, "start");
    this.end = Objects.requireNonNull(end, "end");
    this.reason = reason;
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  /** Bloqueo novo, comprobado, co seu período real. */
  public static Block create(
      BlockId id, FacilityId facilityId, BlockSpec spec, String createdBy, Instant now) {
    Checked c = check(spec);
    return new Block(
        id,
        facilityId,
        c.date(),
        c.interval(),
        c.start(),
        c.end(),
        c.reason(),
        createdBy,
        null,
        now,
        now);
  }

  /** Fila gardada, sen comprobar de novo (xa a comprobou o dominio e a base). */
  public static Block rehydrate(
      BlockId id,
      FacilityId facilityId,
      LocalDate date,
      TimeRange interval,
      Instant start,
      Instant end,
      @Nullable String reason,
      String createdBy,
      @Nullable Long version,
      Instant createdAt,
      Instant updatedAt) {
    return new Block(
        id,
        facilityId,
        date,
        interval,
        start,
        end,
        reason,
        createdBy,
        version,
        createdAt,
        updatedAt);
  }

  /** Edición enteira coa mesma identidade, autoría e versión (o adaptador compróbaa). */
  public Block update(BlockSpec spec, Instant now) {
    Checked c = check(spec);
    return new Block(
        id,
        facilityId,
        c.date(),
        c.interval(),
        c.start(),
        c.end(),
        c.reason(),
        createdBy,
        version,
        createdAt,
        now);
  }

  /** Se os dous períodos reais teñen algún instante en común (fin excluído). */
  public boolean overlaps(Block other) {
    return start.isBefore(other.end) && other.start.isBefore(end);
  }

  /** Se o período real deste cabe enteiro no do outro (entón non bloquea nada novo). */
  public boolean within(Block other) {
    return !start.isBefore(other.start) && !end.isAfter(other.end);
  }

  private record Checked(
      LocalDate date, TimeRange interval, Instant start, Instant end, @Nullable String reason) {}

  /**
   * Data e tramo obrigatorios; motivo de ata 200 caracteres (baleiro = sen motivo). Os erros dos
   * campos saen xuntos ({@code facility.block-invalid}); un tramo mal (fin antes do inicio, máis
   * alá das 24:00, fóra dos cuartos de hora ou baleiro co cambio de hora) sae co código {@code
   * facility.block-invalid-period}.
   */
  private static Checked check(BlockSpec spec) {
    List<FieldViolation> invalid = new ArrayList<>();
    if (spec.date() == null) {
      invalid.add(FieldViolation.of("date", ValidationError.REQUIRED));
    }
    if (spec.startMinute() == null) {
      invalid.add(FieldViolation.of("startMinute", ValidationError.REQUIRED));
    }
    if (spec.endMinute() == null) {
      invalid.add(FieldViolation.of("endMinute", ValidationError.REQUIRED));
    }
    String reason = spec.reason() == null ? null : spec.reason().strip();
    if (reason != null && reason.isEmpty()) {
      reason = null;
    }
    if (reason != null && reason.length() > REASON_MAX) {
      invalid.add(
          FieldViolation.of("reason", ValidationError.SIZE, Map.of("min", 0, "max", REASON_MAX)));
    }
    if (!invalid.isEmpty()) {
      throw new DomainException(ScheduleError.BLOCK_INVALID, Map.of(), invalid);
    }
    LocalDate date = Objects.requireNonNull(spec.date());
    TimeRange interval =
        new TimeRange(
            Objects.requireNonNull(spec.startMinute()), Objects.requireNonNull(spec.endMinute()));
    if (!interval.isValid()) {
      throw invalidPeriod();
    }
    Instant start = OpeningHours.instant(date, interval.startMinute(), OpeningHours.ZONE);
    Instant end = OpeningHours.instant(date, interval.endMinute(), OpeningHours.ZONE);
    if (!start.isBefore(end)) {
      throw invalidPeriod();
    }
    return new Checked(date, interval, start, end, reason);
  }

  private static DomainException invalidPeriod() {
    return new DomainException(
        ScheduleError.BLOCK_INVALID_PERIOD,
        Map.of("step", TimeRange.STEP),
        List.of(
            FieldViolation.of(
                "endMinute", ScheduleError.BLOCK_INVALID_PERIOD, Map.of("step", TimeRange.STEP))));
  }

  public BlockId id() {
    return id;
  }

  public FacilityId facilityId() {
    return facilityId;
  }

  public LocalDate date() {
    return date;
  }

  public TimeRange interval() {
    return interval;
  }

  public Instant start() {
    return start;
  }

  public Instant end() {
    return end;
  }

  public @Nullable String reason() {
    return reason;
  }

  public String createdBy() {
    return createdBy;
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
