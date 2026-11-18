// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

/** Que pasou ao ler o código dunha reserva (ADR-0032, punto 4). */
public enum BookingValidationOutcomeValue {
  /** Validouse agora. */
  VALIDATED,
  /** Confirmada e fóra da marxe: non se validou. */
  OUT_OF_WINDOW,
  /** Xa estaba validada. */
  ALREADY_VALIDATED,
  /** Está cancelada. */
  CANCELLED
}
