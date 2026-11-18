// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.service;

import gal.furar.booking.ActivitySessionLinks;
import gal.furar.event.application.port.out.SessionRepository;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa a SPI {@link ActivitySessionLinks} de {@code booking} (ADR-0041): a sesión que ocupa
 * cada reserva, para o detalle da reserva na xestión. Sen permisos propios: xa os comprobou o caso
 * de uso de {@code booking} que chama; sempre no concello actual.
 */
@Component
class ActivityBookingLinks implements ActivitySessionLinks {

  private final SessionRepository sessions;

  ActivityBookingLinks(SessionRepository sessions) {
    this.sessions = sessions;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, UUID> sessionsByBooking(Collection<UUID> bookingIds) {
    return sessions.byBookings(bookingIds);
  }
}
