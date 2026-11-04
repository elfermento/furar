// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Unha reserva que quedaría fóra cun cambio (modal de reservas afectadas, ADR-0022 punto 6). Horas
 * de reloxo de parede en Europe/Madrid (punto 8).
 *
 * @param holderName nome de quen reserva, para a lenda do modal
 * @param hasEmail se se lle pode avisar por correo
 * @param startMinute minuto do día no que empeza (0–1440)
 * @param endMinute minuto do día no que remata (0–1440)
 * @param useName nome do uso reservado
 */
public record AffectedBooking(
    UUID bookingId,
    String holderName,
    boolean hasEmail,
    LocalDate date,
    int startMinute,
    int endMinute,
    String useName) {

  public AffectedBooking {
    Objects.requireNonNull(bookingId, "bookingId");
    Objects.requireNonNull(holderName, "holderName");
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(useName, "useName");
  }
}
