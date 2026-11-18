// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

/** Orde da lista pública das valoracións (ADR-0037, punto 5). */
public enum ReviewOrder {
  /** Máis recentes (por defecto). */
  NEWEST,
  OLDEST,
  /** Nota máis alta e, coa mesma nota, as máis recentes. */
  HIGHEST,
  /** Nota máis baixa e, coa mesma nota, as máis recentes. */
  LOWEST
}
