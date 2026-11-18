// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import gal.furar.shared.domain.DomainException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * As persoas dunha reserva (ADR-0031, punto 5). Nun uso por prazas, de 1 a {@value #MAX}, sen
 * repetir: cada unha ocupa unha praza e {@link #seats} é o seu número. Fóra dos usos por prazas a
 * reserva non leva persoas ({@link #none}) e ocupa unha soa praza.
 *
 * @param list na orde en que se gardan: o titular (se ocupa praza), as persoas a cargo e os
 *     acompañantes
 */
public record Participants(List<Participant> list) {

  /** Prazas por reserva, como moito: quen ten a conta e ata dez persoas a cargo. */
  public static final int MAX = 11;

  public Participants {
    list = List.copyOf(list);
  }

  /** Sen persoas: a reserva dun uso que non é por prazas. */
  public static Participants none() {
    return new Participants(List.of());
  }

  /**
   * As persoas dunha reserva por prazas.
   *
   * @throws DomainException {@code booking.participants-required} sen ningunha; {@code
   *     booking.too-many-seats} con máis de {@value #MAX}; {@code booking.participant-duplicate}
   *     con dúas co mesmo nome e ano, a mesma ficha dúas veces ou dous titulares
   */
  public static Participants of(List<Participant> list) {
    if (list.isEmpty()) {
      throw new DomainException(BookingError.PARTICIPANTS_REQUIRED);
    }
    if (list.size() > MAX) {
      throw new DomainException(BookingError.TOO_MANY_SEATS, Map.of("max", MAX));
    }
    Set<String> seen = new HashSet<>();
    int holders = 0;
    for (Participant p : list) {
      if (p.kind() == ParticipantKind.HOLDER) {
        holders++;
      }
      boolean repeated =
          (p.dependentId() != null && !seen.add("d:" + p.dependentId()))
              || p.identity().map(i -> !seen.add("n:" + i)).orElse(false);
      if (repeated || holders > 1) {
        throw new DomainException(BookingError.PARTICIPANT_DUPLICATE);
      }
    }
    return new Participants(list);
  }

  /** As prazas que ocupa a reserva. */
  public int seats() {
    return Math.max(1, list.size());
  }

  /** Se o titular ocupa praza. */
  public boolean self() {
    return list.stream().anyMatch(p -> p.kind() == ParticipantKind.HOLDER);
  }
}
