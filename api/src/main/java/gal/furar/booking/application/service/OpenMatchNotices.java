// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.OpenMatchNotice;
import gal.furar.booking.OpenMatchNotice.Kind;
import gal.furar.booking.OpenMatchNotice.Recipient;
import gal.furar.booking.application.port.in.OpenMatchView;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.application.port.out.OpenMatches;
import gal.furar.booking.application.port.out.OpenMatches.Listed;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.Holder;
import gal.furar.booking.domain.OpenMatch;
import gal.furar.booking.domain.OpenMatchCancelReason;
import gal.furar.booking.domain.OpenMatchPlayer;
import gal.furar.booking.domain.OpenMatchPlayerStatus;
import gal.furar.booking.domain.OpenMatchStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * A quen escribir por unha partida aberta (ADR-0040, punto 12), co estado de agora: unha partida xa
 * reaberta, unha solicitude xa retirada ou unha persoa que xa saíu non reciben nada.
 */
@Component
class OpenMatchNotices {

  private final OpenMatches matches;
  private final BookingRepository bookings;
  private final OpenMatchViews views;

  OpenMatchNotices(OpenMatches matches, BookingRepository bookings, OpenMatchViews views) {
    this.matches = matches;
    this.bookings = bookings;
    this.views = views;
  }

  Optional<OpenMatchNotice> notice(UUID matchId, Kind kind, @Nullable UUID playerId) {
    Optional<Listed> found = matches.listed(matchId);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    OpenMatch m = found.get().match();
    List<Recipient> recipients = new ArrayList<>();
    String requester = null;
    switch (kind) {
      case CANCELLED -> {
        if (m.status() != OpenMatchStatus.CANCELLED) {
          return Optional.empty();
        }
        recipients.addAll(confirmed(matchId));
        if (m.cancelReason() == OpenMatchCancelReason.UNFILLED) {
          organizer(m).ifPresent(recipients::add);
        }
      }
      case CHANGED -> {
        if (m.status() == OpenMatchStatus.CANCELLED) {
          return Optional.empty();
        }
        recipients.addAll(confirmed(matchId));
      }
      case REQUESTED -> {
        Optional<OpenMatchPlayer> p = player(matchId, playerId, OpenMatchPlayerStatus.PENDING);
        if (p.isEmpty() || m.status() != OpenMatchStatus.OPEN) {
          return Optional.empty();
        }
        requester = p.get().name();
        organizer(m).ifPresent(recipients::add);
      }
      case ACCEPTED ->
          player(matchId, playerId, OpenMatchPlayerStatus.CONFIRMED)
              .map(OpenMatchNotices::recipient)
              .ifPresent(recipients::add);
      case REJECTED ->
          player(matchId, playerId, OpenMatchPlayerStatus.REJECTED)
              .map(OpenMatchNotices::recipient)
              .ifPresent(recipients::add);
      case HIDDEN -> {
        if (!m.hidden()) {
          return Optional.empty();
        }
        organizer(m).ifPresent(recipients::add);
      }
    }
    if (recipients.isEmpty()) {
      return Optional.empty();
    }
    OpenMatchView v = views.view(found.get(), false);
    return Optional.of(
        new OpenMatchNotice(
            kind,
            matchId,
            m.title(),
            v.facilityId(),
            v.facilityName(),
            v.facilityAddress(),
            v.useName(),
            v.partName(),
            v.start(),
            v.end(),
            m.minimum(),
            v.deadline(),
            m.organizerName(),
            m.cancelReason() == OpenMatchCancelReason.UNFILLED,
            m.status() == OpenMatchStatus.CLOSED,
            requester,
            recipients));
  }

  private List<Recipient> confirmed(UUID matchId) {
    return matches.players(matchId).stream()
        .filter(p -> p.status() == OpenMatchPlayerStatus.CONFIRMED)
        .map(OpenMatchNotices::recipient)
        .toList();
  }

  private Optional<OpenMatchPlayer> player(
      UUID matchId, @Nullable UUID playerId, OpenMatchPlayerStatus status) {
    if (playerId == null) {
      return Optional.empty();
    }
    return matches.playerById(matchId, playerId).filter(p -> p.status() == status);
  }

  /** Quen organiza, polo correo e o idioma da súa reserva; sen correo, ninguén. */
  private Optional<Recipient> organizer(OpenMatch m) {
    return bookings
        .findById(new BookingId(m.bookingId()))
        .map(b -> b.holder())
        .filter(h -> h != null && h.email() != null)
        .map(
            (Holder h) ->
                new Recipient(
                    m.bookingId(), null, h.email(), h.language().code(), m.organizerName()));
  }

  private static Recipient recipient(OpenMatchPlayer p) {
    return new Recipient(null, p.id(), p.email(), p.language().code(), p.name());
  }
}
