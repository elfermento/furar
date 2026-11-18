// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.Participant;
import gal.furar.booking.domain.QrCode;
import gal.furar.facility.FacilityCover;
import gal.furar.shared.domain.CustomFieldValues;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Unha reserva tal como a ve quen a fixo: cos nomes de agora da instalación, o uso e a parte, o QR
 * do código e o que pode facer.
 *
 * @param facilityAddress enderezo da instalación nunha liña (o {@code LOCATION} do {@code .ics})
 * @param partName nome da parte, só nos usos por partes (baleiro se xa non existe)
 * @param canCancel se aínda pode cancelar ou cambiar por internet
 * @param done «Realizada»: confirmada e xa rematada
 * @param cover portada da instalación (a miniatura da tarxeta); baleira se xa non existe
 * @param customFields os campos propios da reserva que ve quen a fixo (sen os de «só o persoal»;
 *     baleiros nunha reserva que fixo o persoal)
 * @param participants as persoas da reserva, só nos usos por prazas («Para quen», ADR-0031)
 * @param canReview se a pode valorar agora (ADR-0037): pasada, á que foi e aínda sen valorar
 * @param reviewStars a nota que lle deu, se xa a valorou
 * @param openMatch a súa partida aberta, se a ten (ADR-0040)
 * @param organization a entidade titular e, nunha sesión, a súa tempada (ADR-0039)
 */
public record MyBooking(
    Booking booking,
    String facilityName,
    @Nullable String facilityAddress,
    String useName,
    @Nullable String partName,
    QrCode qr,
    boolean canCancel,
    boolean done,
    @Nullable FacilityCover cover,
    CustomFieldValues customFields,
    List<Participant> participants,
    boolean canReview,
    @Nullable Integer reviewStars,
    @Nullable OpenMatchSummary openMatch,
    @Nullable BookingOrganization organization) {

  public MyBooking {
    Objects.requireNonNull(booking, "booking");
    Objects.requireNonNull(facilityName, "facilityName");
    Objects.requireNonNull(useName, "useName");
    Objects.requireNonNull(qr, "qr");
    Objects.requireNonNull(customFields, "customFields");
    participants = List.copyOf(participants);
  }
}
