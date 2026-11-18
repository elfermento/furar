// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.util.List;
import java.util.stream.IntStream;

/**
 * O límite fixo das esperas (ADR-0033, punto 4): {@value #MAX} activas por conta e concello. A base
 * garánteo: cada espera leva un ordinal de 1 a {@value #MAX} que non se repite na conta.
 */
public final class WaitLimit {

  /** Esperas activas por conta e concello. */
  public static final int MAX = 3;

  private WaitLimit() {}

  /** Os ordinais que se proban ao crear unha espera, en orde. */
  public static List<Integer> ordinals() {
    return IntStream.rangeClosed(1, MAX).boxed().toList();
  }
}
