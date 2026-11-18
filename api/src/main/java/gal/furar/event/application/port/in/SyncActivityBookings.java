// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.port.in;

import java.util.UUID;

/**
 * As reservas das sesións das actividades (ADR-0041): se unha se cancela nas reservas, a sesión
 * tamén. Despois do commit da reserva e no seu concello.
 */
public interface SyncActivityBookings {

  /** A reserva cancelouse: se era dunha sesión aínda viva, cancélaa e avisa. Idempotente. */
  void bookingCancelled(UUID bookingId);
}
