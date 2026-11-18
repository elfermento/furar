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
 * O detalle dunha reserva para o persoal (panel lateral): o da lista máis o correo de quen reserva,
 * o enderezo da instalación e o que se pode facer agora.
 *
 * @param holderName nome de quen reserva; sen el, a reserva está anonimizada (conservación)
 * @param holderEmail correo de quen reserva (sen teléfono na fase 1); sen el, anonimizada
 * @param facilityAddress enderezo da instalación nunha liña
 * @param cancelUntil ata cando a persoa pode cancelar de balde
 * @param paymentTargets os cobros que se poden marcar agora (tamén desfacer)
 * @param cancellable se o concello aínda a pode cancelar (está confirmada)
 * @param cancelChannels as canles que se poden marcar ao cancelar: correo e aviso na web
 * @param customFields campos propios da reserva (todos), por código
 * @param holderPhone teléfono, só nas que fixo o persoal e se o deu
 * @param holderAccount se a persoa reservou ela coa súa conta (os seus datos non se corrixen);
 *     nunha reserva que fixo o persoal é falso, aínda que o correo teña conta
 * @param bookedBy quen do persoal a fixo no nome da persoa; nulo se a fixo ela
 * @param lastMail o último correo que saíu da reserva; nulo se aínda non saíu ningún
 * @param seats prazas da reserva (1 fóra dos usos por prazas)
 * @param participants para quen é, nos usos por prazas, co ano das persoas a cargo e acompañantes
 * @param attendance se se validou ou quedou como falta (ADR-0032)
 * @param canMarkNoShow se agora se pode marcar «Non presentado»: confirmada, rematada e sen marca
 * @param noShow as faltas que leva a persoa, se a reserva ten «non presentado», correo e a
 *     penalización está acesa
 * @param blockLifted se esta operación levantou un bloqueo por faltas
 * @param openMatch a súa partida aberta, nos números e sen nomes (ADR-0040)
 * @param organization a entidade titular (ADR-0039) e, nunha sesión, a súa tempada
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
    @Nullable String holderPhone,
    boolean holderAccount,
    @Nullable BookedByResponse bookedBy,
    @Nullable BookingMailResponse lastMail,
    int seats,
    List<BookingParticipantResponse> participants,
    BookingAttendanceValue attendance,
    boolean canMarkNoShow,
    @Nullable NoShowStandingResponse noShow,
    boolean blockLifted,
    @Nullable BookingOpenMatchResponse openMatch,
    @Nullable BookingOrganizationResponse organization,
    @Nullable BookingActivityResponse activity,
    long version) {}
