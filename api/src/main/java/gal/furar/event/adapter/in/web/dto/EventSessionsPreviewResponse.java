// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * As sesións que crearía unha actividade (ADR-0041), coa mesma forma ca a vista previa dunha
 * tempada (ADR-0039, punto 8): cantas, a primeira e a última, os festivos, os días sen o uso, os
 * pechados e os choques (que non se crean). Noutro sitio, só as sesións.
 */
public record EventSessionsPreviewResponse(
    int sessions,
    @Nullable Instant firstSession,
    @Nullable Instant lastSession,
    List<LocalDate> holidays,
    List<LocalDate> useOff,
    List<LocalDate> closed,
    List<ConflictResponse> conflicts) {

  /**
   * Un día que xa ten algo.
   *
   * @param kind {@code OTHER_USE_EXCLUSIVE}, {@code OUT_OF_HOURS}, {@code BLOCK}, {@code BOOKING}
   *     ou {@code SESSION}
   * @param bookingId a reserva ou sesión que o pisa, para cancelala desde a lista
   * @param name de quen é a reserva, ou de que entidade ou actividade a sesión
   * @param label o motivo do bloqueo ou o nome do uso exclusivo
   */
  public record ConflictResponse(
      LocalDate date,
      Instant start,
      Instant end,
      String kind,
      @Nullable UUID bookingId,
      @Nullable String name,
      @Nullable String label) {}
}
