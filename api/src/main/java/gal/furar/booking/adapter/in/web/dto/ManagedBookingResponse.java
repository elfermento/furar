// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha reserva nas listas e no calendario de xestión (maquetas 26 e 27). O correo e o que se pode
 * facer van só no detalle.
 *
 * @param holderName nome de quen reserva, copiado da súa conta ao reservar; sen el, a reserva está
 *     anonimizada (conservación)
 * @param facilityName nome de agora da instalación
 * @param useName nome de agora do uso
 * @param partName nome da parte, só nos usos por partes
 * @param price o total conxelado ao reservar
 * @param done «Realizada»: confirmada e xa rematada
 * @param reservedAt cando se reservou
 * @param cancelledAt cando se cancelou, só nas canceladas
 * @param bookedBy quen do persoal a fixo no nome da persoa; nulo se a fixo ela
 */
public record ManagedBookingResponse(
    UUID id,
    String code,
    @Nullable String holderName,
    UUID facilityId,
    String facilityName,
    UUID useId,
    String useName,
    BookingRentalModeValue rentalMode,
    @Nullable UUID partId,
    @Nullable String partName,
    Instant start,
    Instant end,
    int slots,
    BookingStatusValue status,
    PaymentStatusValue paymentStatus,
    BookingPriceResponse price,
    boolean done,
    Instant reservedAt,
    @Nullable Instant cancelledAt,
    @Nullable BookedByResponse bookedBy,
    long version) {}
