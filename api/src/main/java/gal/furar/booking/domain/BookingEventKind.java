// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

/** Tipo de fila da historia da reserva ({@code booking_event}, ADR-0025 punto 12). */
public enum BookingEventKind {
  CREATED,
  CHANGED,
  CANCELLED_BY_PERSON,
  CANCELLED_BY_MUNICIPALITY,
  /** O concello mantivo a reserva ante un cambio da instalación. */
  KEPT,
  PAID,
  REFUNDED,
  /** Desfíxose un cobro ou unha devolución mal marcados (P13). */
  PAYMENT_UNDONE;

  /** A fila que deixa un cambio de pago cara a {@code target} ({@link Booking#changePayment}). */
  public static BookingEventKind ofPayment(PaymentStatus target) {
    return switch (target) {
      case PAID -> PAID;
      case REFUNDED -> REFUNDED;
      case PENDING, TO_REFUND -> PAYMENT_UNDONE;
      case NONE -> throw new IllegalArgumentException("No payment event towards NONE");
    };
  }
}
