// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.PaymentStatus;
import gal.furar.shared.domain.CustomFieldValues;
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
 * @param paymentTargets os cobros que se poden marcar agora (§1.2.12, P13)
 * @param customFields os campos propios da reserva (o persoal ve todos)
 */
public record ManagedBooking(
    Booking booking,
    String facilityName,
    @Nullable String facilityAddress,
    String useName,
    @Nullable String partName,
    boolean done,
    Set<PaymentStatus> paymentTargets,
    CustomFieldValues customFields) {

  public ManagedBooking {
    Objects.requireNonNull(booking, "booking");
    Objects.requireNonNull(facilityName, "facilityName");
    Objects.requireNonNull(useName, "useName");
    paymentTargets = Set.copyOf(paymentTargets);
    Objects.requireNonNull(customFields, "customFields");
  }
}
