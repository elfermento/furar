// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;

/**
 * A penalización das faltas dun concello (ADR-0032, punto 7): ao chegar a {@code threshold} faltas
 * en {@code windowDays} días naturais, {@code blockDays} días sen reservar por internet. Os días
 * cóntanse en hora de Galicia (Europe/Madrid): só aquí, na xanela e no final do día en que remata o
 * bloqueo (punto 11).
 *
 * @param since desde cando está acesa: só contan as faltas posteriores; nulo = apagada
 */
public record NoShowRule(@Nullable Instant since, int threshold, int windowDays, int blockDays) {

  public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

  public boolean enabled() {
    return since != null;
  }

  /**
   * Desde cando contan as faltas en {@code now}: o comezo do día (hora de Galicia) de hai {@code
   * windowDays} días ou, se é posterior, o momento en que se acendeu a penalización.
   *
   * @throws IllegalStateException se está apagada
   */
  public Instant countFrom(Instant now) {
    if (since == null) {
      throw new IllegalStateException("The no-show penalty is off");
    }
    Instant window = today(now).minusDays(windowDays).atStartOfDay(ZONE).toInstant();
    return window.isAfter(since) ? window : since;
  }

  /** Se con {@code faults} faltas que contan toca bloquear. */
  public boolean reached(int faults) {
    return faults >= threshold;
  }

  /**
   * Cando remata un bloqueo que empeza en {@code now}: ao final do día (hora de Galicia) de hoxe
   * máis {@code blockDays}, é dicir, ao comezo do día seguinte.
   */
  public Instant blockEnd(Instant now) {
    return today(now).plusDays(blockDays + 1L).atStartOfDay(ZONE).toInstant();
  }

  /** O último día bloqueado (hora de Galicia) dun bloqueo que remata en {@code endsAt}. */
  public static LocalDate lastBlockedDay(Instant endsAt) {
    return endsAt.minusNanos(1).atZone(ZONE).toLocalDate();
  }

  private static LocalDate today(Instant now) {
    return now.atZone(ZONE).toLocalDate();
  }
}
