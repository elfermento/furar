// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

/** O pago nun evento con prezo: pendente, pagada ou sen cobrar («Gratis»). */
public enum EventPaymentStatusValue {
  PENDING,
  PAID,
  WAIVED
}
