// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Que reserva se pode valorar (ADR-0037, punto 2): só unha á que a persoa foi. Iso é unha
 * confirmada, xa rematada e sen falta; e, se a instalación tiña control de entrada cando empezou
 * (ADR-0032: sen validar, pasaría soa a falta), só a validada. Unha reserva dese tipo aínda sen
 * marca está á espera da tarefa das faltas: non se valora, por prudencia.
 */
public final class ReviewRule {

  private ReviewRule() {}

  /**
   * @param entryControlSince desde cando a instalación ten control de entrada; nulo se non o ten
   */
  public static boolean reviewable(
      Booking booking, Instant now, @Nullable Instant entryControlSince) {
    // A reserva dunha entidade non se valora (ADR-0039, punto 5).
    if (booking.organizationId() != null
        || !booking.isDone(now)
        || booking.attendance() == Attendance.NO_SHOW) {
      return false;
    }
    boolean controlled =
        entryControlSince != null && booking.slot().start().isAfter(entryControlSince);
    return !controlled || booking.attendance() == Attendance.USED;
  }
}
