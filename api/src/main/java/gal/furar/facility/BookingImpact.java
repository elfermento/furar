// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import java.util.List;
import java.util.UUID;

/**
 * SPI cara a {@code booking} (ADR-0022, punto 2; ADR-0005 revisada): que reservas futuras toca un
 * cambio da instalación e que facer con elas. Un só bean; sen ningún, {@code facility} usa {@link
 * NoBookingImpact}. Chámase sempre baixo o bloqueo da instalación e na mesma transacción ca o
 * cambio.
 */
public interface BookingImpact {

  /**
   * Reservas futuras que quedarían fóra co cambio. Chámase antes de gardar o cambio: a base aínda
   * ten o estado de antes, así que o que se perde vai no propio cambio (p. ex. os tramos exactos de
   * {@link FacilityChange.ScheduleChanged}, o período de {@link FacilityChange.Block}).
   */
  List<AffectedBooking> affectedBy(FacilityChange change);

  /**
   * Aplica as decisións (unha por reserva afectada). Só cambia estado na transacción: os avisos
   * (correo, web) saen despois do commit por evento, nunca desde aquí.
   */
  void apply(FacilityChange change, List<BookingDecision> decisions);

  /**
   * Se a instalación tivo algunha vez reservas (pasadas, futuras ou canceladas). Unha instalación
   * con historial non se borra: dáse de baixa para conservalo (especificación, bloque 1, «Estados e
   * baixa»).
   */
  boolean hasBookings(UUID facilityId);
}
