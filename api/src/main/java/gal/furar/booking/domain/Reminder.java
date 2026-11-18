// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Cando toca o recordatorio dunha reserva (ADR-0035): 24 horas antes de que acabe o prazo para
 * cancelar de balde. Sen prazo, o prazo é o propio inicio ({@link Booking#cancelUntil()}), así que
 * a mesma regra dá 24 horas antes da reserva. Non se envía se a reserva se fixo despois dese
 * momento, nin unha vez pasado o prazo.
 */
public final class Reminder {

  /** Canto antes do prazo para cancelar de balde. */
  public static final Duration LEAD = Duration.ofHours(24);

  private Reminder() {}

  /** O momento do recordatorio dunha reserva con ese prazo para cancelar de balde. */
  public static Instant at(Instant cancelUntil) {
    return cancelUntil.minus(LEAD);
  }

  /**
   * Se ese recordatorio toca agora: a reserva fíxose como moito no momento do recordatorio, o
   * momento xa chegou e o prazo aínda non pasou.
   */
  public static boolean due(Instant createdAt, Instant cancelUntil, Instant now) {
    Instant at = at(cancelUntil);
    return !createdAt.isAfter(at) && !now.isBefore(at) && now.isBefore(cancelUntil);
  }

  /** Se a reserva ten prazo para cancelar de balde antes do inicio. */
  public static boolean hasDeadline(Instant cancelUntil, Instant start) {
    return cancelUntil.isBefore(start);
  }
}
