// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

/**
 * Cada canto se paga o prezo (ADR-0041): unha vez ({@code ONCE}, o de sempre), ao mes, ao trimestre
 * ou pola tempada. Só informativo: o pago de cada inscrición segue sendo un só estado (Gratis,
 * Pendente, Pagada).
 */
public enum PricePeriod {
  ONCE,
  MONTH,
  QUARTER,
  SEASON
}
