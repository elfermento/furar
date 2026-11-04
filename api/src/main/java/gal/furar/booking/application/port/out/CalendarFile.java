// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import gal.furar.booking.domain.Booking;
import org.jspecify.annotations.Nullable;

/**
 * O evento de calendario dunha reserva (RFC 5545, ADR-0025 punto 14): «Engadir ao calendario» na
 * web e o adxunto do correo. Os textos van no idioma gardado na reserva.
 */
public interface CalendarFile {

  /**
   * O ficheiro {@code .ics} enteiro, con {@code CRLF}.
   *
   * @param address o enderezo da instalación nunha liña, para o {@code LOCATION}
   */
  String of(
      Booking booking,
      String facilityName,
      @Nullable String address,
      String useName,
      @Nullable String partName);
}
