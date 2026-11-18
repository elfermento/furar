// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.service;

import gal.furar.booking.BookingMailLog;
import gal.furar.notification.application.port.out.DeliveryLog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * A SPI {@link BookingMailLog} de {@code booking}: o último correo que saíu dunha reserva, para o
 * detalle do persoal. Non é un caso de uso: chámao o detalle, que xa comprobou os permisos.
 */
@Service
class BookingMailLogService implements BookingMailLog {

  private final DeliveryLog log;

  BookingMailLogService(DeliveryLog log) {
    this.log = log;
  }

  @Override
  public Optional<SentMail> lastSent(UUID bookingId) {
    return log.lastSent(bookingId).map(s -> new SentMail(Kind.valueOf(s.kind().name()), s.at()));
  }
}
