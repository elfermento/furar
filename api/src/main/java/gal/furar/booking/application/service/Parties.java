// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.in.ParticipantSelection;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.Participant;
import gal.furar.booking.domain.ParticipantKind;
import gal.furar.booking.domain.Participants;
import gal.furar.booking.domain.RentalMode;
import gal.furar.people.DependentSummary;
import gal.furar.people.PeopleQueries;
import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Para quen é unha reserva (ADR-0031, puntos 5 e 8): converte o que se pide nas persoas da reserva
 * e comproba «unha praza por persoa». Chámase baixo o bloqueo da instalación.
 */
@Component
class Parties {

  private final PeopleQueries people;
  private final BookingRepository bookings;

  Parties(PeopleQueries people, BookingRepository bookings) {
    this.people = people;
    this.bookings = bookings;
  }

  /**
   * As persoas da reserva. Fóra dos usos por prazas non as leva: só vale «quen reserva». Nos usos
   * por prazas, quen ten a reserva (se ocupa praza), as fichas activas da súa conta e os
   * acompañantes.
   *
   * @param account a conta da reserva: a do token ou, no mostrador, a do correo; nula sen conta
   * @throws DomainException {@code booking.participants-not-allowed}, {@code
   *     booking.dependent-unknown} (unha ficha doutra conta, desactivada ou que non existe) ou os
   *     de {@link Participants#of}
   */
  Participants resolve(RentalMode mode, ParticipantSelection selection, @Nullable String account) {
    if (mode != RentalMode.SEATS) {
      if (!selection.isDefault()) {
        throw new DomainException(BookingError.PARTICIPANTS_NOT_ALLOWED);
      }
      return Participants.none();
    }
    List<Participant> list = new ArrayList<>();
    if (selection.self()) {
      list.add(Participant.holder());
    }
    Set<UUID> ids = new LinkedHashSet<>(selection.dependentIds());
    if (ids.size() != selection.dependentIds().size()) {
      throw new DomainException(BookingError.PARTICIPANT_DUPLICATE);
    }
    if (!ids.isEmpty()) {
      List<DependentSummary> found = account == null ? List.of() : people.dependents(account, ids);
      if (found.size() != ids.size()) {
        throw new DomainException(BookingError.DEPENDENT_UNKNOWN);
      }
      found.forEach(d -> list.add(Participant.dependent(d.id(), d.name(), d.birthYear())));
    }
    selection.companions().forEach(c -> list.add(Participant.companion(c.name(), c.birthYear())));
    return Participants.of(list);
  }

  /**
   * As mesmas persoas que xa leva a reserva (ao cambiala sen dicir para quen é): tal como están
   * gardadas, sen volver ler as fichas.
   */
  Participants current(RentalMode mode, BookingId id) {
    if (mode != RentalMode.SEATS) {
      return Participants.none();
    }
    return Participants.of(bookings.participants(List.of(id)).getOrDefault(id.value(), List.of()));
  }

  /**
   * Os acompañantes que xa leva a reserva (escribiunos o persoal): a persoa, ao cambiala pola súa
   * conta, non os quita.
   */
  List<ParticipantSelection.Companion> companions(BookingId id) {
    return bookings.participants(List.of(id)).getOrDefault(id.value(), List.of()).stream()
        .filter(p -> p.kind() == ParticipantKind.COMPANION && p.name() != null)
        .map(p -> new ParticipantSelection.Companion(p.name(), p.birthYear()))
        .toList();
  }

  /**
   * Unha praza por persoa: ningunha das persoas ten xa praza nese uso nun período que pise {@code
   * [start, end)}, sen contar a reserva {@code except} (a que se cambia).
   *
   * @throws DomainException {@code booking.seat-taken-by-you} se é quen ten a reserva; {@code
   *     booking.dependent-seat-taken} co {@code name} da persoa a cargo
   */
  void checkSeats(
      UUID useId,
      Participants party,
      @Nullable String holderEmailKey,
      Instant start,
      Instant end,
      @Nullable BookingId except) {
    List<String> keys =
        party.list().stream()
            .map(p -> p.personKey(holderEmailKey))
            .flatMap(Optional::stream)
            .toList();
    if (keys.isEmpty()) {
      return;
    }
    Set<String> seated = bookings.seated(useId, keys, start, end, except);
    for (Participant p : party.list()) {
      Optional<String> key = p.personKey(holderEmailKey);
      if (key.isEmpty() || !seated.contains(key.get())) {
        continue;
      }
      if (p.kind() == ParticipantKind.HOLDER) {
        throw new DomainException(BookingError.SEAT_TAKEN_BY_YOU);
      }
      throw new DomainException(
          BookingError.DEPENDENT_SEAT_TAKEN, Map.of("name", String.valueOf(p.name())));
    }
  }
}
