// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.service;

import gal.furar.event.EventSessionCancelled;
import gal.furar.event.application.port.in.SyncActivityBookings;
import gal.furar.event.application.port.out.RegistrationRepository;
import gal.furar.event.application.port.out.SessionRepository;
import gal.furar.event.domain.EventSession;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Se a reserva que ocupa a instalación nunha sesión dunha actividade se cancela nas reservas (o
 * persoal desde o calendario, ou un cambio da instalación co modal de reservas afectadas), a sesión
 * quédase cancelada e avísase ás persoas con praza (ADR-0041). Non é un caso de uso: vén dun evento
 * de {@code booking}, despois do commit e no concello da reserva.
 */
@Service
public class SessionBookingSync implements SyncActivityBookings {

  private final SessionRepository sessions;
  private final RegistrationRepository registrations;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public SessionBookingSync(
      SessionRepository sessions,
      RegistrationRepository registrations,
      ApplicationEventPublisher events,
      Clock clock) {
    this.sessions = sessions;
    this.registrations = registrations;
    this.events = events;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void bookingCancelled(UUID bookingId) {
    Optional<EventSession> found = sessions.findByBooking(bookingId);
    if (found.isEmpty() || found.get().cancelled()) {
      return;
    }
    EventSession session = found.get();
    registrations.lockEvent(session.eventId());
    sessions.cancel(List.of(session.id()), clock.instant());
    events.publishEvent(
        new EventSessionCancelled(
            TenantContext.require().id(), session.eventId().value(), session.id()));
  }
}
