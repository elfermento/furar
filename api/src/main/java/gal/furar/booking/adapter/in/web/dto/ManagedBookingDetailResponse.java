// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O detalle dunha reserva para o persoal (maqueta 26, panel lateral): o da lista máis o correo de
 * quen reserva, o enderezo da instalación e o que se pode facer agora.
 *
 * @param holderName nome de quen reserva; sen el, a reserva está anonimizada (conservación)
 * @param holderEmail correo de quen reserva (sen teléfono na fase 1, P7); sen el, anonimizada
 * @param facilityAddress enderezo da instalación nunha liña
 * @param cancelUntil ata cando a persoa pode cancelar de balde
 * @param paymentTargets os cobros que se poden marcar agora (P13: tamén desfacer)
 * @param cancellable se o concello aínda a pode cancelar (está confirmada)
 * @param cancelChannels as canles que se poden marcar ao cancelar (P12): correo e aviso na web
 * @param customFields campos propios da reserva (todos), por código
 */
public record ManagedBookingDetailResponse(
    UUID id,
    String code,
    @Nullable String holderName,
    @Nullable String holderEmail,
    UUID facilityId,
    String facilityName,
    @Nullable String facilityAddress,
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
    Instant cancelUntil,
    boolean done,
    Instant reservedAt,
    @Nullable Instant cancelledAt,
    List<PaymentStatusValue> paymentTargets,
    boolean cancellable,
    List<CancelChannelValue> cancelChannels,
    @Schema(description = "Campos propios da reserva, por código") Map<String, Object> customFields,
    long version) {}
