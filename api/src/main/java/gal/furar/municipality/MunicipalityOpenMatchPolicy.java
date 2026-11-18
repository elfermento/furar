// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * «Buscar xente para xogar» dun concello para {@code booking} (ADR-0040; A19: só tipos de Java).
 *
 * @param since desde cando está aceso; nulo = apagado (as partidas nin se ven nin admiten xente)
 * @param cancelHours «Cancelar se non hai mínimo», horas antes do inicio
 * @param changedAt o último cambio do interruptor ou das horas
 */
public record MunicipalityOpenMatchPolicy(
    @Nullable Instant since, int cancelHours, @Nullable Instant changedAt) {

  public boolean enabled() {
    return since != null;
  }

  /**
   * Desde cando conta o prazo: a tarefa só cancela as partidas cuxo prazo cae despois do último
   * cambio da política (ou de acendela), así que acender ou subir as horas nunca cancela en bloque.
   */
  public @Nullable Instant deadlinesFrom() {
    if (since == null) {
      return null;
    }
    return changedAt == null || changedAt.isBefore(since) ? since : changedAt;
  }
}
