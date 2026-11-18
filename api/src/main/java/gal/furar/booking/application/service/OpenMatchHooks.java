// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.OpenMatchCancelled;
import gal.furar.booking.OpenMatchChanged;
import gal.furar.booking.application.port.out.OpenMatches;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.Holder;
import gal.furar.booking.domain.OpenMatch;
import gal.furar.booking.domain.OpenMatchCancelReason;
import gal.furar.booking.domain.OpenMatchStatus;
import gal.furar.booking.domain.RentalMode;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * A reserva manda sobre a súa partida aberta (ADR-0040, punto 6). Chámano os servizos de reservas
 * dentro da súa transacción, despois do bloqueo da instalación e da escritura da reserva: a orde é
 * sempre instalación → reserva → partida. Os avisos saen despois do commit.
 */
@Component
class OpenMatchHooks {

  private final OpenMatches matches;
  private final OpenMatchViews views;
  private final ApplicationEventPublisher events;

  OpenMatchHooks(OpenMatches matches, OpenMatchViews views, ApplicationEventPublisher events) {
    this.matches = matches;
    this.views = views;
    this.events = events;
  }

  /**
   * Cancelouse a reserva (a persoa, o concello ou un cambio da instalación): a partida tamén,
   * bórranse as solicitudes e avísase ás persoas que estaban dentro.
   */
  void bookingCancelled(Booking booking, Instant now) {
    live(booking)
        .ifPresent(
            m -> {
              matches.update(m, m.cancel(OpenMatchCancelReason.BOOKING_CANCELLED, now));
              matches.deletePending(m.id());
              events.publishEvent(
                  new OpenMatchCancelled(TenantContext.require().id(), m.id(), Ids.newV7()));
            });
  }

  /**
   * Antes de que a persoa mova a súa reserva: cara a un uso por prazas non (422 {@code
   * booking.match-seats-use}); se a partida busca xente, a hora nova ten que deixar aínda abrila
   * (422 {@code booking.match-too-late}). Quen organiza pode antes «Deixar de buscar».
   */
  void checkPersonMove(Booking after, Instant now) {
    Optional<OpenMatch> match = live(after);
    if (match.isEmpty()) {
      return;
    }
    if (after.slot().rentalMode() == RentalMode.SEATS) {
      throw new DomainException(BookingError.MATCH_SEATS_USE);
    }
    if (match.get().status() == OpenMatchStatus.OPEN) {
      OpenMatch.checkInTime(
          after.cancelUntil(), after.slot().start(), views.policy().cancelHours(), now);
    }
  }

  /**
   * A reserva cambiou de hora: a partida vai con ela e avísase ás persoas que estaban dentro. Se a
   * moveu o persoal a unha hora que xa non deixaría abrila (ou a un uso por prazas), a partida
   * péchase: nunca se cancela soa por un cambio do persoal.
   */
  void moved(Booking before, Booking after, boolean byStaff, Instant now) {
    if (before.slot().start().equals(after.slot().start())
        && before.slot().end().equals(after.slot().end())
        && before.slot().rentalMode() == after.slot().rentalMode()) {
      return;
    }
    live(after)
        .ifPresent(
            m -> {
              OpenMatch next = m.moved(now);
              if (byStaff && m.status() == OpenMatchStatus.OPEN && !stillOpenable(after, now)) {
                next = next.close(now);
                matches.deletePending(m.id());
              }
              matches.update(m, next);
              events.publishEvent(
                  new OpenMatchChanged(TenantContext.require().id(), m.id(), Ids.newV7()));
            });
  }

  /**
   * O persoal corrixiu o correo do titular e a reserva cambiou de dono (ADR-0031, punto 7): a
   * partida péchase, sen correo.
   */
  void holderChanged(Booking before, Booking after, Instant now) {
    Holder was = before.holder();
    Holder is = after.holder();
    if (was == null || is == null || Objects.equals(was.subject(), is.subject())) {
      return;
    }
    live(after)
        .filter(m -> m.status() == OpenMatchStatus.OPEN)
        .ifPresent(
            m -> {
              matches.update(m, m.close(now));
              matches.deletePending(m.id());
            });
  }

  private boolean stillOpenable(Booking booking, Instant now) {
    if (booking.slot().rentalMode() == RentalMode.SEATS) {
      return false;
    }
    Instant deadline = OpenMatch.deadline(booking.slot().start(), views.policy().cancelHours());
    return now.isBefore(booking.cancelUntil()) && now.isBefore(deadline);
  }

  /** A partida da reserva, co bloqueo da súa fila, se a ten e non está cancelada. */
  private Optional<OpenMatch> live(Booking booking) {
    return matches
        .findByBookingLocked(booking.id().value())
        .filter(m -> m.status() != OpenMatchStatus.CANCELLED);
  }
}
