// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.List;

/**
 * A portada de «Reservas» (maqueta 26): as últimas reservadas e as tres pestanas de «Cobros».
 *
 * @param latest as últimas reservadas, da máis recente
 * @param pendingPayment confirmadas pendentes de pago, de hoxe aos próximos 30 días
 * @param toRefund canceladas que estaban pagadas e aínda non se devolveron, de calquera día
 * @param cancelled canceladas de hoxe aos próximos 30 días
 */
public record BookingsOverviewResponse(
    List<ManagedBookingResponse> latest,
    BookingTabResponse pendingPayment,
    BookingTabResponse toRefund,
    BookingTabResponse cancelled) {}
