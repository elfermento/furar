// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha partida aberta tal como a ve a persoa con sesión (ADR-0040, punto 9): o que se ve en
 * público, o seu papel e, se está dentro, os nomes.
 *
 * @param playerId a súa fila, se se uniu ou o pediu
 * @param players as persoas que están dentro, só para quen está dentro (quen organiza e as
 *     confirmadas); baleira para o resto
 * @param requests as solicitudes pendentes, só para quen organiza
 */
public record MyOpenMatch(
    OpenMatchView view,
    Role role,
    @Nullable UUID playerId,
    List<Person> players,
    List<Request> requests) {

  public MyOpenMatch {
    Objects.requireNonNull(view, "view");
    Objects.requireNonNull(role, "role");
    players = List.copyOf(players);
    requests = List.copyOf(requests);
  }

  /** O papel da persoa na partida. */
  public enum Role {
    ORGANIZER,
    CONFIRMED,
    PENDING,
    REJECTED,
    NONE;

    /** Quen organiza e quen está dentro ven os nomes e a partida en calquera estado. */
    public boolean inside() {
      return this == ORGANIZER || this == CONFIRMED;
    }
  }

  /** Unha persoa que está dentro. */
  public record Person(UUID id, String name) {}

  /** Unha solicitude pendente. */
  public record Request(UUID id, String name, Instant requestedAt) {}
}
