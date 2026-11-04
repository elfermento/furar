// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

/**
 * Tipo de prezo dun uso (plan do dominio 2, §2.1): {@code NONE} o concello aínda non o puxo, {@code
 * FREE} gratis, {@code PAID} de pago no mostrador.
 */
public enum PriceKind {
  NONE,
  FREE,
  PAID
}
