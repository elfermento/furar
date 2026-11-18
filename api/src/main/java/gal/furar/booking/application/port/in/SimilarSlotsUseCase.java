// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.SimilarSlot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * «Ocos parecidos» a unha hora ocupada (ADR-0033, punto 9): por regras, só ocos que se poden
 * reservar, como moito un por regra. Só le.
 */
public interface SimilarSlotsUseCase {

  /**
   * Sen sesión: só coas normas do uso (antelación e duración).
   *
   * @throws gal.furar.shared.domain.DomainException {@code common.not-found} se a instalación non
   *     se ve ou o uso non é dela; {@code booking.slot-invalid} se {@code start} non é un paso da
   *     grella dese uso
   */
  List<Item> forAnyone(UUID facilityId, UUID useId, Instant start, int slots);

  /**
   * Coa sesión: tamén coas normas da persoa (o límite por persoa e a súa praza). Unha conta de
   * traballo ou bloqueada por faltas non recibe ningún.
   */
  List<Item> forMe(UUID facilityId, UUID useId, Instant start, int slots);

  /**
   * Un oco parecido cos nomes de agora.
   *
   * @param facilityAddress rúa e número, só se é outra instalación
   */
  record Item(SimilarSlot slot, String facilityName, @Nullable String facilityAddress) {}
}
