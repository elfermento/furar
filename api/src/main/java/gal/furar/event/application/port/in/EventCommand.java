// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.port.in;

import gal.furar.event.domain.EventKind;
import gal.furar.event.domain.PricePeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Alta ou edición dun evento. Lugar: {@code facilityId} (unha instalación do concello) ou {@code
 * place} (outro sitio), un dos dous. Prazas, prezo e «Apuntarse ata» opcionais. Un evento dun día
 * ({@code kind} nulo ou {@code ONE_DAY}) leva {@code startsAt}; unha actividade ({@code SCHEDULED},
 * ADR-0041), {@code schedule}. Sen {@code pricePeriod}, unha vez.
 */
public record EventCommand(
    @Nullable String nameGl,
    @Nullable String nameEs,
    @Nullable String descriptionGl,
    @Nullable String descriptionEs,
    @Nullable EventKind kind,
    @Nullable Instant startsAt,
    @Nullable UUID facilityId,
    @Nullable String place,
    @Nullable ScheduleCommand schedule,
    @Nullable Integer capacity,
    @Nullable Integer priceCents,
    @Nullable PricePeriod pricePeriod,
    @Nullable Instant registrationDeadline,
    boolean published,
    boolean approval,
    @Nullable Integer birthYearFrom,
    @Nullable Integer birthYearTo) {

  /** Un evento dun día (ADR-0038). */
  public EventCommand(
      @Nullable String nameGl,
      @Nullable String nameEs,
      @Nullable String descriptionGl,
      @Nullable String descriptionEs,
      Instant startsAt,
      @Nullable UUID facilityId,
      @Nullable String place,
      @Nullable Integer capacity,
      @Nullable Integer priceCents,
      @Nullable Instant registrationDeadline,
      boolean published,
      boolean approval) {
    this(
        nameGl,
        nameEs,
        descriptionGl,
        descriptionEs,
        EventKind.ONE_DAY,
        startsAt,
        facilityId,
        place,
        null,
        capacity,
        priceCents,
        null,
        registrationDeadline,
        published,
        approval,
        null,
        null);
  }

  /** O tipo, co de sempre se non vén. */
  public EventKind kindOrDefault() {
    return kind == null ? EventKind.ONE_DAY : kind;
  }

  /**
   * O horario dunha actividade (ADR-0041). Nunha instalación, o uso e (nos usos por partes) a
   * parte; noutro sitio, ningún.
   *
   * @param weekdays días da semana ISO (luns = 1)
   * @param skip os días con choque que viu o xestor na vista previa (só nunha instalación)
   */
  public record ScheduleCommand(
      @Nullable UUID useId,
      @Nullable UUID partId,
      @Nullable List<Integer> weekdays,
      int startMinute,
      int endMinute,
      LocalDate firstDate,
      LocalDate lastDate,
      @Nullable Set<LocalDate> skip) {}
}
