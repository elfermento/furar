// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link BookingMailLog} sen o módulo {@code notification}: non saíu ningún correo. Non é un bean:
 * úsase cando non hai ningún (ADR-0005, «un bean, implementación por defecto»).
 */
public final class NoBookingMailLog implements BookingMailLog {

  @Override
  public Optional<SentMail> lastSent(UUID bookingId) {
    return Optional.empty();
  }
}
