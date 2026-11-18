// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

/** Por que se cancelou unha partida aberta (ADR-0040). */
public enum OpenMatchCancelReason {
  /** Ao prazo do concello non se chegou ao mínimo: cancelouse tamén a reserva. */
  UNFILLED,
  /** Cancelouse a reserva (a persoa, o concello ou un cambio da instalación). */
  BOOKING_CANCELLED
}
