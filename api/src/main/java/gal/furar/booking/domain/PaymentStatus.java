// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

/**
 * Estado do cobro no mostrador (ADR-0025, punto 12). {@code NONE}: sen prezo ou gratis, non hai
 * nada que cobrar. {@code TO_REFUND} e {@code REFUNDED}: só nunha reserva cancelada que estaba
 * pagada.
 */
public enum PaymentStatus {
  NONE,
  PENDING,
  PAID,
  TO_REFUND,
  REFUNDED
}
