// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A penalización das faltas dun concello para {@code booking} (ADR-0032, punto 7; A19: só tipos de
 * Java).
 *
 * @param since desde cando está acesa: só contan as faltas posteriores; nulo = apagada
 * @param threshold faltas que bloquean
 * @param windowDays en cantos días naturais
 * @param blockDays días sen reservar por internet
 */
public record MunicipalityNoShowPolicy(
    @Nullable Instant since, int threshold, int windowDays, int blockDays) {

  public boolean enabled() {
    return since != null;
  }
}
