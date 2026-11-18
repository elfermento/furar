// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Penalización das faltas do concello (ADR-0032, punto 7; tarxeta «Non presentados»): ao chegar a
 * {@code threshold} faltas en {@code windowDays} días, a persoa non pode reservar por internet
 * durante {@code blockDays} días. Apagada por defecto; só contan as faltas posteriores a acendela.
 *
 * @param since desde cando está acesa; nulo = apagada
 */
public record NoShowPolicy(@Nullable Instant since, int threshold, int windowDays, int blockDays) {

  public static final int THRESHOLD_MAX = 20;
  public static final int DAYS_MAX = 365;

  /** A da alta: apagada, 3 faltas en 30 días, 15 días de bloqueo. */
  public static final NoShowPolicy OFF = new NoShowPolicy(null, 3, 30, 15);

  public NoShowPolicy {
    if (threshold < 1
        || threshold > THRESHOLD_MAX
        || windowDays < 1
        || windowDays > DAYS_MAX
        || blockDays < 1
        || blockDays > DAYS_MAX) {
      throw new DomainException(
          MunicipalityError.NO_SHOW_POLICY_INVALID,
          Map.of("maxFaults", THRESHOLD_MAX, "maxDays", DAYS_MAX));
    }
  }

  public boolean enabled() {
    return since != null;
  }

  /**
   * Os números novos; acendela garda o momento ({@code now}) e, se xa estaba acesa, queda o de
   * antes: as faltas que xa contaban seguen contando.
   */
  public NoShowPolicy changed(
      boolean enabled, int newThreshold, int newWindowDays, int newBlockDays, Instant now) {
    Instant newSince = !enabled ? null : (since != null ? since : now);
    return new NoShowPolicy(newSince, newThreshold, newWindowDays, newBlockDays);
  }
}
