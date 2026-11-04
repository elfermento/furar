// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Prezo (plan do dominio 2, §1.2.11): o tipo e, se é de pago, o importe en céntimos. O do uso é por
 * franxa (nas prazas, por persoa e franxa); o da reserva, o total conxelado ao reservar ({@link
 * #forSlots}). Sen prezo e gratis non levan importe.
 *
 * @param kind tipo de prezo
 * @param amountCents importe en céntimos, só en {@link PriceKind#PAID}
 */
public record Price(PriceKind kind, @Nullable Integer amountCents) {

  public Price {
    Objects.requireNonNull(kind, "kind");
    if ((kind == PriceKind.PAID) != (amountCents != null)) {
      throw new IllegalArgumentException("Only a paid price has an amount: " + kind);
    }
    if (amountCents != null && amountCents < 1) {
      throw new IllegalArgumentException("A paid price is at least one cent: " + amountCents);
    }
  }

  public static Price none() {
    return new Price(PriceKind.NONE, null);
  }

  public static Price free() {
    return new Price(PriceKind.FREE, null);
  }

  public static Price paid(int amountCents) {
    return new Price(PriceKind.PAID, amountCents);
  }

  /** O total de {@code slots} franxas seguidas: importe × franxas (§1.2.11). */
  public Price forSlots(int slots) {
    if (slots < 1) {
      throw new IllegalArgumentException("At least one slot: " + slots);
    }
    return amountCents == null ? this : paid(Math.multiplyExact(amountCents, slots));
  }

  /** O cobro ao reservar: pendente se é de pago; se non, nada que cobrar. */
  public PaymentStatus initialPayment() {
    return kind == PriceKind.PAID ? PaymentStatus.PENDING : PaymentStatus.NONE;
  }
}
