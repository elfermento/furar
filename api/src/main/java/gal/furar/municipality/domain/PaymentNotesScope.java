// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * De onde saen os textos de pago e devolución (D2-11, revisión do 2026-11-03): os do concello para
 * todas as instalacións ou os de cada instalación, na súa ficha, sen herdar os do concello.
 */
public enum PaymentNotesScope {
  /** «Igual para todas as instalacións»: os textos do concello. */
  ALL,
  /** «Distinto por instalación»: os da ficha de cada instalación; sen texto, o de Furar. */
  FACILITY;

  public static Optional<PaymentNotesScope> fromName(String name) {
    return Arrays.stream(values()).filter(s -> s.name().equals(name)).findFirst();
  }
}
