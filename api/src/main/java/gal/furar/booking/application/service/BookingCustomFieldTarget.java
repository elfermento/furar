// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.domain.BookingId;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.CustomFieldTargetProvider;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A reserva admite campos personalizados (ADR-0011, revisión do 2026-11-04): destino {@code
 * BOOKING}, que quen reserva enche antes de «Confirmar reserva». Unha reserva é dunha persoa: os
 * seus campos ven só ela e o persoal, nunca son públicos.
 */
@Component
class BookingCustomFieldTarget implements CustomFieldTargetProvider {

  private final BookingRepository bookings;

  BookingCustomFieldTarget(BookingRepository bookings) {
    this.bookings = bookings;
  }

  @Override
  public CustomFieldTarget target() {
    return CustomFieldTarget.BOOKING;
  }

  @Override
  public Set<String> coreFields() {
    return Set.of(
        "code",
        "status",
        "facility",
        "facility_id",
        "use",
        "use_id",
        "part",
        "part_id",
        "start",
        "end",
        "slots",
        "price",
        "total",
        "payment",
        "payment_status",
        "holder",
        "name",
        "email",
        "language",
        "qr",
        "created_at",
        "cancelled_at");
  }

  /** Só se é unha reserva do concello actual (o repositorio le dentro del). */
  @Override
  public boolean ownedByCurrentMunicipality(UUID entityId) {
    return bookings.findById(new BookingId(entityId)).isPresent();
  }

  @Override
  public boolean allowsPublic() {
    return false;
  }

  /** Quen vai reservar é o titular da súa reserva: ve os campos de titular para enchelos. */
  @Override
  public boolean ownedByItsCreator() {
    return true;
  }
}
