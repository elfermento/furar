// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.BookingMailLog;
import gal.furar.booking.StaffAccounts;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.Participant;
import gal.furar.booking.domain.PaymentStatus;
import gal.furar.shared.domain.CustomFieldValues;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Unha reserva tal como a ve o persoal do concello: cos nomes de agora da instalación, o uso e a
 * parte, o enderezo da instalación e o que se pode facer con ela.
 *
 * @param facilityAddress enderezo da instalación nunha liña (baleiro se xa non existe)
 * @param partName nome da parte, só nos usos por partes (baleiro se xa non existe)
 * @param done «Realizada»: confirmada e xa rematada
 * @param paymentTargets os cobros que se poden marcar agora
 * @param customFields os campos propios da reserva (o persoal ve todos)
 * @param bookedBy quen do persoal a fixo no nome da persoa; nulo se a fixo ela (ou xa non se sabe)
 * @param lastMail o último correo que saíu (só no detalle dunha reserva)
 * @param participants as persoas da reserva, só nos usos por prazas («Para quen», ADR-0031)
 * @param noShow as faltas que leva a persoa, se a reserva ten «non presentado», correo e a
 *     penalización está acesa (ADR-0032, punto 7)
 * @param blockLifted se a operación que devolve esta vista levantou un bloqueo por faltas
 * @param openMatch a súa partida aberta, só no detalle e sen nomes (ADR-0040)
 * @param organization a entidade titular e, nunha sesión, a súa tempada (ADR-0039)
 */
public record ManagedBooking(
    Booking booking,
    String facilityName,
    @Nullable String facilityAddress,
    String useName,
    @Nullable String partName,
    boolean done,
    Set<PaymentStatus> paymentTargets,
    CustomFieldValues customFields,
    StaffAccounts.@Nullable Member bookedBy,
    BookingMailLog.@Nullable SentMail lastMail,
    List<Participant> participants,
    @Nullable NoShowStanding noShow,
    boolean blockLifted,
    @Nullable OpenMatchSummary openMatch,
    @Nullable BookingOrganization organization,
    @Nullable BookingActivity activity) {

  /** Sen nada que avisar sobre faltas. */
  public ManagedBooking(
      Booking booking,
      String facilityName,
      @Nullable String facilityAddress,
      String useName,
      @Nullable String partName,
      boolean done,
      Set<PaymentStatus> paymentTargets,
      CustomFieldValues customFields,
      StaffAccounts.@Nullable Member bookedBy,
      BookingMailLog.@Nullable SentMail lastMail,
      List<Participant> participants) {
    this(
        booking,
        facilityName,
        facilityAddress,
        useName,
        partName,
        done,
        paymentTargets,
        customFields,
        bookedBy,
        lastMail,
        participants,
        null,
        false,
        null,
        null,
        null);
  }

  /** A mesma vista coa súa partida aberta (ADR-0040). */
  public ManagedBooking withOpenMatch(@Nullable OpenMatchSummary match) {
    return new ManagedBooking(
        booking,
        facilityName,
        facilityAddress,
        useName,
        partName,
        done,
        paymentTargets,
        customFields,
        bookedBy,
        lastMail,
        participants,
        noShow,
        blockLifted,
        match,
        organization,
        activity);
  }

  /** A mesma vista co aviso das faltas que leva a persoa, despois de marcar unha. */
  public ManagedBooking withNoShow(@Nullable NoShowStanding standing) {
    return new ManagedBooking(
        booking,
        facilityName,
        facilityAddress,
        useName,
        partName,
        done,
        paymentTargets,
        customFields,
        bookedBy,
        lastMail,
        participants,
        standing,
        blockLifted,
        openMatch,
        organization,
        activity);
  }

  /** A mesma vista dicindo que a operación levantou un bloqueo por faltas. */
  public ManagedBooking withBlockLifted(boolean lifted) {
    return new ManagedBooking(
        booking,
        facilityName,
        facilityAddress,
        useName,
        partName,
        done,
        paymentTargets,
        customFields,
        bookedBy,
        lastMail,
        participants,
        noShow,
        lifted,
        openMatch,
        organization,
        activity);
  }

  /** A mesma vista coa entidade titular (ADR-0039). */
  public ManagedBooking withOrganization(@Nullable BookingOrganization owner) {
    return new ManagedBooking(
        booking,
        facilityName,
        facilityAddress,
        useName,
        partName,
        done,
        paymentTargets,
        customFields,
        bookedBy,
        lastMail,
        participants,
        noShow,
        blockLifted,
        openMatch,
        owner,
        activity);
  }

  /** A mesma vista coa actividade da que é sesión (ADR-0041), para ir a ela desde o calendario. */
  public ManagedBooking withActivity(@Nullable BookingActivity linked) {
    return new ManagedBooking(
        booking,
        facilityName,
        facilityAddress,
        useName,
        partName,
        done,
        paymentTargets,
        customFields,
        bookedBy,
        lastMail,
        participants,
        noShow,
        blockLifted,
        openMatch,
        organization,
        linked);
  }

  public ManagedBooking {
    Objects.requireNonNull(booking, "booking");
    Objects.requireNonNull(facilityName, "facilityName");
    Objects.requireNonNull(useName, "useName");
    paymentTargets = Set.copyOf(paymentTargets);
    Objects.requireNonNull(customFields, "customFields");
    participants = List.copyOf(participants);
  }
}
