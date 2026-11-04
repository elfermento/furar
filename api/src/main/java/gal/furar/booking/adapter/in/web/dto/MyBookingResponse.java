// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha reserva para quen a fixo.
 *
 * @param code código {@code FUR-XXXX}, o mesmo do QR
 * @param facilityName nome de agora da instalación
 * @param useName nome de agora do uso
 * @param partName nome da parte, só nos usos por partes
 * @param start inicio real
 * @param end fin real
 * @param slots franxas seguidas
 * @param price o total conxelado ao reservar
 * @param cancelUntil ata cando se cancela de balde (o inicio, sen norma)
 * @param canCancel se aínda se pode cancelar ou cambiar por internet
 * @param done «Realizada»: confirmada e xa rematada
 * @param cover portada da instalación (miniatura da tarxeta); nula se xa non existe
 * @param customFields campos propios da reserva que ve quen a fixo, por código
 * @param version a do {@code ETag}, para o {@code If-Match} de cambiar e cancelar
 */
public record MyBookingResponse(
    UUID id,
    String code,
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
    Instant cancelUntil,
    boolean canCancel,
    boolean done,
    Instant createdAt,
    QrResponse qr,
    @Nullable CoverImageResponse cover,
    @Schema(description = "Campos propios da reserva que ve quen a fixo, por código")
        Map<String, Object> customFields,
    long version) {}
