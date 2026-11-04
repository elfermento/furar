// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import java.util.List;

/**
 * A portada de «Reservas» (maqueta 26): as últimas reservadas e as tres pestanas de «Cobros» coas
 * súas contas.
 *
 * @param latest as últimas reservadas, da máis recente
 * @param pendingPayment confirmadas pendentes de pago de hoxe aos próximos 30 días, por día e hora
 * @param toRefund canceladas que estaban pagadas e aínda non se devolveron, de calquera día
 * @param cancelled canceladas de hoxe aos próximos 30 días, por día e hora
 */
public record BookingsOverview(
    List<ManagedBooking> latest, Tab pendingPayment, Tab toRefund, Tab cancelled) {

  public BookingsOverview {
    latest = List.copyOf(latest);
  }

  /** Unha pestana: cantas hai en total e as primeiras. */
  public record Tab(long count, List<ManagedBooking> first) {

    public Tab {
      first = List.copyOf(first);
    }
  }
}
