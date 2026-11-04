// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import org.jspecify.annotations.Nullable;

/**
 * Os textos de pago e devolución dunha instalación (D2-11, revisión do 2026-11-03): os que lle puxo
 * o concello na ficha, ou nulos. Só se usan se o concello escolle «Distinto por instalación».
 *
 * @param payment «Como se paga», ou nulo
 * @param refund «Como se devolve», ou nulo
 */
public record FacilityPaymentTexts(@Nullable Text payment, @Nullable Text refund) {

  /** Texto en gl e es. */
  public record Text(String gl, String es) {}
}
