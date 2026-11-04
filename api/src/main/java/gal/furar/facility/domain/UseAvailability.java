// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

/** Cando se pode reservar un uso. */
public enum UseAvailability {
  /** Sempre que a instalación estea aberta. */
  ALWAYS,
  /** Só nuns rangos de datas (P5). */
  DATES
}
