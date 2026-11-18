// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

/**
 * Estado da reserva (ADR-0025, punto 12), separado do pago. «Realizada» non é un estado: calcúlase
 * (confirmada e xa rematada, {@link Booking#isDone}).
 */
public enum BookingStatus {
  CONFIRMED,
  CANCELLED_BY_PERSON,
  CANCELLED_BY_MUNICIPALITY;

  public boolean isCancelled() {
    return this != CONFIRMED;
  }
}
