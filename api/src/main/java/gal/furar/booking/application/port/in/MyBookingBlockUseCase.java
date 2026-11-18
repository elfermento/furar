// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Se a persoa da sesión está bloqueada por faltas neste concello (ADR-0032, punto 7): a pantalla de
 * reservar avísaa antes de que o intente.
 */
public interface MyBookingBlockUseCase {

  Block block();

  /**
   * @param until o último día bloqueado (hora de Galicia); nulo se non está bloqueada
   * @param faults as faltas que causaron o bloqueo; 0 se non está bloqueada
   * @param windowDays en cantos días conta o concello as faltas
   */
  record Block(boolean blocked, @Nullable LocalDate until, int faults, int windowDays) {

    public static Block none(int windowDays) {
      return new Block(false, null, 0, windowDays);
    }
  }
}
