// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import gal.furar.booking.domain.SimilarSlot;
import gal.furar.booking.domain.SlotCandidate;
import gal.furar.booking.domain.SlotTarget;
import java.util.List;

/**
 * Quen escolle e ordena os «ocos parecidos» (ADR-0033, punto 9). O servizo xunta os candidatos
 * (ocos libres que a persoa pode reservar) e a implementación decide cales propoñer: outra distinta
 * substitúea sen tocar o servizo nin a API.
 */
public interface SlotRecommender {

  /**
   * @param target a hora pedida
   * @param candidates ocos libres, xa filtrados polas normas
   * @param max cantos como moito
   */
  List<SimilarSlot> recommend(SlotTarget target, List<SlotCandidate> candidates, int max);
}
