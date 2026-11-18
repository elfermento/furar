// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import java.util.List;

/**
 * Onde van as reservas dunha descarga (o adaptador escribe o CSV na resposta): primeiro as columnas
 * dos campos propios e despois as reservas, por tandas e na orde de día e hora.
 */
public interface BookingExportSink {

  /** Antes da primeira tanda (tamén se non hai ningunha reserva). */
  void begin(long total, List<BookingFieldColumn> fields);

  /** Unha tanda de reservas. */
  void write(List<ManagedBooking> bookings);
}
