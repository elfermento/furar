// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

/** O estado dunha partida aberta (ADR-0040). */
public enum OpenMatchStatus {
  /** Busca xente: vese en público e admite xente. */
  OPEN,
  /** «Deixar de buscar» (ou pechada por un cambio): a reserva e as persoas seguen. */
  CLOSED,
  /** Cancelada coa súa reserva ou por non chegar ao mínimo; non se reabre. */
  CANCELLED
}
