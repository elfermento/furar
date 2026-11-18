// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.domain;

/**
 * O pago dunha inscrición nun evento con prezo, coma nas reservas: pendente, pagada no mostrador ou
 * sen cobrar ({@code WAIVED}, «Gratis»: o concello decide non cobrala).
 */
public enum PaymentStatus {
  PENDING,
  PAID,
  WAIVED
}
