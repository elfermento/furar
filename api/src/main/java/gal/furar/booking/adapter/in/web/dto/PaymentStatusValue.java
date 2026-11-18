// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

/** Cobro no mostrador: nada que cobrar, pendente, pagada, a devolver ou devolta. */
public enum PaymentStatusValue {
  NONE,
  PENDING,
  PAID,
  TO_REFUND,
  REFUNDED
}
